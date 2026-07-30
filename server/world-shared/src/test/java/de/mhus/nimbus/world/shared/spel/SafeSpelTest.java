package de.mhus.nimbus.world.shared.spel;

import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeSpelTest {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private Map<String, Object> root() {
        Map<String, Object> state = new HashMap<>();
        state.put("flag", true);
        state.put("count", 5);
        Map<String, Object> root = new HashMap<>();
        root.put("state", state);
        return root;
    }

    @Test
    void readOnlyEvaluatesConditions() {
        EvaluationContext ctx = SafeSpel.readOnly(root());
        Boolean result = PARSER.parseExpression("state.flag == true and state.count > 3")
                .getValue(ctx, Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void readOnlyBlocksTypeReferenceRce() {
        EvaluationContext ctx = SafeSpel.readOnly(root());
        assertThatThrownBy(() -> PARSER.parseExpression(
                        "T(java.lang.Runtime).getRuntime().exec('id')")
                .getValue(ctx))
                .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readWriteAllowsPropertyAssignment() {
        Map<String, Object> root = root();
        EvaluationContext ctx = SafeSpel.readWrite(root);
        PARSER.parseExpression("state.flag = false").getValue(ctx);
        Map<String, Object> state = (Map<String, Object>) root.get("state");
        assertThat(state.get("flag")).isEqualTo(false);
    }

    @Test
    void readWriteStillBlocksTypeReferenceRce() {
        EvaluationContext ctx = SafeSpel.readWrite(root());
        assertThatThrownBy(() -> PARSER.parseExpression(
                        "T(java.lang.System).exit(1)")
                .getValue(ctx))
                .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class);
    }
}
