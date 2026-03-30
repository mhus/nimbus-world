package de.mhus.nimbus.world.life.logic;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * Execution context passed to LogicEffectHandlers during rule processing.
 * Contains current flag state and tracks which flags were changed.
 */
@Data
@Builder
public class LogicContext {

    private String worldId;

    /**
     * Meta/debug info from the originating LogicEvent.
     */
    private String source;

    /**
     * Current flag state (mutable, loaded from WProgress).
     */
    private Map<String, Object> flags;

    /**
     * Tracks flag names that were changed during effect execution.
     * Used for cascade rule evaluation.
     */
    private Set<String> changedFlags;

    /**
     * Record a flag change.
     */
    public void flagChanged(String flagName) {
        changedFlags.add(flagName);
    }
}
