package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only service for evaluating SpEL conditions against Logic Machine flags.
 * Can be used in any module (world-player, world-life, world-control) since
 * it only reads from WProgress.
 *
 * Flags are stored in WProgress with playerId="logic", type="logic-flag".
 * SpEL expressions use the "flags." prefix, e.g. "flags.hasKey == true".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicConditionService {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final WProgressService progressService;

    /**
     * Evaluate a SpEL condition against the current logic flags of a world.
     *
     * @param worldId        world identifier (includes instance if applicable)
     * @param spelExpression boolean SpEL expression, e.g. "flags.hasKey == true"
     * @return true if condition matches, false otherwise (also false on parse errors)
     */
    public boolean checkCondition(String worldId, String spelExpression) {
        if (spelExpression == null || spelExpression.isBlank()) {
            return true;
        }

        try {
            Map<String, Object> flags = loadFlags(worldId);

            Map<String, Object> root = new HashMap<>();
            root.put("flags", flags);

            StandardEvaluationContext context = new StandardEvaluationContext();
            context.addPropertyAccessor(new MapAccessor());
            context.setRootObject(root);

            Expression expr = PARSER.parseExpression(spelExpression);
            Boolean result = expr.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Failed to evaluate logic condition '{}' for worldId={}: {}",
                    spelExpression, worldId, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> loadFlags(String worldId) {
        return progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .map(WProgress::getProgressData)
                .orElseGet(HashMap::new);
    }
}
