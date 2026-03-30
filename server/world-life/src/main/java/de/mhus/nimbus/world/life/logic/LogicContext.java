package de.mhus.nimbus.world.life.logic;

import lombok.Builder;
import lombok.Data;

import de.mhus.nimbus.world.shared.world.LogicEffect;
import java.util.ArrayList;
import java.util.List;
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
     * The current rule's package for shorthand resolution.
     * Effect handlers use this to resolve unqualified flag names.
     */
    private String rulePackage;

    /**
     * Current flag state (mutable, nested by package, loaded from WProgress).
     */
    private LogicFlagMap flags;

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

    /**
     * Delayed effects collected during cascade, scheduled after lock release.
     */
    @Builder.Default
    private List<DelayedEffect> delayedEffects = new ArrayList<>();

    public void addDelayedEffect(LogicEffect effect, String rulePackage, int delaySeconds) {
        delayedEffects.add(new DelayedEffect(effect, rulePackage, delaySeconds));
    }

    public record DelayedEffect(LogicEffect effect, String rulePackage, int delaySeconds) {}
}
