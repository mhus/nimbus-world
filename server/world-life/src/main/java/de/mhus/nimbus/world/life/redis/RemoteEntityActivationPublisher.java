package de.mhus.nimbus.world.life.redis;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes activation/deactivation events for REMOTE entities via Redis.
 * Channel: world:{worldId}:remote.entity.activate
 *
 * External servers subscribe to know when REMOTE entities are loaded/unloaded on this pod.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoteEntityActivationPublisher {

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;

    /**
     * Publish activation event when a REMOTE entity is loaded.
     */
    public void publishActivate(WorldId worldId, WEntity entity) {
        if (entity.getType() != WEntityType.REMOTE) return;

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", "activate");
            payload.put("entityId", entity.getName());
            if (entity.getPosition() != null) {
                payload.put("x", entity.getPosition().getX());
                payload.put("y", entity.getPosition().getY());
                payload.put("z", entity.getPosition().getZ());
            }

            String json = objectMapper.writeValueAsString(payload);
            redisMessaging.publish(worldId.getId(), "remote.entity.activate", json);
            log.debug("World {}: Published REMOTE entity activate: {}", worldId, entity.getName());

        } catch (Exception e) {
            log.error("World {}: Failed to publish remote entity activate for {}", worldId, entity.getName(), e);
        }
    }

    /**
     * Publish deactivation event when a REMOTE entity is unloaded.
     */
    public void publishDeactivate(WorldId worldId, WEntity entity) {
        if (entity.getType() != WEntityType.REMOTE) return;

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", "deactivate");
            payload.put("entityId", entity.getName());
            if (entity.getPosition() != null) {
                payload.put("x", entity.getPosition().getX());
                payload.put("y", entity.getPosition().getY());
                payload.put("z", entity.getPosition().getZ());
            }

            String json = objectMapper.writeValueAsString(payload);
            redisMessaging.publish(worldId.getId(), "remote.entity.activate", json);
            log.debug("World {}: Published REMOTE entity deactivate: {}", worldId, entity.getName());

        } catch (Exception e) {
            log.error("World {}: Failed to publish remote entity deactivate for {}", worldId, entity.getName(), e);
        }
    }
}
