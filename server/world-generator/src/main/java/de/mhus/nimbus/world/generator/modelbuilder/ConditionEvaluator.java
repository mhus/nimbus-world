package de.mhus.nimbus.world.generator.modelbuilder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/**
 * Evaluates SpEL condition expressions against a variable map.
 * Supports set(x)/notset(x) shorthand for null checks.
 */
@Slf4j
public final class ConditionEvaluator {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private ConditionEvaluator() {}

    /**
     * Evaluate a condition against the given variables.
     * Returns true if condition is null/blank (no condition = always active).
     */
    public static boolean evaluate(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) return true;

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.addPropertyAccessor(new MapAccessor());
            context.setRootObject(variables);

            String processed = preprocess(condition);
            Expression expr = PARSER.parseExpression(processed);
            Boolean result = expr.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Failed to evaluate condition '{}': {}", condition, e.getMessage());
            return false;
        }
    }

    /**
     * Preprocess condition: set(x) -> x != null, notset(x) -> x == null
     */
    private static String preprocess(String condition) {
        String result = condition;
        result = result.replaceAll("set\\(([^)]+)\\)", "$1 != null");
        result = result.replaceAll("notset\\(([^)]+)\\)", "$1 == null");
        return result;
    }
}
