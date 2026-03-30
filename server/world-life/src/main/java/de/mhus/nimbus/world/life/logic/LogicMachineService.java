package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.world.shared.redis.WorldRedisLockService;
import de.mhus.nimbus.world.shared.world.LogicEffect;
import de.mhus.nimbus.world.shared.world.WLogicFlag;
import de.mhus.nimbus.world.shared.world.WLogicFlagRepository;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleRepository;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core Logic Machine service.
 * Processes events by evaluating SpEL assignments, then cascading affected rules.
 * All state is stored in WProgress with playerId="logic", type="logic-flag".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicMachineService {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final WProgressService progressService;
    private final WLogicRuleRepository ruleRepository;
    private final WLogicFlagRepository flagRepository;
    private final LogicEffectRegistry effectRegistry;
    private final LogicSpelService spelService;
    private final WorldRedisLockService lockService;

    @Value("${logic.machine.max-execution-depth:10}")
    private int maxExecutionDepth;

    /**
     * Process a LogicEvent asynchronously.
     * Acquires an instance-level lock, executes eval expressions,
     * persists flag changes, and cascades affected rules.
     */
    @Async
    public void processEventAsync(LogicEvent event) {
        processEvent(event);
    }

    /**
     * Process a LogicEvent synchronously (used internally and for testing).
     */
    public void processEvent(LogicEvent event) {
        String worldId = event.getWorldId();
        String lockKey = "logic:" + worldId;
        String token = lockService.acquireGenericLock(lockKey, LOCK_TTL);

        if (token == null) {
            log.warn("Logic Machine: could not acquire lock for worldId={}, dropping event from source={}",
                    worldId, event.getSource());
            return;
        }

        try {
            doProcessEvent(event);
        } catch (Exception e) {
            log.error("Logic Machine: error processing event for worldId={}: {}",
                    worldId, e.getMessage(), e);
        } finally {
            lockService.releaseGenericLock(lockKey, token);
        }
    }

    /**
     * Check a condition against the current flag state (read-only, no locking needed).
     */
    public LogicConditionResult checkCondition(LogicCondition condition) {
        try {
            LogicFlagMap flags = loadFlags(condition.getWorldId());
            boolean result = spelService.evaluateCondition(condition.getSpelExpression(), flags);
            return LogicConditionResult.of(result);
        } catch (Exception e) {
            log.error("Logic Machine: condition check failed for worldId={}: {}",
                    condition.getWorldId(), e.getMessage(), e);
            return LogicConditionResult.error(e.getMessage());
        }
    }

    private void doProcessEvent(LogicEvent event) {
        String worldId = event.getWorldId();
        LogicFlagMap flags = loadFlags(worldId);

        log.debug("Logic Machine: processing event for worldId={}, source={}, eval={}",
                worldId, event.getSource(), event.getEval());

        // Execute eval expressions sequentially
        if (event.getEval() != null) {
            for (String expression : event.getEval()) {
                spelService.evaluateAssignment(expression, flags);
            }
        }

        Set<String> changedFlags = new HashSet<>(flags.getChangedKeys());

        if (changedFlags.isEmpty()) {
            log.debug("Logic Machine: no flags changed for worldId={}", worldId);
            return;
        }

        // Persist changed flags
        saveFlags(worldId, flags);
        flags.clearChanges();

        log.debug("Logic Machine: flags changed: {} for worldId={}", changedFlags, worldId);

        // Build context for effect execution
        LogicContext context = LogicContext.builder()
                .worldId(worldId)
                .source(event.getSource())
                .flags(flags)
                .changedFlags(new HashSet<>())
                .build();

        // Cascade rules
        cascadeRules(worldId, changedFlags, flags, context, 0);
    }

    private void cascadeRules(String worldId, Set<String> changedFlags, LogicFlagMap flags,
                              LogicContext context, int depth) {
        if (depth >= maxExecutionDepth) {
            log.error("Logic Machine: max cascade depth {} reached for worldId={}, changedFlags={}",
                    maxExecutionDepth, worldId, changedFlags);
            return;
        }

        // Find rules affected by the changed flags
        List<WLogicRule> affectedRules = ruleRepository
                .findByWorldIdAndAffectedInAndEnabledTrue(worldId, List.copyOf(changedFlags));

        // Sort by priority (lower = first)
        affectedRules.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));

        Set<String> newChangedFlags = new HashSet<>();

        for (WLogicRule rule : affectedRules) {
            try {
                boolean conditionMet = spelService.evaluateCondition(rule.getSpelCondition(), flags);

                if (!conditionMet) {
                    continue;
                }

                log.debug("Logic Machine: rule '{}' fired for worldId={}", rule.getName(), worldId);

                // Execute effects
                for (LogicEffect effect : rule.getEffects()) {
                    Set<String> effectChanges = effectRegistry.executeEffect(effect, context);
                    newChangedFlags.addAll(effectChanges);
                }

                // Update lastFired timestamp
                rule.setUpdatedAt(Instant.now());

            } catch (LogicEvaluationException e) {
                log.error("Logic Machine: disabling rule '{}' due to evaluation error: {}",
                        rule.getName(), e.getMessage());
                rule.setEnabled(false);
                rule.setUpdatedAt(Instant.now());
                ruleRepository.save(rule);
            } catch (Exception e) {
                log.error("Logic Machine: error executing rule '{}': {}",
                        rule.getName(), e.getMessage(), e);
            }
        }

        // Track changes from effects in the flag map and persist
        if (!newChangedFlags.isEmpty()) {
            // Effects may have changed flags in the context map directly
            // Sync context flags back to the flag map
            saveFlags(worldId, flags);
            flags.clearChanges();

            // Auto-create flag definitions for new flags
            autoCreateFlagDefinitions(worldId, newChangedFlags);

            log.debug("Logic Machine: cascading with new changes: {} (depth={})", newChangedFlags, depth + 1);
            cascadeRules(worldId, newChangedFlags, flags, context, depth + 1);
        }
    }

    /**
     * Load flags from WProgress for the given world.
     */
    private LogicFlagMap loadFlags(String worldId) {
        return progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .map(progress -> new LogicFlagMap(progress.getProgressData()))
                .orElseGet(LogicFlagMap::new);
    }

    /**
     * Persist the current flag state to WProgress.
     */
    private void saveFlags(String worldId, LogicFlagMap flags) {
        progressService.save(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null, new HashMap<>(flags));
    }

    /**
     * Auto-create WLogicFlag definitions for flags that don't exist yet.
     */
    private void autoCreateFlagDefinitions(String worldId, Set<String> flagNames) {
        for (String flagName : flagNames) {
            if (flagRepository.findByWorldIdAndFlagName(worldId, flagName).isEmpty()) {
                WLogicFlag flag = WLogicFlag.builder()
                        .worldId(worldId)
                        .flagName(flagName)
                        .autoCreated(true)
                        .description("auto-created")
                        .createdAt(Instant.now())
                        .build();
                flagRepository.save(flag);
                log.info("Logic Machine: auto-created flag definition '{}' for worldId={}", flagName, worldId);
            }
        }
    }
}
