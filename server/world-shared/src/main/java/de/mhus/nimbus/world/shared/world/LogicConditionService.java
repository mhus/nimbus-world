package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Read-only service for evaluating SpEL conditions against Logic Machine flags.
 * Can be used in any module (world-player, world-life, world-control) since
 * it only reads from WProgress.
 *
 * Also provides test and simulate methods for the rule editor.
 *
 * Flags are stored in WProgress with playerId="logic", type="logic-flag"
 * as a nested map: { "package": { "flag": value } }.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicConditionService {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Matches unqualified flag references: "flags.xxx" NOT followed by ".yyy"
     */
    private static final Pattern UNQUALIFIED_FLAG = Pattern.compile(
            "flags\\.([a-zA-Z_]\\w*)(?!\\.)");

    private final WProgressService progressService;

    /**
     * Evaluate a SpEL condition against the current logic flags of a world.
     * Used by BasicGameplay for serverInfo condition checks (always fully qualified).
     *
     * @param worldId        world identifier (includes instance if applicable)
     * @param spelExpression boolean SpEL expression, e.g. "flags.pkg.flag == true"
     * @return true if condition matches, false otherwise
     */
    public boolean checkCondition(String worldId, String spelExpression) {
        if (spelExpression == null || spelExpression.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> flags = loadFlags(worldId);
            return evaluateCondition(spelExpression, flags);
        } catch (Exception e) {
            log.warn("Failed to evaluate logic condition '{}' for worldId={}: {}",
                    spelExpression, worldId, e.getMessage());
            return false;
        }
    }

    /**
     * Test: evaluate a rule's condition against live flags of a world instance.
     * Read-only, no state changes. Returns detailed result for the rule editor.
     *
     * @param worldId    world instance ID (required)
     * @param ruleId     rule ID (optional, if null uses inline spelCondition/rulePackage)
     * @param inlineData optional inline data with "spelCondition" and "rulePackage"
     * @return detailed test result
     */
    public Map<String, Object> testCondition(String worldId, String ruleId,
                                              WLogicRuleRepository ruleRepository,
                                              Map<String, Object> inlineData) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "test");
        result.put("worldId", worldId);

        try {
            String spelCondition;
            String rulePackage;
            if (ruleId != null) {
                WLogicRule rule = ruleRepository.findById(ruleId).orElse(null);
                if (rule == null) {
                    result.put("error", "Rule not found: " + ruleId);
                    return result;
                }
                spelCondition = rule.getSpelCondition();
                rulePackage = rule.getRulePackage() != null ? rule.getRulePackage() : "default";
                result.put("ruleName", rule.getName());
                result.put("rulePackage", rulePackage);
            } else if (inlineData != null) {
                spelCondition = (String) inlineData.get("spelCondition");
                rulePackage = (String) inlineData.getOrDefault("rulePackage", "default");
                result.put("rulePackage", rulePackage);
            } else {
                result.put("error", "Either ruleId or inline rule data required");
                return result;
            }

            // Load live flags
            Map<String, Object> flags = loadFlags(worldId);
            result.put("flags", flags);

            // Resolve shorthand and evaluate
            String resolvedCondition = resolveShorthand(spelCondition, rulePackage);
            result.put("spelCondition", spelCondition);
            result.put("resolvedCondition", resolvedCondition);
            result.put("conditionResult", evaluateCondition(resolvedCondition, flags));

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Simulate: dry-run a rule with user-provided flags (pure sandbox).
     * No DB access for flags, no persistence, no broadcasts.
     * Only LogicFlagUpdate effects are simulated (block_status and apply_rule are skipped).
     *
     * @param ruleId         rule ID
     * @param ruleRepository repository for rule lookup
     * @param userFlags      user-provided flags as nested map: {"pkg": {"flag": value}}
     * @return simulation result
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> simulate(String ruleId,
                                        WLogicRuleRepository ruleRepository,
                                        Map<String, Object> userFlags) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "simulate");

        try {
            WLogicRule rule = ruleRepository.findById(ruleId).orElse(null);
            if (rule == null) {
                result.put("error", "Rule not found: " + ruleId);
                return result;
            }
            result.put("ruleName", rule.getName());

            String rulePackage = rule.getRulePackage() != null ? rule.getRulePackage() : "default";
            result.put("rulePackage", rulePackage);

            // Deep copy user flags for sandbox (expected format: {"pkg": {"flag": value}})
            Map<String, Object> flags = deepCopyFlags(userFlags);
            result.put("flagsBefore", deepCopyFlags(flags));

            // Evaluate condition
            String resolvedCondition = resolveShorthand(rule.getSpelCondition(), rulePackage);
            result.put("spelCondition", rule.getSpelCondition());
            result.put("resolvedCondition", resolvedCondition);

            boolean conditionResult = evaluateCondition(resolvedCondition, flags);
            result.put("conditionResult", conditionResult);

            // Simulate effects
            List<Map<String, Object>> effectResults = new ArrayList<>();
            if (conditionResult && rule.getEffects() != null) {
                for (LogicEffect effect : rule.getEffects()) {
                    Map<String, Object> er = new LinkedHashMap<>();
                    er.put("type", effect.getType());
                    er.put("parameters", effect.getParameters());

                    String delayStr = effect.getParameters() != null ? effect.getParameters().get("delay") : null;
                    if (delayStr != null && !delayStr.isBlank()) {
                        er.put("status", "would be delayed " + delayStr + "s");
                    } else if ("LogicFlagUpdate".equals(effect.getType()) && effect.getParameters() != null) {
                        // Simulate flag updates
                        Set<String> changed = new LinkedHashSet<>();
                        for (Map.Entry<String, String> param : effect.getParameters().entrySet()) {
                            String key = param.getKey();
                            String qualifiedKey = key.contains(".") ? key : rulePackage + "." + key;
                            Object newValue = parseValue(param.getValue());
                            setNestedFlag(flags, qualifiedKey, newValue);
                            changed.add(qualifiedKey);
                        }
                        er.put("changedFlags", changed);
                        er.put("status", "simulated");
                    } else {
                        er.put("status", "skipped in simulation (" + effect.getType() + ")");
                    }
                    effectResults.add(er);
                }
            }
            result.put("effects", effectResults);
            result.put("flagsAfter", flags);

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    // --- Internal helpers ---

    private boolean evaluateCondition(String resolvedExpression, Map<String, Object> flags) {
        if (resolvedExpression == null || resolvedExpression.isBlank()) {
            return true; // no condition = always true
        }

        // Validate that expression uses "flags." prefix
        if (!resolvedExpression.contains("flags.")) {
            throw new IllegalArgumentException(
                    "SpEL condition must use 'flags.' prefix, e.g. 'flags.myFlag == true'. Got: " + resolvedExpression);
        }

        Map<String, Object> root = new HashMap<>();
        root.put("flags", flags);

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        context.setRootObject(root);

        Expression expr = PARSER.parseExpression(resolvedExpression);
        Boolean r = expr.getValue(context, Boolean.class);
        return r != null && r;
    }

    public static String resolveShorthand(String expression, String rulePackage) {
        if (expression == null || rulePackage == null || rulePackage.isBlank()) {
            return expression;
        }
        return UNQUALIFIED_FLAG.matcher(expression)
                .replaceAll("flags." + rulePackage + ".$1");
    }

    private Map<String, Object> loadFlags(String worldId) {
        return progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .map(WProgress::getProgressData)
                .orElseGet(HashMap::new);
    }

    @SuppressWarnings("unchecked")
    private void setNestedFlag(Map<String, Object> flags, String qualifiedKey, Object value) {
        int dot = qualifiedKey.indexOf('.');
        if (dot < 0) return;
        String pkg = qualifiedKey.substring(0, dot);
        String flag = qualifiedKey.substring(dot + 1);
        Map<String, Object> pkgMap = (Map<String, Object>) flags.computeIfAbsent(pkg, k -> new HashMap<>());
        pkgMap.put(flag, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyFlags(Map<String, Object> source) {
        if (source == null) return new HashMap<>();
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() instanceof Map) {
                copy.put(entry.getKey(), new LinkedHashMap<>((Map<String, Object>) entry.getValue()));
            } else {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private Object parseValue(String raw) {
        if (raw == null || "null".equals(raw)) return null;
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
