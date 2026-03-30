package de.mhus.nimbus.world.life.logic;

import java.util.Map;
import java.util.Set;

/**
 * Plugin interface for Logic Machine effects.
 * Each handler is responsible for one effect type.
 */
public interface LogicEffectHandler {

    /**
     * Execute the effect with the given parameters and context.
     *
     * @param parameters effect-specific parameters from the rule definition
     * @param context    current execution context with flag state
     * @return set of flag names that were changed by this effect (for cascade), empty set if none
     */
    Set<String> execute(Map<String, Object> parameters, LogicContext context);

    /**
     * The effect type this handler is responsible for.
     * Must match {@link de.mhus.nimbus.world.shared.world.LogicEffect#getType()}.
     */
    String getType();
}
