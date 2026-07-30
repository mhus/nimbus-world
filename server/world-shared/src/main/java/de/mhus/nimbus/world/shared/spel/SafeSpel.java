package de.mhus.nimbus.world.shared.spel;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Central factory for hardened SpEL evaluation contexts used by the logic /
 * condition engines.
 *
 * <p>All contexts use {@link SimpleEvaluationContext}, which — unlike
 * {@code StandardEvaluationContext} — forbids type references
 * ({@code T(java.lang.Runtime)}), bean references, constructors and arbitrary
 * method invocation. This removes the remote-code-execution surface while still
 * supporting the map-backed property access the logic expressions rely on
 * (via {@link MapAccessor}).
 */
public final class SafeSpel {

    private SafeSpel() {}

    /**
     * Read-only, map-aware context for boolean conditions. Assignment is
     * disabled, so an expression can only read state, never mutate it.
     */
    public static EvaluationContext readOnly(Object rootObject) {
        return SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .withAssignmentDisabled()
                .withRootObject(rootObject)
                .build();
    }

    /**
     * Map-aware context that additionally permits property assignment
     * (e.g. {@code "state.key = true"}). Still no type references, beans,
     * constructors or method calls — assignment targets only map properties.
     */
    public static EvaluationContext readWrite(Object rootObject) {
        return SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .withRootObject(rootObject)
                .build();
    }
}
