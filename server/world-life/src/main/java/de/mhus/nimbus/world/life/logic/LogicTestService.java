package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.world.shared.world.LogicConditionService;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleRepository;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for testing Logic Machine rules in world-life.
 * Delegates test/simulate to LogicConditionService (world-shared).
 * Execute runs locally with full pipeline (locking, effects, cascade).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicTestService {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";

    private final WProgressService progressService;
    private final WLogicRuleRepository ruleRepository;
    private final LogicConditionService conditionService;
    private final LogicMachineService logicMachineService;
    private final LogicSpelService spelService;

    public Map<String, Object> testCondition(String worldId, String ruleId,
                                              Map<String, Object> inlineData) {
        return conditionService.testCondition(worldId, ruleId, ruleRepository, inlineData);
    }

    public Map<String, Object> simulate(String ruleId, Map<String, Object> flags) {
        return conditionService.simulate(ruleId, ruleRepository, flags);
    }

    public Map<String, Object> execute(String worldId, String ruleId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "execute");
        result.put("worldId", worldId);

        try {
            WLogicRule rule = ruleRepository.findById(ruleId).orElse(null);
            if (rule == null) {
                result.put("error", "Rule not found: " + ruleId);
                return result;
            }
            result.put("ruleName", rule.getName());

            String rulePackage = rule.getRulePackage() != null ? rule.getRulePackage() : "default";

            // Snapshot before
            Map<String, Object> flagsBefore = loadFlags(worldId);
            result.put("flagsBefore", flagsBefore);

            // Check condition
            String resolvedCondition = LogicConditionService.resolveShorthand(
                    rule.getSpelCondition(), rulePackage);
            boolean conditionResult = conditionService.checkCondition(worldId, resolvedCondition);
            result.put("conditionResult", conditionResult);

            if (!conditionResult) {
                result.put("executed", false);
                result.put("reason", "Condition not met");
                return result;
            }

            // Execute with full pipeline
            logicMachineService.executeRuleDirectly(worldId, rule);

            // Snapshot after
            result.put("flagsAfter", loadFlags(worldId));
            result.put("executed", true);

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> loadFlags(String worldId) {
        return progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .map(progress -> progress.getProgressData())
                .orElseGet(java.util.HashMap::new);
    }
}
