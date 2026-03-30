package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.world.shared.world.LogicEffect;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Effect handler that executes another rule by package and name.
 * The target rule's condition is checked before execution.
 *
 * Parameters:
 *   - rulePackage: package of the target rule (optional, defaults to current rule's package)
 *   - ruleName:    name of the target rule (required)
 *
 * Example:
 *   {"type": "apply_rule", "parameters": {"ruleName": "open_gate"}}
 *   {"type": "apply_rule", "parameters": {"rulePackage": "quest_forest", "ruleName": "complete_step"}}
 */
@Component
@Slf4j
public class LogicRuleApplyHandler implements LogicEffectHandler {

    public static final String TYPE = "apply_rule";

    private final WLogicRuleRepository ruleRepository;
    private final LogicSpelService spelService;
    private final LogicEffectRegistry effectRegistry;

    public LogicRuleApplyHandler(WLogicRuleRepository ruleRepository,
                                 LogicSpelService spelService,
                                 @Lazy LogicEffectRegistry effectRegistry) {
        this.ruleRepository = ruleRepository;
        this.spelService = spelService;
        this.effectRegistry = effectRegistry;
    }

    @Override
    public Set<String> execute(Map<String, String> parameters, LogicContext context) {
        String ruleName = parameters.get("ruleName");
        if (ruleName == null || ruleName.isBlank()) {
            log.error("apply_rule: missing required parameter 'ruleName' in {}", parameters);
            return Set.of();
        }

        String targetPackage = parameters.getOrDefault("rulePackage",
                context.getRulePackage() != null ? context.getRulePackage() : "default");

        String worldId = context.getWorldId();

        // Find the target rule by worldId and name
        // Rules are stored with the base worldId, but we look up by the full worldId first
        WLogicRule targetRule = ruleRepository.findByWorldIdAndName(worldId, ruleName).orElse(null);
        if (targetRule == null) {
            log.warn("apply_rule: rule '{}' not found for worldId={}", ruleName, worldId);
            return Set.of();
        }

        if (!targetRule.isEnabled()) {
            log.debug("apply_rule: rule '{}' is disabled, skipping", ruleName);
            return Set.of();
        }

        // Check the target rule's condition
        String rulePackage = targetRule.getRulePackage() != null ? targetRule.getRulePackage() : "default";
        boolean conditionMet = spelService.evaluateCondition(
                targetRule.getSpelCondition(), context.getFlags(), rulePackage);

        if (!conditionMet) {
            log.debug("apply_rule: condition not met for rule '{}'", ruleName);
            return Set.of();
        }

        log.debug("apply_rule: executing rule '{}' (package={}) for worldId={}",
                ruleName, rulePackage, worldId);

        // Execute the target rule's effects
        String previousPackage = context.getRulePackage();
        context.setRulePackage(rulePackage);

        Set<String> changedFlags = new HashSet<>();
        try {
            for (LogicEffect effect : targetRule.getEffects()) {
                Set<String> effectChanges = effectRegistry.executeEffect(effect, context);
                changedFlags.addAll(effectChanges);
            }
            targetRule.setUpdatedAt(Instant.now());
        } finally {
            // Restore previous package context
            context.setRulePackage(previousPackage);
        }

        return changedFlags;
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
