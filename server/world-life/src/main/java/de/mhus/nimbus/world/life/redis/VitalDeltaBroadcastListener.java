package de.mhus.nimbus.world.life.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.service.SimulatorService;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Listens for vital delta messages targeting NPC entities on this pod.
 * Channel: world:{worldId}:v.d.e (entity vital deltas)
 *
 * Dynamically subscribes to all enabled worlds discovered from MongoDB.
 *
 * Currently logs received deltas since NPC vitals are not yet implemented.
 * When NPC vitals are added to SimulationState, this listener will apply
 * the deltas to the target entity's health/mana/stamina.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VitalDeltaBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final SimulatorService simulatorService;
    private final WorldDiscoveryService worldDiscoveryService;
    private final ObjectMapper objectMapper;

    private final Set<WorldId> subscribedWorlds = new HashSet<>();

    @PostConstruct
    public void initialize() {
        updateSubscriptions();
    }

    @Scheduled(fixedDelay = 60000)
    public void updateSubscriptions() {
        Set<WorldId> knownWorlds = worldDiscoveryService.getKnownWorldIds();

        for (WorldId worldId : knownWorlds) {
            if (!subscribedWorlds.contains(worldId)) {
                redisMessaging.subscribe(worldId.getId(), "v.d.e", (topic, message) -> handleVitalDelta(worldId, message));
                subscribedWorlds.add(worldId);
                log.info("Subscribed to entity vital deltas for world: {}", worldId);
            }
        }

        Set<WorldId> toRemove = new HashSet<>(subscribedWorlds);
        toRemove.removeAll(knownWorlds);
        for (WorldId worldId : toRemove) {
            redisMessaging.unsubscribe(worldId.getId(), "v.d.e");
            subscribedWorlds.remove(worldId);
            log.info("Unsubscribed from entity vital deltas for world: {}", worldId);
        }
    }

    private void handleVitalDelta(WorldId worldId, String message) {
        try {
            VitalDeltaBroadcastMessage delta = objectMapper.readValue(message, VitalDeltaBroadcastMessage.class);

            if (delta.getTargetEntityId() == null || delta.getVitalType() == null) {
                log.warn("Invalid vital delta message for world {}: missing targetEntityId or vitalType", worldId);
                return;
            }

            var state = simulatorService.findSimulationState(worldId, delta.getTargetEntityId());
            if (state == null) {
                log.trace("Vital delta target entity {} not loaded on this pod in world {}",
                        delta.getTargetEntityId(), worldId);
                return;
            }

            // TODO: Apply delta to entity vitals when NPC vital system is implemented.
            // For now, log the received delta for debugging.
            log.debug("World {}: Received vital delta for entity {}: {} {} (from {})",
                    worldId, delta.getTargetEntityId(), delta.getVitalType(),
                    delta.getDelta(), delta.getSourceEntityId());

        } catch (Exception e) {
            log.error("Failed to handle vital delta for world {}: {}", worldId, e.getMessage(), e);
        }
    }
}
