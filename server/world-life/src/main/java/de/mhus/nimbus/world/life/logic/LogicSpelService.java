package de.mhus.nimbus.world.life.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * SpEL evaluation service for the Logic Machine.
 * Handles both assignments (eval) and conditions (boolean checks).
 */
@Service
@Slf4j
public class LogicSpelService {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Evaluate an assignment expression against the flag map.
     * Example: "flag1 = !flag1", "counter = counter + 1"
     *
     * The expression operates directly on the map keys (no "flags." prefix needed
     * since the map IS the root object).
     *
     * @param expression SpEL assignment expression
     * @param flags      mutable flag map (tracks changes)
     */
    public void evaluateAssignment(String expression, LogicFlagMap flags) {
        try {
            StandardEvaluationContext context = createContext(flags);
            Expression expr = PARSER.parseExpression(expression);
            expr.getValue(context);
        } catch (Exception e) {
            log.error("Failed to evaluate assignment '{}': {}", expression, e.getMessage());
            throw new LogicEvaluationException("Assignment failed: " + expression, e);
        }
    }

    /**
     * Evaluate a boolean condition expression against the flag map.
     * Example: "flags.hasKey == true && flags.doorOpen == false"
     *
     * @param spelCondition SpEL boolean expression
     * @param flags         current flag state
     * @return true if condition matches, false otherwise
     */
    public boolean evaluateCondition(String spelCondition, LogicFlagMap flags) {
        if (spelCondition == null || spelCondition.isBlank()) {
            return true;
        }
        try {
            StandardEvaluationContext context = createContext(flags);
            Expression expr = PARSER.parseExpression(spelCondition);
            Boolean result = expr.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Failed to evaluate condition '{}': {}", spelCondition, e.getMessage());
            return false;
        }
    }

    /**
     * Creates a SpEL context with a root object containing "flags" as a key.
     * This allows expressions like "flags.hasKey == true" and "flags.flag1 = !flags.flag1".
     * MapAccessor enables property-style access on Map objects.
     */
    private StandardEvaluationContext createContext(LogicFlagMap flags) {
        Map<String, Object> root = new HashMap<>();
        root.put("flags", flags);

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        context.setRootObject(root);
        return context;
    }
}
