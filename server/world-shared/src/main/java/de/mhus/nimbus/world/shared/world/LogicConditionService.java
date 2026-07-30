package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import de.mhus.nimbus.world.shared.spel.SafeSpel;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Read-only service for evaluating SpEL conditions against Logic Machine state.
 * Can be used in any module (world-player, world-life, world-control) since
 * it only reads from WProgress.
 *
 * Also provides test and simulate methods for the rule editor.
 *
 * State is stored in WProgress with playerId="logic", type="logic-flag"
 * as a nested map: { "package": { "key": value } }.
 *
 * SpEL expressions use "state." prefix: state.pkg.key or state.key (shorthand).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicConditionService {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Matches unqualified state references: "state.xxx" NOT followed by ".yyy"
     */
    private static final Pattern UNQUALIFIED_STATE = Pattern.compile(
            "state\\.([a-zA-Z_]\\w*)(?![\\w.])");

    private final WProgressService progressService;

    /**
     * Evaluate a SpEL condition against the current logic state of a world.
     * Used by BasicGameplay for serverInfo condition checks (always fully qualified).
     *
     * @param worldId        world identifier
     * @param spelExpression boolean SpEL expression, e.g. "state.pkg.key == true"
     * @return true if condition matches, false otherwise
     */
    public boolean checkCondition(String worldId, String spelExpression) {
        if (spelExpression == null || spelExpression.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> state = loadState(worldId);
            return evaluateCondition(spelExpression, state);
        } catch (Exception e) {
            log.warn("Failed to evaluate logic condition '{}' for worldId={}: {}",
                    spelExpression, worldId, e.getMessage());
            return false;
        }
    }

    /**
     * Test: evaluate a rule's condition against live state of a world instance.
     * Read-only, no state changes.
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

            Map<String, Object> state = loadState(worldId);
            result.put("state", state);

            String resolvedCondition = resolveShorthand(spelCondition, rulePackage);
            result.put("spelCondition", spelCondition);
            result.put("resolvedCondition", resolvedCondition);
            result.put("conditionResult", evaluateCondition(resolvedCondition, state));

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Simulate: dry-run a rule with user-provided state (pure sandbox).
     * No DB access, no persistence, no broadcasts.
     *
     * @param ruleId         rule ID
     * @param ruleRepository repository for rule lookup
     * @param userState      user-provided state as nested map: {"pkg": {"key": value}}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> simulate(String ruleId,
                                        WLogicRuleRepository ruleRepository,
                                        Map<String, Object> userState) {
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

            Map<String, Object> state = deepCopyState(userState);
            result.put("stateBefore", deepCopyState(state));

            String resolvedCondition = resolveShorthand(rule.getSpelCondition(), rulePackage);
            result.put("spelCondition", rule.getSpelCondition());
            result.put("resolvedCondition", resolvedCondition);

            boolean conditionResult = evaluateCondition(resolvedCondition, state);
            result.put("conditionResult", conditionResult);

            List<Map<String, Object>> effectResults = new ArrayList<>();
            if (conditionResult && rule.getEffects() != null) {
                for (LogicEffect effect : rule.getEffects()) {
                    Map<String, Object> er = new LinkedHashMap<>();
                    er.put("type", effect.getType());
                    er.put("parameters", effect.getParameters());

                    String delayStr = effect.getParameters() != null ? effect.getParameters().get("delay") : null;
                    if (delayStr != null && !delayStr.isBlank()) {
                        er.put("status", "would be delayed " + delayStr + "s");
                    } else if ("state_update".equals(effect.getType()) && effect.getParameters() != null) {
                        Set<String> changed = new LinkedHashSet<>();
                        for (Map.Entry<String, String> param : effect.getParameters().entrySet()) {
                            String key = param.getKey();
                            String qualifiedKey = key.contains(".") ? key : rulePackage + "." + key;
                            Object newValue = parseValue(param.getValue());
                            setNestedValue(state, qualifiedKey, newValue);
                            changed.add(qualifiedKey);
                        }
                        er.put("changedKeys", changed);
                        er.put("status", "simulated");
                    } else {
                        er.put("status", "skipped in simulation (" + effect.getType() + ")");
                    }
                    effectResults.add(er);
                }
            }
            result.put("effects", effectResults);
            result.put("stateAfter", state);

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    // --- Internal helpers ---

    private boolean evaluateCondition(String resolvedExpression, Map<String, Object> state) {
        if (resolvedExpression == null || resolvedExpression.isBlank()) {
            return true;
        }

        if (!resolvedExpression.contains("state.")) {
            throw new IllegalArgumentException(
                    "SpEL condition must use 'state.' prefix, e.g. 'state.myKey == true'. Got: " + resolvedExpression);
        }

        Map<String, Object> root = new HashMap<>();
        root.put("state", state);

        EvaluationContext context = SafeSpel.readOnly(root);

        Expression expr = PARSER.parseExpression(resolvedExpression);
        Boolean r = expr.getValue(context, Boolean.class);
        return r != null && r;
    }

    public static String resolveShorthand(String expression, String rulePackage) {
        if (expression == null || rulePackage == null || rulePackage.isBlank()) {
            return expression;
        }
        return UNQUALIFIED_STATE.matcher(expression)
                .replaceAll("state." + rulePackage + ".$1");
    }

    private Map<String, Object> loadState(String worldId) {
        return progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .map(WProgress::getProgressData)
                .orElseGet(HashMap::new);
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> state, String qualifiedKey, Object value) {
        int dot = qualifiedKey.indexOf('.');
        if (dot < 0) return;
        String pkg = qualifiedKey.substring(0, dot);
        String key = qualifiedKey.substring(dot + 1);
        Map<String, Object> pkgMap = (Map<String, Object>) state.computeIfAbsent(pkg, k -> new HashMap<>());
        pkgMap.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyState(Map<String, Object> source) {
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
