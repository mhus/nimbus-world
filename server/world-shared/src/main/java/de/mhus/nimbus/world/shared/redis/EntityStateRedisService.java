package de.mhus.nimbus.world.shared.redis;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Waypoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Central Redis storage for entity runtime state.
 * Extends the existing per-entity Redis data (npc-pathway, chunk-entities)
 * with a persistent state hash.
 *
 * Key: world:{worldId}:npc-state:{entityId}
 * Hash fields: lifecycle, health, healthMax
 *
 * Written by world-life, read by world-player and world-life.
 * TTL refreshed on every update (default 10min, longer than pathway cache).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityStateRedisService {

    public static final String LIFECYCLE_ALIVE = "ALIVE";
    public static final String LIFECYCLE_DEAD = "DEAD";
    public static final String LIFECYCLE_GONE = "GONE";

    private static final String KEY_PREFIX = "npc-state:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private static final String FIELD_LIFECYCLE = "lifecycle";
    private static final String FIELD_HEALTH = "health";
    private static final String FIELD_HEALTH_MAX = "healthMax";
    private static final String FIELD_SCHEDULE_PHASE = "schedulePhase";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * Update entity state in Redis.
     */
    public void updateState(String worldId, String entityId, String lifecycle, double health, double healthMax) {
        String key = key(worldId, entityId);
        var ops = redis.opsForHash();
        ops.putAll(key, Map.of(
                FIELD_LIFECYCLE, lifecycle,
                FIELD_HEALTH, String.valueOf(health),
                FIELD_HEALTH_MAX, String.valueOf(healthMax)
        ));
        redis.expire(key, TTL);
    }

    /**
     * Set lifecycle state only (e.g. on death or gone transition).
     */
    public void setLifecycle(String worldId, String entityId, String lifecycle) {
        String key = key(worldId, entityId);
        redis.opsForHash().put(key, FIELD_LIFECYCLE, lifecycle);
        redis.expire(key, TTL);
    }

    /**
     * Update health values (e.g. after damage).
     */
    public void updateHealth(String worldId, String entityId, double health, double healthMax) {
        String key = key(worldId, entityId);
        var ops = redis.opsForHash();
        ops.put(key, FIELD_HEALTH, String.valueOf(health));
        ops.put(key, FIELD_HEALTH_MAX, String.valueOf(healthMax));
        redis.expire(key, TTL);
    }

    /**
     * Remove entity state (e.g. on respawn or unload).
     */
    public void remove(String worldId, String entityId) {
        redis.delete(key(worldId, entityId));
    }

    /**
     * Get lifecycle state. Empty means ALIVE (key absent).
     */
    public String getLifecycle(String worldId, String entityId) {
        Object val = redis.opsForHash().get(key(worldId, entityId), FIELD_LIFECYCLE);
        return val != null ? val.toString() : LIFECYCLE_ALIVE;
    }

    public boolean isDead(String worldId, String entityId) {
        return LIFECYCLE_DEAD.equals(getLifecycle(worldId, entityId));
    }

    public boolean isGone(String worldId, String entityId) {
        return LIFECYCLE_GONE.equals(getLifecycle(worldId, entityId));
    }

    public boolean isAlive(String worldId, String entityId) {
        return LIFECYCLE_ALIVE.equals(getLifecycle(worldId, entityId));
    }

    /**
     * Set the current schedule phase name for an entity.
     */
    public void setSchedulePhase(String worldId, String entityId, String phaseName) {
        String key = key(worldId, entityId);
        redis.opsForHash().put(key, FIELD_SCHEDULE_PHASE, phaseName != null ? phaseName : "");
        redis.expire(key, TTL);
    }

    /**
     * Get the current schedule phase name. Empty string or null means no phase.
     */
    public String getSchedulePhase(String worldId, String entityId) {
        Object val = redis.opsForHash().get(key(worldId, entityId), FIELD_SCHEDULE_PHASE);
        return val != null ? val.toString() : null;
    }

    /**
     * Get full state as map. Returns empty map if no state exists.
     */
    public Map<Object, Object> getState(String worldId, String entityId) {
        return redis.opsForHash().entries(key(worldId, entityId));
    }

    public Optional<Double> getHealth(String worldId, String entityId) {
        Object val = redis.opsForHash().get(key(worldId, entityId), FIELD_HEALTH);
        if (val == null) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(val.toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // --- Looter management (players eligible for loot) ---

    /**
     * Set the looters (players who attacked this entity) on death.
     * Stored as a Redis Set alongside the state hash.
     */
    public void setLooters(String worldId, String entityId, Set<String> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) return;
        String looterKey = looterKey(worldId, entityId);
        redis.opsForSet().add(looterKey, playerIds.toArray(String[]::new));
        redis.expire(looterKey, TTL);
    }

    /**
     * Check if a player is eligible for loot (was an attacker).
     */
    public boolean isLooter(String worldId, String entityId, String playerId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(looterKey(worldId, entityId), playerId));
    }

    /**
     * Remove a player from the looter set after looting.
     * @return true if the player was in the set and was removed
     */
    public boolean removeLooter(String worldId, String entityId, String playerId) {
        Long removed = redis.opsForSet().remove(looterKey(worldId, entityId), playerId);
        return removed != null && removed > 0;
    }

    /**
     * Remove entity state (e.g. on respawn or unload). Also removes looter set.
     */
    public void removeAll(String worldId, String entityId) {
        redis.delete(key(worldId, entityId));
        redis.delete(looterKey(worldId, entityId));
    }

    // --- Position from pathway ---

    /**
     * Calculate the current position of an entity by loading its pathway from Redis
     * and interpolating between waypoints based on the current timestamp.
     * Same algorithm as the client engine (EntityService.ts).
     *
     * @return interpolated position or null if no pathway exists
     */
    public Vector3 getCurrentPosition(String worldId, String entityId) {
        String pathwayKey = "world:" + fullWorldId(worldId) + ":npc-pathway:" + entityId;
        String json = redis.opsForValue().get(pathwayKey);
        if (json == null) return null;

        try {
            EntityPathway pathway = objectMapper.readValue(json, EntityPathway.class);
            List<Waypoint> waypoints = pathway.getWaypoints();
            if (waypoints == null || waypoints.isEmpty()) return null;

            long now = System.currentTimeMillis();

            // Before first waypoint → first position
            if (now <= waypoints.getFirst().getTimestamp()) {
                return copyTarget(waypoints.getFirst());
            }

            // Past last waypoint → last position
            if (now >= waypoints.getLast().getTimestamp()) {
                return copyTarget(waypoints.getLast());
            }

            // Find segment and interpolate
            for (int i = 0; i < waypoints.size() - 1; i++) {
                Waypoint from = waypoints.get(i);
                Waypoint to = waypoints.get(i + 1);
                if (now >= from.getTimestamp() && now < to.getTimestamp()) {
                    double t = (double) (now - from.getTimestamp()) / (to.getTimestamp() - from.getTimestamp());
                    return Vector3.builder()
                            .x(from.getTarget().getX() + (to.getTarget().getX() - from.getTarget().getX()) * t)
                            .y(from.getTarget().getY() + (to.getTarget().getY() - from.getTarget().getY()) * t)
                            .z(from.getTarget().getZ() + (to.getTarget().getZ() - from.getTarget().getZ()) * t)
                            .build();
                }
            }

            return copyTarget(waypoints.getLast());
        } catch (Exception e) {
            log.warn("Failed to parse pathway for entity {} in world {}: {}", entityId, worldId, e.getMessage());
            return null;
        }
    }

    private Vector3 copyTarget(Waypoint wp) {
        if (wp.getTarget() == null) return null;
        return Vector3.builder()
                .x(wp.getTarget().getX())
                .y(wp.getTarget().getY())
                .z(wp.getTarget().getZ())
                .build();
    }

    private String fullWorldId(String worldId) {
        return WorldId.unchecked(worldId).getFullId();
    }

    private String key(String worldId, String entityId) {
        return "world:" + fullWorldId(worldId) + ":" + KEY_PREFIX + entityId;
    }

    private String looterKey(String worldId, String entityId) {
        return "world:" + fullWorldId(worldId) + ":" + KEY_PREFIX + entityId + ":looters";
    }
}
