package de.mhus.nimbus.world.life.model;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.world.shared.world.WEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Internal simulation state for each entity.
 * Tracks pathway generation, timing, and behavior state.
 */
@Data
@RequiredArgsConstructor
public class SimulationState {

    /**
     * Entity being simulated.
     */
    private final WEntity entity;

    /**
     * Last generated pathway.
     */
    private EntityPathway currentPathway;

    /**
     * Timestamp when last pathway was generated (milliseconds).
     */
    private long lastPathwayTime = 0;

    /**
     * Timestamp when current pathway ends (milliseconds).
     * Calculated from pathway waypoints.
     */
    private long pathwayEndTime = 0;

    /**
     * Target position for current movement (if any).
     * Used by behaviors for multi-step movements.
     */
    private de.mhus.nimbus.generated.types.Vector3 targetPosition;

    /**
     * Check if entity needs a new pathway based on time.
     *
     * @param currentTime Current timestamp (milliseconds)
     * @return True if pathway has ended or doesn't exist
     */
    public boolean isPathwayExpired(long currentTime) {
        return currentPathway == null || currentTime >= pathwayEndTime;
    }

    /**
     * Update pathway end time based on waypoints.
     */
    public void updatePathwayEndTime() {
        if (currentPathway == null || currentPathway.getWaypoints() == null || currentPathway.getWaypoints().isEmpty()) {
            pathwayEndTime = 0;
            return;
        }

        // Last waypoint timestamp is end time
        var waypoints = currentPathway.getWaypoints();
        var lastWaypoint = waypoints.get(waypoints.size() - 1);
        pathwayEndTime = lastWaypoint.getTimestamp();
    }
}
