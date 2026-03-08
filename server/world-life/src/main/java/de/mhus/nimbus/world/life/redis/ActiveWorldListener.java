package de.mhus.nimbus.world.life.redis;

import de.mhus.nimbus.world.life.service.LifePodRegistrationService;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Listens for world activation notifications on the global Redis channel "world:global:active".
 * When a player logs in (including into an instance), the AccessService publishes the
 * effective worldId. This listener registers it with WorldDiscoveryService so that
 * all per-world Redis listeners (chunk registration, entity interaction, etc.)
 * can dynamically subscribe to the new world/instance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActiveWorldListener {

    private final WorldRedisMessagingService redisMessaging;
    private final WorldDiscoveryService worldDiscoveryService;
    private final LifePodRegistrationService lifePodRegistrationService;

    @PostConstruct
    public void initialize() {
        redisMessaging.subscribeGlobal("active", (topic, message) -> handleWorldActive(message));
        log.info("ActiveWorldListener initialized - listening for world activation events");
    }

    private void handleWorldActive(String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return;
        }
        try {
            String trimmedWorldId = worldId.trim();
            boolean isNew = worldDiscoveryService.registerDynamicWorld(trimmedWorldId);
            lifePodRegistrationService.registerForWorld(trimmedWorldId);
            if (isNew) {
                log.info("World activated via Redis notification: {}", trimmedWorldId);
            }
        } catch (Exception e) {
            log.warn("Failed to handle world activation for '{}': {}", worldId, e.getMessage());
        }
    }
}
