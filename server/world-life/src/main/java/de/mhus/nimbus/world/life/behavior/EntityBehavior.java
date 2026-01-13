package de.mhus.nimbus.world.life.behavior;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.shared.world.WEntity;

/**
 * Interface for entity behavior strategies.
 *
 * Behaviors are responsible for generating entity movement pathways based on:
 * - Entity state and properties
 * - Current simulation time
 * - World terrain
 * - Behavior-specific logic (roaming, patrolling, fleeing, etc.)
 *
 * Implementations are Spring @Component beans registered in BehaviorRegistry.
 */
public interface EntityBehavior {

    /**
     * Get the behavior type identifier.
     * Must be unique across all behavior implementations.
     *
     * @return Behavior type string (e.g., "PreyAnimalBehavior")
     */
    String getBehaviorType();

    /**
     * Update entity and generate new pathway if needed.
     *
     * Called every simulation tick for entities owned by this pod.
     * Should check if a new pathway is needed before generating one.
     *
     * @param entity Entity to simulate
     * @param state Simulation state for this entity
     * @param currentTime Current simulation time (milliseconds)
     * @param worldId World identifier
     * @return New EntityPathway if generated, or null if no update needed
     */
    EntityPathway update(WEntity entity, SimulationState state, long currentTime, WorldId worldId);

    /**
     * Check if entity needs a new pathway.
     * Default implementation checks if current pathway has expired.
     *
     * @param state Simulation state
     * @param currentTime Current time (milliseconds)
     * @return True if new pathway should be generated
     */
    default boolean needsNewPathway(SimulationState state, long currentTime) {
        return state.getCurrentPathway() == null || currentTime >= state.getPathwayEndTime();
    }
}
