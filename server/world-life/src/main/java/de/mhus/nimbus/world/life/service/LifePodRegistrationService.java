package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.shared.redis.WorldRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers this world-life pod's URL in Redis for each active world.
 * Other services can look up which life pods are responsible for a given world.
 *
 * Redis key: world:{fullWorldId}:life-pods → SET of internal pod URLs
 * TTL: 30 seconds (refreshed by heartbeat every 10 seconds)
 *
 * Flow:
 * - registerForWorld(): called when a world becomes active on this pod
 * - unregisterForWorld(): called when no chunks remain for a world
 * - heartbeat(): refreshes TTL for all registered worlds (every 10s)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LifePodRegistrationService {

    private static final String REDIS_KEY = "life-pods";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final WorldRedisService worldRedisService;
    private final LocationService locationService;

    private final Set<String> registeredWorlds = ConcurrentHashMap.newKeySet();

    /**
     * Register this pod for a world.
     * Called when a world becomes active (player login notification).
     *
     * @param worldId Full world ID (e.g. "earth616:westview::inst123")
     */
    public void registerForWorld(String worldId) {
        String podUrl = locationService.getInternalServerUrl();
        worldRedisService.addToSet(worldId, REDIS_KEY, podUrl);
        worldRedisService.setExpire(worldId, REDIS_KEY, TTL);
        registeredWorlds.add(worldId);
        log.debug("Registered life pod {} for world {}", podUrl, worldId);
    }

    /**
     * Unregister this pod from a world.
     * Called when no active chunks remain for the world.
     *
     * @param worldId Full world ID
     */
    public void unregisterForWorld(String worldId) {
        String podUrl = locationService.getInternalServerUrl();
        worldRedisService.removeFromSet(worldId, REDIS_KEY, podUrl);
        registeredWorlds.remove(worldId);
        log.debug("Unregistered life pod {} from world {}", podUrl, worldId);
    }

    /**
     * Get all life pod URLs registered for a world.
     *
     * @param worldId Full world ID
     * @return Set of internal pod URLs
     */
    public Set<String> getPodsForWorld(String worldId) {
        return worldRedisService.getSetMembers(worldId, REDIS_KEY);
    }

    /**
     * Heartbeat: refresh TTL for all registered worlds.
     * Ensures stale entries expire if this pod crashes.
     */
    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        if (registeredWorlds.isEmpty()) return;

        for (String worldId : registeredWorlds) {
            try {
                worldRedisService.setExpire(worldId, REDIS_KEY, TTL);
            } catch (Exception e) {
                log.warn("Failed to refresh life-pod TTL for world {}: {}", worldId, e.getMessage());
            }
        }
        log.trace("Refreshed life-pod TTL for {} worlds", registeredWorlds.size());
    }
}
