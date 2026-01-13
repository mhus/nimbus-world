package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles effect parameter update messages from clients.
 * Message type: "s.u" (Script Effect Update, Client → Server)
 *
 * Client sends parameter updates when effect variables change during execution.
 * Server publishes to Redis for multi-pod broadcasting.
 *
 * Expected data:
 * {
 *   "effectId": "effect_123",
 *   "paramName": "targetPos",
 *   "value": {"x": -1.5, "y": 66.5, "z": 1.5},
 *   "chunks": [{"cx":0,"cz":0}, {"cx":-1,"cz":0}],
 *   "targeting": {...}  // optional, currently not used
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScriptEffectUpdateHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final WorldRedisMessagingService redisMessaging;

    @Override
    public String getMessageType() {
        return "s.u";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Effect update from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        // Extract effect data
        String effectId = data.has("effectId") ? data.get("effectId").asText() : null;
        JsonNode chunks = data.get("chunks");
        JsonNode paramName = data.get("paramName");
        JsonNode value = data.get("value");

        if (effectId == null || paramName == null || value == null || chunks == null) {
            log.warn("Effect update without values");
            return;
        }

        // TODO: Validate variables
        // TODO: Check permissions (player allowed to update this effect?)

        // Publish to Redis for multi-pod broadcasting
        publishToRedis(session, data);

        log.debug("Effect update: effectId={}, session={}, data={}",
                effectId, session.getSessionId(), data);
    }

    /**
     * Publish effect update to Redis for broadcasting to all pods.
     */
    private void publishToRedis(PlayerSession session, JsonNode originalData) {
        try {
            // Build enriched message with session info
            ObjectNode enriched = objectMapper.createObjectNode();
            enriched.put("sessionId", session.getSessionId());
            enriched.put("worldId", session.getWorldId().getId());
            enriched.put("userId", session.getPlayer().user().getUserId());
            enriched.put("title", session.getTitle());

            // Copy original data
            if (originalData.has("effectId")) enriched.put("effectId", originalData.get("effectId").asText());
            if (originalData.has("paramName")) enriched.put("paramName", originalData.get("paramName").asText());
            if (originalData.has("value")) enriched.set("value", originalData.get("value"));
            if (originalData.has("chunks")) enriched.set("chunks", originalData.get("chunks"));
            if (originalData.has("targeting")) enriched.set("targeting", originalData.get("targeting"));

            String json = objectMapper.writeValueAsString(enriched);
            redisMessaging.publish(session.getWorldId().getId(), "s.u", json);

            log.trace("Published effect update to Redis: worldId={}, sessionId={}",
                    session.getWorldId(), session.getSessionId());

        } catch (Exception e) {
            log.error("Failed to publish effect update to Redis", e);
        }
    }
}
