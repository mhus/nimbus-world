package de.mhus.nimbus.world.life.redis;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes combat feedback to remote servers via Redis.
 * Channel: world:{worldId}:remote.combat.feedback
 *
 * Events: damaged, died, respawned
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoteCombatFeedbackPublisher {

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;

    public void publishDamaged(WorldId worldId, String entityId, double damage, double health, String attackerId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "damaged");
            payload.put("entityId", entityId);
            payload.put("damage", damage);
            payload.put("health", health);
            payload.put("attackerId", attackerId);

            String json = objectMapper.writeValueAsString(payload);
            redisMessaging.publish(worldId.getId(), "remote.combat.feedback", json);
            log.debug("World {}: Published combat feedback damaged for entity {}", worldId, entityId);

        } catch (Exception e) {
            log.error("World {}: Failed to publish combat feedback for {}", worldId, entityId, e);
        }
    }

    public void publishDied(WorldId worldId, String entityId, String killerId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "died");
            payload.put("entityId", entityId);
            payload.put("killerId", killerId);

            String json = objectMapper.writeValueAsString(payload);
            redisMessaging.publish(worldId.getId(), "remote.combat.feedback", json);
            log.debug("World {}: Published combat feedback died for entity {}", worldId, entityId);

        } catch (Exception e) {
            log.error("World {}: Failed to publish combat feedback died for {}", worldId, entityId, e);
        }
    }

    public void publishRespawned(WorldId worldId, String entityId, double x, double y, double z) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "respawned");
            payload.put("entityId", entityId);
            payload.put("x", x);
            payload.put("y", y);
            payload.put("z", z);

            String json = objectMapper.writeValueAsString(payload);
            redisMessaging.publish(worldId.getId(), "remote.combat.feedback", json);
            log.debug("World {}: Published combat feedback respawned for entity {}", worldId, entityId);

        } catch (Exception e) {
            log.error("World {}: Failed to publish combat feedback respawned for {}", worldId, entityId, e);
        }
    }
}
