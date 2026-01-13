package de.mhus.nimbus.world.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-based distributed lock service for chunk updates.
 * Prevents concurrent chunk regeneration across world-control instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorldRedisLockService {

    private final StringRedisTemplate redis;

    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(1);
    private static final String LOCK_PREFIX = "lock:chunk-update:";

    /**
     * Acquire a lock for chunk updates.
     *
     * @param worldId World identifier
     * @return Lock token if acquired, null if lock is held by another process
     */
    public String acquireLock(String worldId) {
        return acquireLock(worldId, DEFAULT_LOCK_TTL);
    }

    /**
     * Acquire a lock for chunk updates with custom TTL.
     *
     * @param worldId World identifier
     * @param ttl Lock time-to-live
     * @return Lock token if acquired, null if lock is held by another process
     */
    public String acquireLock(String worldId, Duration ttl) {
        String lockKey = lockKey(worldId);
        String token = UUID.randomUUID().toString();

        // SET NX (only if not exists) with TTL
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, token, ttl);

        if (Boolean.TRUE.equals(acquired)) {
            log.trace("Acquired chunk update lock: world={} token={} ttl={}ms",
                    worldId, token, ttl.toMillis());
            return token;
        }

        log.trace("Failed to acquire chunk update lock (already held): world={}", worldId);
        return null;
    }

    /**
     * Refresh/extend an existing lock.
     *
     * @param worldId World identifier
     * @param token Lock token from acquisition
     * @param ttl New TTL
     * @return true if lock was refreshed, false if token invalid or lock expired
     */
    public boolean refreshLock(String worldId, String token, Duration ttl) {
        String lockKey = lockKey(worldId);
        String currentToken = redis.opsForValue().get(lockKey);

        if (token.equals(currentToken)) {
            redis.expire(lockKey, ttl);
            log.trace("Refreshed chunk update lock: world={} ttl={}ms", worldId, ttl.toMillis());
            return true;
        }

        log.warn("Failed to refresh lock (token mismatch or expired): world={}", worldId);
        return false;
    }

    /**
     * Release a lock.
     *
     * @param worldId World identifier
     * @param token Lock token from acquisition
     * @return true if lock was released, false if token invalid
     */
    public boolean releaseLock(String worldId, String token) {
        String lockKey = lockKey(worldId);
        String currentToken = redis.opsForValue().get(lockKey);

        if (token.equals(currentToken)) {
            redis.delete(lockKey);
            log.trace("Released chunk update lock: world={}", worldId);
            return true;
        }

        log.warn("Failed to release lock (token mismatch or already expired): world={}", worldId);
        return false;
    }

    /**
     * Check if a lock is currently held.
     *
     * @param worldId World identifier
     * @return true if lock exists
     */
    public boolean isLocked(String worldId) {
        String lockKey = lockKey(worldId);
        return Boolean.TRUE.equals(redis.hasKey(lockKey));
    }

    /**
     * Acquire a generic lock with custom key.
     *
     * @param lockKey Full lock key (e.g., "job:123", "export:world-abc")
     * @param ttl Lock time-to-live
     * @return Lock token if acquired, null if lock is held by another process
     */
    public String acquireGenericLock(String lockKey, Duration ttl) {
        String fullKey = "world:lock:" + lockKey;
        String token = UUID.randomUUID().toString();

        Boolean acquired = redis.opsForValue().setIfAbsent(fullKey, token, ttl);

        if (Boolean.TRUE.equals(acquired)) {
            log.trace("Acquired lock: key={} token={} ttl={}ms",
                    lockKey, token, ttl.toMillis());
            return token;
        }

        log.trace("Failed to acquire lock (already held): key={}", lockKey);
        return null;
    }

    /**
     * Release a generic lock.
     *
     * @param lockKey Full lock key
     * @param token Lock token from acquisition
     * @return true if lock was released, false if token invalid
     */
    public boolean releaseGenericLock(String lockKey, String token) {
        String fullKey = "world:lock:" + lockKey;
        String currentToken = redis.opsForValue().get(fullKey);

        if (token.equals(currentToken)) {
            redis.delete(fullKey);
            log.trace("Released lock: key={}", lockKey);
            return true;
        }

        log.warn("Failed to release lock (token mismatch or already expired): key={}", lockKey);
        return false;
    }

    private String lockKey(String worldId) {
        return "world:" + worldId + ":" + LOCK_PREFIX;
    }
}
