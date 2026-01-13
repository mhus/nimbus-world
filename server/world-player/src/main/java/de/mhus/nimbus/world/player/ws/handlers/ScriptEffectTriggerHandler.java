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
 * Handles effect trigger messages from clients.
 * Message type: "s.t" (Script Effect Trigger, Client → Server)
 *
 * Client sends effect trigger when player triggers an effect (e.g., entering an area).
 * Server publishes to Redis for multi-pod broadcasting.
 *
 * Expected data:
 * {
 *   "entityId": "@player_1234",  // optional, source entity
 *   "effectId": "effect_123",
 *   "chunks": [{"x":1,"z":4}, {"x":2,"z":4}],  // optional, affected chunks
 *   "effect": {  // ScriptActionDefinition
 *     ...
 *   }
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScriptEffectTriggerHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final WorldRedisMessagingService redisMessaging;

    @Override
    public String getMessageType() {
        return "s.t";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Effect trigger from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        // Extract effect data
        String effectId = data.has("effectId") ? data.get("effectId").asText() : null;
        String entityId = data.has("entityId") ? data.get("entityId").asText() : null;
        JsonNode chunks = data.has("chunks") ? data.get("chunks") : null;
        JsonNode effect = data.has("effect") ? data.get("effect") : null;

        if (effectId == null || effect == null) {
            log.warn("Effect trigger without effectId or effect definition");
            return;
        }

        // TODO: Validate effect definition
        // TODO: Check permissions (player allowed to trigger this effect?)

        // Publish to Redis for multi-pod broadcasting
        publishToRedis(session, data);

        log.debug("Effect trigger: effectId={}, entityId={}, session={}",
                effectId, entityId, session.getSessionId());
    }

    /**
     * Publish effect trigger to Redis for broadcasting to all pods.
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
            if (originalData.has("entityId")) enriched.put("entityId", originalData.get("entityId").asText());
            if (originalData.has("effectId")) enriched.put("effectId", originalData.get("effectId").asText());
            if (originalData.has("chunks")) enriched.set("chunks", originalData.get("chunks"));
            if (originalData.has("effect")) enriched.set("effect", originalData.get("effect"));

            String json = objectMapper.writeValueAsString(enriched);
            redisMessaging.publish(session.getWorldId().getId(), "s.t", json);

            log.trace("Published effect trigger to Redis: worldId={}, sessionId={}",
                    session.getWorldId(), session.getSessionId());

        } catch (Exception e) {
            log.error("Failed to publish effect trigger to Redis", e);
        }
    }
}
