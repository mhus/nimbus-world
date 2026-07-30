package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.redis.WorldRedisLockService;
import de.mhus.nimbus.world.shared.world.LogicEffect;
import de.mhus.nimbus.world.shared.world.WLogicStateDef;
import de.mhus.nimbus.world.shared.world.WLogicStateService;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core Logic Machine service.
 * Processes events by evaluating SpEL assignments, then cascading affected rules.
 * All state is stored in WProgress with playerId="logic", type="logic-flag".
 *
 * Rules are epoch-aware: only rules matching the current epoch of the world instance
 * are evaluated during event processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicMachineService {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final WProgressService progressService;
    private final WLogicRuleService ruleService;
    private final WLogicStateService logicStateService;
    private final LogicEffectRegistry effectRegistry;
    private final LogicSpelService spelService;
    private final WorldRedisLockService lockService;
    private final WWorldInstanceService worldInstanceService;
    private final LogicMetricsService metricsService;

    @Value("${logic.machine.max-execution-depth:10}")
    private int maxExecutionDepth;

    private final ScheduledExecutorService delayScheduler = Executors.newScheduledThreadPool(2);

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

        List<LogicContext.DelayedEffect> delayedEffects = List.of();
        try {
            delayedEffects = doProcessEvent(event);
        } catch (Exception e) {
            log.error("Logic Machine: error processing event for worldId={}: {}",
                    worldId, e.getMessage(), e);
        } finally {
            lockService.releaseGenericLock(lockKey, token);
        }

        // Schedule delayed effects AFTER lock release
        scheduleDelayedEffects(worldId, event.getSource(), delayedEffects);
    }

    /**
     * Execute a specific rule directly with proper locking and cascade.
     * Used by the test/execute feature in the rule editor.
     */
    public void executeRuleDirectly(String worldId, WLogicRule rule) {
        String lockKey = "logic:" + worldId;
        String token = lockService.acquireGenericLock(lockKey, LOCK_TTL);
        if (token == null) {
            throw new LogicEvaluationException("Could not acquire lock for worldId=" + worldId, null);
        }

        List<LogicContext.DelayedEffect> delayedEffects = List.of();
        try {
            String rulePackage = rule.getRulePackage() != null ? rule.getRulePackage() : "default";
            LogicStateMap flags = loadFlags(worldId);

            LogicContext context = LogicContext.builder()
                    .worldId(worldId)
                    .source("execute:" + rule.getName())
                    .rulePackage(rulePackage)
                    .flags(flags)
                    .changedFlags(new HashSet<>())
                    .build();

            Set<String> changedFlags = new HashSet<>();
            for (LogicEffect effect : rule.getEffects()) {
                Set<String> effectChanges = effectRegistry.executeEffect(effect, context);
                changedFlags.addAll(effectChanges);
            }

            if (!changedFlags.isEmpty()) {
                saveFlags(worldId, flags);
                flags.clearChanges();

                // Cascade
                int epoch = resolveEpoch(worldId);
                cascadeRules(worldId, changedFlags, flags, context, epoch, 0);

                if (!flags.getChangedKeys().isEmpty()) {
                    saveFlags(worldId, flags);
                }
            }

            delayedEffects = context.getDelayedEffects();
        } finally {
            lockService.releaseGenericLock(lockKey, token);
        }

        scheduleDelayedEffects(worldId, "execute:" + rule.getName(), delayedEffects);
    }

    /**
     * Check a condition against the current flag state (read-only, no locking needed).
     */
    public LogicConditionResult checkCondition(LogicCondition condition) {
        try {
            LogicStateMap flags = loadFlags(condition.getWorldId());
            boolean result = spelService.evaluateCondition(condition.getSpelExpression(), flags);
            return LogicConditionResult.of(result);
        } catch (Exception e) {
            log.error("Logic Machine: condition check failed for worldId={}: {}",
                    condition.getWorldId(), e.getMessage(), e);
            return LogicConditionResult.error(e.getMessage());
        }
    }

    /**
     * Process event inside lock. Returns delayed effects to be scheduled after lock release.
     */
    private List<LogicContext.DelayedEffect> doProcessEvent(LogicEvent event) {
        String worldId = event.getWorldId();
        LogicStateMap flags = loadFlags(worldId);

        log.debug("Logic Machine: processing event for worldId={}, source={}, eval={}",
                worldId, event.getSource(), event.getEval());

        metricsService.recordEventProcessed();

        // Execute eval expressions sequentially (no shorthand -- serverInfo is always fully qualified)
        if (event.getEval() != null) {
            for (String expression : event.getEval()) {
                spelService.evaluateAssignment(expression, flags);
            }
        }

        Set<String> changedFlags = new HashSet<>(flags.getChangedKeys());

        if (changedFlags.isEmpty()) {
            log.debug("Logic Machine: no flags changed for worldId={}", worldId);
            return List.of();
        }

        // Persist changed flags
        saveFlags(worldId, flags);
        flags.clearChanges();

        log.debug("Logic Machine: flags changed: {} for worldId={}", changedFlags, worldId);

        // Resolve current epoch for the world instance
        int epoch = resolveEpoch(worldId);

        // Build context for effect execution
        LogicContext context = LogicContext.builder()
                .worldId(worldId)
                .source(event.getSource())
                .flags(flags)
                .changedFlags(new HashSet<>())
                .build();

        // Cascade rules
        cascadeRules(worldId, changedFlags, flags, context, epoch, 0);

        return context.getDelayedEffects();
    }

    private void cascadeRules(String worldId, Set<String> changedFlags, LogicStateMap flags,
                              LogicContext context, int epoch, int depth) {
        if (depth >= maxExecutionDepth) {
            log.error("Logic Machine: max cascade depth {} reached for worldId={}, changedFlags={}",
                    maxExecutionDepth, worldId, changedFlags);
            return;
        }

        // Find rules affected by the changed flags, filtered by epoch
        List<WLogicRule> affectedRules = ruleService
                .findAffectedRules(worldId, List.copyOf(changedFlags), epoch);

        // Sort by priority (lower = first)
        affectedRules.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));

        Set<String> newChangedFlags = new HashSet<>();

        for (WLogicRule rule : affectedRules) {
            long startNanos = System.nanoTime();
            try {
                String rulePackage = rule.getRulePackage() != null ? rule.getRulePackage() : "default";
                boolean conditionMet = spelService.evaluateCondition(rule.getSpelCondition(), flags, rulePackage);

                if (!conditionMet) {
                    metricsService.recordRuleSkipped(worldId, rule.getName());
                    continue;
                }

                // Execute effects with rule's package context
                context.setRulePackage(rulePackage);
                for (LogicEffect effect : rule.getEffects()) {
                    Set<String> effectChanges = effectRegistry.executeEffect(effect, context);
                    newChangedFlags.addAll(effectChanges);
                }

                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                metricsService.recordRuleFired(worldId, rule.getName(), durationMs);
                log.debug("Logic Machine: rule '{}' fired for worldId={} (epoch={}, {}ms)",
                        rule.getName(), worldId, epoch, durationMs);

                rule.setUpdatedAt(Instant.now());

            } catch (LogicEvaluationException e) {
                metricsService.recordRuleError(worldId, rule.getName());
                log.error("Logic Machine: disabling rule '{}' due to evaluation error: {}",
                        rule.getName(), e.getMessage());
                rule.setEnabled(false);
                rule.setUpdatedAt(Instant.now());
                ruleService.save(rule);
            } catch (Exception e) {
                metricsService.recordRuleError(worldId, rule.getName());
                log.error("Logic Machine: error executing rule '{}': {}",
                        rule.getName(), e.getMessage(), e);
            }
        }

        // Track changes from effects in the flag map and persist
        if (!newChangedFlags.isEmpty()) {
            saveFlags(worldId, flags);
            flags.clearChanges();

            // Auto-create flag definitions for new flags
            autoCreateFlagDefinitions(worldId, newChangedFlags);

            log.debug("Logic Machine: cascading with new changes: {} (depth={})", newChangedFlags, depth + 1);
            cascadeRules(worldId, newChangedFlags, flags, context, epoch, depth + 1);
        }
    }

    /**
     * Schedule delayed effects after lock release.
     * Each delayed effect runs independently after its delay.
     * If a delayed effect changes flags, it fires a new LogicEvent for cascade.
     */
    private void scheduleDelayedEffects(String worldId, String source,
                                        List<LogicContext.DelayedEffect> delayedEffects) {
        if (delayedEffects == null || delayedEffects.isEmpty()) return;

        for (LogicContext.DelayedEffect delayed : delayedEffects) {
            delayScheduler.schedule(() -> {
                try {
                    log.debug("Logic Machine: executing delayed effect '{}' after {}s for worldId={}",
                            delayed.effect().getType(), delayed.delaySeconds(), worldId);

                    // Build a minimal context for the delayed execution
                    LogicStateMap flags = loadFlags(worldId);
                    LogicContext ctx = LogicContext.builder()
                            .worldId(worldId)
                            .source(source)
                            .rulePackage(delayed.rulePackage())
                            .flags(flags)
                            .changedFlags(new HashSet<>())
                            .build();

                    Set<String> changedFlags = effectRegistry.executeEffectDirect(delayed.effect(), ctx);

                    // If the delayed effect changed flags, trigger cascade via new event
                    if (!changedFlags.isEmpty()) {
                        log.debug("Logic Machine: delayed effect changed flags {}, triggering cascade", changedFlags);
                        // Build eval expressions from changed flags to trigger cascade
                        // Re-process as a new event (acquires its own lock)
                        LogicEvent cascadeEvent = LogicEvent.builder()
                                .worldId(worldId)
                                .eval(List.of()) // no eval, just cascade
                                .source("delayed:" + delayed.effect().getType())
                                .build();
                        processEvent(cascadeEvent);
                    }
                } catch (Exception e) {
                    log.error("Logic Machine: delayed effect '{}' failed for worldId={}: {}",
                            delayed.effect().getType(), worldId, e.getMessage(), e);
                }
            }, delayed.delaySeconds(), TimeUnit.SECONDS);

            metricsService.recordDelayedEffect();
            log.debug("Logic Machine: scheduled delayed effect '{}' in {}s for worldId={}",
                    delayed.effect().getType(), delayed.delaySeconds(), worldId);
        }
    }

    /**
     * Resolve the current epoch for a worldId.
     * If the worldId refers to an instance, the epoch is read from the WWorldInstance.
     * Otherwise (base world), epoch defaults to 0.
     */
    private int resolveEpoch(String worldId) {
        WorldId wid = WorldId.of(worldId).orElse(null);
        if (wid != null && wid.isInstance()) {
            return worldInstanceService.findByInstanceIdWithValidation(wid.getId())
                    .map(instance -> instance.getEpoch())
                    .orElse(0);
        }
        return 0;
    }

    /**
     * Load flags from WProgress for the given world.
     */
    private LogicStateMap loadFlags(String worldId) {
        return progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .map(progress -> new LogicStateMap(progress.getProgressData()))
                .orElseGet(LogicStateMap::new);
    }

    /**
     * Persist the current flag state to WProgress.
     */
    private void saveFlags(String worldId, LogicStateMap flags) {
        progressService.save(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null, flags.toProgressData());
    }

    /**
     * Auto-create WLogicStateDef definitions for flags that don't exist yet.
     */
    private void autoCreateFlagDefinitions(String worldId, Set<String> names) {
        for (String name : names) {
            if (logicStateService.findByWorldIdAndName(worldId, name).isEmpty()) {
                WLogicStateDef flag = WLogicStateDef.builder()
                        .worldId(worldId)
                        .name(name)
                        .autoCreated(true)
                        .description("auto-created")
                        .createdAt(Instant.now())
                        .build();
                logicStateService.save(flag);
                log.info("Logic Machine: auto-created flag definition '{}' for worldId={}", name, worldId);
            }
        }
    }
}
