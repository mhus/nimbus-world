package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.world.shared.spel.SafeSpel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SpEL evaluation service for the Logic Machine.
 * Handles both assignments (eval) and conditions (boolean checks).
 *
 * Supports package-scoped state:
 * - state.pkg.key1       -> fully qualified (package "pkg", key "key1")
 * - state.key1           -> shorthand, resolved to state.{rulePackage}.key1
 *
 * Shorthand resolution is applied when a rulePackage is provided.
 */
@Service
@Slf4j
public class LogicSpelService {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final String DEFAULT_PACKAGE = "default";

    /**
     * Matches "state.xxx" where xxx is NOT followed by ".identifier"
     * (i.e., unqualified references that need package prefix insertion).
     *
     * state.key1          -> match (unqualified)
     * state.pkg.key1      -> no match on "state.pkg" because followed by ".key1"
     * state.key1 == true  -> match on "state.key1"
     */
    private static final Pattern UNQUALIFIED_STATE = Pattern.compile(
            "state\\.([a-zA-Z_]\\w*)(?![\\w.])");

    /**
     * Evaluate an assignment expression with package-scoped shorthand resolution.
     *
     * @param expression   SpEL assignment, e.g. "state.key1 = true"
     * @param stateMap     mutable state map (nested by package)
     * @param rulePackage  current rule's package (null = no shorthand resolution)
     */
    public void evaluateAssignment(String expression, LogicStateMap stateMap, String rulePackage) {
        String resolved = resolveShorthand(expression, rulePackage);
        try {
            EvaluationContext context = SafeSpel.readWrite(buildRoot(stateMap));
            Expression expr = PARSER.parseExpression(resolved);
            expr.getValue(context);
        } catch (Exception e) {
            log.error("Failed to evaluate assignment '{}' (resolved: '{}'): {}", expression, resolved, e.getMessage());
            throw new LogicEvaluationException("Assignment failed: " + expression, e);
        }
    }

    /**
     * Evaluate a boolean condition with package-scoped shorthand resolution.
     *
     * @param spelCondition SpEL boolean expression
     * @param stateMap      current state (nested by package)
     * @param rulePackage   current rule's package (null = no shorthand resolution)
     * @return true if condition matches
     */
    public boolean evaluateCondition(String spelCondition, LogicStateMap stateMap, String rulePackage) {
        if (spelCondition == null || spelCondition.isBlank()) {
            return true;
        }
        String resolved = resolveShorthand(spelCondition, rulePackage);
        try {
            EvaluationContext context = SafeSpel.readOnly(buildRoot(stateMap));
            Expression expr = PARSER.parseExpression(resolved);
            Boolean result = expr.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Failed to evaluate condition '{}' (resolved: '{}'): {}", spelCondition, resolved, e.getMessage());
            return false;
        }
    }

    /**
     * Evaluate a condition without shorthand resolution (fully qualified only).
     */
    public boolean evaluateCondition(String spelCondition, LogicStateMap stateMap) {
        return evaluateCondition(spelCondition, stateMap, null);
    }

    /**
     * Evaluate an assignment without shorthand resolution.
     */
    public void evaluateAssignment(String expression, LogicStateMap stateMap) {
        evaluateAssignment(expression, stateMap, null);
    }

    /**
     * Resolve shorthand state references by inserting the rule's package.
     * "state.key1" -> "state.{package}.key1" when key1 is not followed by ".xxx".
     */
    static String resolveShorthand(String expression, String rulePackage) {
        if (expression == null || rulePackage == null || rulePackage.isBlank()) {
            return expression;
        }
        return UNQUALIFIED_STATE.matcher(expression)
                .replaceAll("state." + rulePackage + ".$1");
    }

    private Map<String, Object> buildRoot(LogicStateMap stateMap) {
        Map<String, Object> root = new HashMap<>();
        root.put("state", stateMap);
        return root;
    }
}
