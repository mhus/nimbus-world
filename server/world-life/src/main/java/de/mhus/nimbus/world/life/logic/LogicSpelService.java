package de.mhus.nimbus.world.life.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SpEL evaluation service for the Logic Machine.
 * Handles both assignments (eval) and conditions (boolean checks).
 *
 * Supports package-scoped flags:
 * - flags.pkg.flag1       -> fully qualified (package "pkg", flag "flag1")
 * - flags.flag1           -> shorthand, resolved to flags.{rulePackage}.flag1
 *
 * Shorthand resolution is applied when a rulePackage is provided.
 */
@Service
@Slf4j
public class LogicSpelService {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final String DEFAULT_PACKAGE = "default";

    /**
     * Matches "flags.xxx" where xxx is NOT followed by ".identifier"
     * (i.e., unqualified flag references that need package prefix insertion).
     *
     * flags.flag1          -> match (unqualified)
     * flags.pkg.flag1      -> no match on "flags.pkg" because followed by ".flag1"
     * flags.flag1 == true  -> match on "flags.flag1"
     */
    private static final Pattern UNQUALIFIED_FLAG = Pattern.compile(
            "flags\\.([a-zA-Z_]\\w*)(?!\\.)");

    /**
     * Evaluate an assignment expression with package-scoped shorthand resolution.
     *
     * @param expression   SpEL assignment, e.g. "flags.flag1 = true"
     * @param flags        mutable flag map (nested by package)
     * @param rulePackage  current rule's package (null = no shorthand resolution)
     */
    public void evaluateAssignment(String expression, LogicFlagMap flags, String rulePackage) {
        String resolved = resolveShorthand(expression, rulePackage);
        try {
            StandardEvaluationContext context = createContext(flags);
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
     * @param flags         current flag state (nested by package)
     * @param rulePackage   current rule's package (null = no shorthand resolution)
     * @return true if condition matches
     */
    public boolean evaluateCondition(String spelCondition, LogicFlagMap flags, String rulePackage) {
        if (spelCondition == null || spelCondition.isBlank()) {
            return true;
        }
        String resolved = resolveShorthand(spelCondition, rulePackage);
        try {
            StandardEvaluationContext context = createContext(flags);
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
     * Used by LogicConditionService in world-shared (serverInfo conditions).
     */
    public boolean evaluateCondition(String spelCondition, LogicFlagMap flags) {
        return evaluateCondition(spelCondition, flags, null);
    }

    /**
     * Evaluate an assignment without shorthand resolution.
     * Used for LogicEvents from serverInfo (always fully qualified).
     */
    public void evaluateAssignment(String expression, LogicFlagMap flags) {
        evaluateAssignment(expression, flags, null);
    }

    /**
     * Resolve shorthand flag references by inserting the rule's package.
     * "flags.flag1" -> "flags.{package}.flag1" when flag1 is not followed by ".xxx".
     *
     * @param expression  SpEL expression
     * @param rulePackage package name (null = no resolution)
     * @return resolved expression
     */
    static String resolveShorthand(String expression, String rulePackage) {
        if (expression == null || rulePackage == null || rulePackage.isBlank()) {
            return expression;
        }
        return UNQUALIFIED_FLAG.matcher(expression)
                .replaceAll("flags." + rulePackage + ".$1");
    }

    private StandardEvaluationContext createContext(LogicFlagMap flags) {
        Map<String, Object> root = new HashMap<>();
        root.put("flags", flags);

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        context.setRootObject(root);
        return context;
    }
}
