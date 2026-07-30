package de.mhus.nimbus.world.life.model;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.world.life.util.EntityServerData;
import de.mhus.nimbus.world.shared.gameplay.CombatStrategy;
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

    // --- Combat state ---

    /** Combat strategy for this entity (from server property combat_strategy) */
    private CombatStrategy combatStrategy;

    /** True if entity is currently in combat mode */
    private boolean inCombat;

    /** Timestamp when combat mode ends (millis) */
    private long combatEndTime;

    /** Session IDs of attackers for position lookup in Redis (entityId -> sessionId) */
    private java.util.Map<String, String> attackerSessions = new java.util.HashMap<>();

    /** Number of attacks performed by entity in current combat (for ATTACK_FLEE: attack once then flee) */
    private int combatAttackCount;

    /** Current schedule phase name (null if no schedule or not yet determined) */
    private String currentSchedulePhase;

    // --- Dialog pause state (in-memory only) ---

    /** Player IDs currently in dialog with this entity, mapped to start timestamp. */
    private final java.util.Map<String, Long> dialogList = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long DIALOG_STALE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    /**
     * Add a player to the dialog list.
     * @return true if this was the first player (NPC should stop moving)
     */
    public boolean dialogStart(String playerId) {
        boolean wasEmpty = dialogList.isEmpty();
        dialogList.put(playerId, System.currentTimeMillis());
        return wasEmpty;
    }

    /**
     * Remove a player from the dialog list.
     * @return true if the list is now empty (NPC can move again)
     */
    public boolean dialogEnd(String playerId) {
        dialogList.remove(playerId);
        return dialogList.isEmpty();
    }

    /**
     * Remove stale dialog entries (older than 30 minutes).
     */
    public void cleanupStaleDialogs() {
        long now = System.currentTimeMillis();
        dialogList.entrySet().removeIf(e -> (now - e.getValue()) > DIALOG_STALE_TIMEOUT_MS);
    }

    /**
     * Clear all dialogs (e.g. on schedule phase change).
     */
    public void clearDialogs() {
        dialogList.clear();
    }

    /**
     * Check if entity is currently in dialog with any player.
     */
    public boolean isInDialog() {
        return !dialogList.isEmpty();
    }

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
        return EntityServerData.getLong(entity, key, defaultValue);
    }

    /**
     * Get combat duration from entity server properties.
     * Property: combat_duration (seconds), default 15.
     */
    public long getCombatDurationMs() {
        return getServerLong("combat_duration", CombatStrategy.DEFAULT_COMBAT_DURATION_MS / 1000) * 1000;
    }

    /**
     * Enter combat mode with a new attacker.
     */
    public void enterCombat(String attackerEntityId, String sessionId, long currentTime) {
        this.inCombat = true;
        this.combatEndTime = currentTime + getCombatDurationMs();
        if (attackerEntityId != null && sessionId != null) {
            this.attackerSessions.put(attackerEntityId, sessionId);
        }
    }

    /**
     * Refresh combat timer (e.g., on repeated hits).
     */
    public void refreshCombat(String attackerEntityId, String sessionId, long currentTime) {
        this.combatEndTime = currentTime + getCombatDurationMs();
        if (attackerEntityId != null && sessionId != null) {
            this.attackerSessions.put(attackerEntityId, sessionId);
        }
    }

    /**
     * Exit combat mode and reset combat state.
     */
    public void exitCombat() {
        this.inCombat = false;
        this.combatEndTime = 0;
        this.attackerSessions.clear();
        this.combatAttackCount = 0;
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
