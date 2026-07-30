package de.mhus.nimbus.world.life.redis;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.behavior.RemotePathwayQueue;
import de.mhus.nimbus.world.life.service.SimulatorService;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Listens for pathways from remote servers via Redis.
 * Channel: world:{worldId}:remote.pathway
 *
 * Deserializes EntityPathway, checks if the entity is loaded on this pod,
 * and enqueues into RemotePathwayQueue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemotePathwayListener {

    private final WorldRedisMessagingService redisMessaging;
    private final WorldDiscoveryService worldDiscoveryService;
    private final SimulatorService simulatorService;
    private final RemotePathwayQueue remotePathwayQueue;
    private final EngineMapper engineMapper;

    private final Set<WorldId> subscribedWorlds = new HashSet<>();

    @PostConstruct
    public void initialize() {
        worldDiscoveryService.addWorldActivationListener(this::subscribeToWorld);
        updateSubscriptions();
    }

    private synchronized void subscribeToWorld(WorldId worldId) {
        if (subscribedWorlds.contains(worldId)) return;
        redisMessaging.subscribe(worldId.getId(), "remote.pathway",
                (topic, message) -> handleRemotePathway(worldId, message));
        subscribedWorlds.add(worldId);
        log.info("Subscribed to remote pathways for world: {}", worldId);
    }

    @Scheduled(fixedDelay = 10000)
    public void updateSubscriptions() {
        Set<WorldId> knownWorlds = worldDiscoveryService.getKnownWorldIds();

        for (WorldId worldId : knownWorlds) {
            subscribeToWorld(worldId);
        }

        Set<WorldId> toRemove = new HashSet<>(subscribedWorlds);
        toRemove.removeAll(knownWorlds);
        for (WorldId worldId : toRemove) {
            redisMessaging.unsubscribe(worldId.getId(), "remote.pathway");
            subscribedWorlds.remove(worldId);
            log.info("Unsubscribed from remote pathways for world: {}", worldId);
        }
    }

    private void handleRemotePathway(WorldId worldId, String message) {
        try {
            EntityPathway pathway = engineMapper.readValue(message, EntityPathway.class);

            if (pathway.getEntityId() == null) {
                log.warn("Remote pathway without entityId for world {}", worldId);
                return;
            }

            // Check if entity is loaded on this pod
            var state = simulatorService.findSimulationState(worldId, pathway.getEntityId());
            if (state == null) {
                log.trace("Remote pathway for entity {} not loaded on this pod in world {}",
                        pathway.getEntityId(), worldId);
                return;
            }

            remotePathwayQueue.offer(worldId.getId(), pathway.getEntityId(), pathway);
            log.debug("World {}: Queued remote pathway for entity {}", worldId, pathway.getEntityId());

        } catch (Exception e) {
            log.error("Failed to handle remote pathway for world {}: {}", worldId, e.getMessage(), e);
        }
    }
}
