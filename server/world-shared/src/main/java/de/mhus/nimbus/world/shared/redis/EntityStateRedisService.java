package de.mhus.nimbus.world.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

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

    private final StringRedisTemplate redis;

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

    private String key(String worldId, String entityId) {
        return "world:" + worldId + ":" + KEY_PREFIX + entityId;
    }
}
