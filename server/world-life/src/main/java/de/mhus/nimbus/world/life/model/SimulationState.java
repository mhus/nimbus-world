package de.mhus.nimbus.world.life.model;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.world.WEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Internal simulation state for each entity.
 * Tracks pathway generation, timing, and behavior state.
 */
@Data
@RequiredArgsConstructor
public class SimulationState {

    public enum LifecycleState {
        /** Entity is alive and active. */
        ALIVE,
        /** Entity died, playing death animation / fading out. */
        DEAD,
        /** Entity has disappeared, waiting for respawn. */
        GONE
    }

    private static final long DEFAULT_FADE_TIME_MS = 120_000;
    private static final long DEFAULT_RESPAWN_TIME_MS = 120_000;

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
     * Combat data for this entity (vitals, combat stats, active effects).
     * Initialized from WEntity.server properties if combat properties exist.
     * Null if the entity has no combat capabilities.
     */
    private EntityCombatData combatData;

    /** Current lifecycle state. */
    private LifecycleState lifecycleState = LifecycleState.ALIVE;

    /** Timestamp when the current lifecycle phase started (millis). */
    private long lifecycleTimestamp = 0;

    /** Player entity IDs that attacked this entity during current life. Eligible for loot on death. */
    private Set<String> attackers = new HashSet<>();

    /**
     * Get fade time (time entity stays visible after death) from entity server properties.
     * Property: death_fadeTime (seconds), default 120.
     */
    public long getFadeTimeMs() {
        return getServerLong("death_fadeTime", DEFAULT_FADE_TIME_MS / 1000) * 1000;
    }

    /**
     * Get respawn time (time entity stays gone before respawning) from entity server properties.
     * Property: death_respawnTime (seconds), default 120.
     */
    public long getRespawnTimeMs() {
        return getServerLong("death_respawnTime", DEFAULT_RESPAWN_TIME_MS / 1000) * 1000;
    }

    private long getServerLong(String key, long defaultValue) {
        var server = entity.getServer();
        if (server == null) return defaultValue * 1000 / 1000; // just defaultValue
        String val = server.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

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
