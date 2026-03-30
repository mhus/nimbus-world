package de.mhus.nimbus.world.shared.world;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Embedded effect definition within a WLogicRule.
 * Each effect references a LogicEffectHandler by type and provides parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogicEffect {

    /**
     * Effect handler type, e.g. "state_update", "LogicBlockStatus".
     */
    private String type;

    /**
     * Handler-specific parameters.
     */
    private Map<String, String> parameters;
}
