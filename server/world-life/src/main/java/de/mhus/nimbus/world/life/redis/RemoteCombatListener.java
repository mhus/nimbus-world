package de.mhus.nimbus.world.life.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.service.SimulatorService;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.redis.VitalDeltaPublisher;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Listens for combat commands from remote servers via Redis.
 * Channel: world:{worldId}:remote.combat
 *
 * Deserializes RemoteCombatCommand, checks ownership, and routes
 * attack actions into the existing combat pipeline via VitalDeltaPublisher.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoteCombatListener {

    private final WorldRedisMessagingService redisMessaging;
    private final WorldDiscoveryService worldDiscoveryService;
    private final SimulatorService simulatorService;
    private final VitalDeltaPublisher vitalDeltaPublisher;
    private final ObjectMapper objectMapper;

    private final Set<WorldId> subscribedWorlds = new HashSet<>();

    @PostConstruct
    public void initialize() {
        worldDiscoveryService.addWorldActivationListener(this::subscribeToWorld);
        updateSubscriptions();
    }

    private synchronized void subscribeToWorld(WorldId worldId) {
        if (subscribedWorlds.contains(worldId)) return;
        redisMessaging.subscribe(worldId.getId(), "remote.combat",
                (topic, message) -> handleRemoteCombat(worldId, message));
        subscribedWorlds.add(worldId);
        log.info("Subscribed to remote combat for world: {}", worldId);
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
            redisMessaging.unsubscribe(worldId.getId(), "remote.combat");
            subscribedWorlds.remove(worldId);
            log.info("Unsubscribed from remote combat for world: {}", worldId);
        }
    }

    private void handleRemoteCombat(WorldId worldId, String message) {
        try {
            RemoteCombatCommand cmd = objectMapper.readValue(message, RemoteCombatCommand.class);

            if (cmd.getEntityId() == null || cmd.getTargetEntityId() == null) {
                log.warn("Remote combat command missing entityId or targetEntityId for world {}", worldId);
                return;
            }

            // Check if the attacking entity is loaded on this pod
            var state = simulatorService.findSimulationState(worldId, cmd.getEntityId());
            if (state == null) {
                log.trace("Remote combat: entity {} not loaded on this pod in world {}",
                        cmd.getEntityId(), worldId);
                return;
            }

            if ("attack".equals(cmd.getAction())) {
                // Route into existing combat pipeline
                vitalDeltaPublisher.publishAttack(
                        worldId.getId(),
                        cmd.getTargetEntityId(),
                        cmd.getEntityId(),
                        cmd.getPhysicalDamage(),
                        cmd.getPhysicalAccuracy(),
                        cmd.getMagicalDamage(),
                        cmd.getMagicalAccuracy(),
                        cmd.getCritChance(),
                        cmd.getCritMultiplier()
                );
                log.debug("World {}: Remote attack from {} -> {}", worldId, cmd.getEntityId(), cmd.getTargetEntityId());
            } else {
                log.warn("World {}: Unknown remote combat action: {}", worldId, cmd.getAction());
            }

        } catch (Exception e) {
            log.error("Failed to handle remote combat for world {}: {}", worldId, e.getMessage(), e);
        }
    }
}
