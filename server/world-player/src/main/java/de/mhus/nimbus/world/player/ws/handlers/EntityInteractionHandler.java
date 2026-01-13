package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles entity interaction messages from clients.
 * Message type: "e.int.r" (Entity Interaction Request, Client → Server)
 *
 * Client sends entity interactions when player interacts with NPCs or entities.
 * Server processes the interaction (currently just logging).
 *
 * Expected data:
 * {
 *   "entityId": "npc_farmer_001",
 *   "ts": 1697045600000,  // timestamp
 *   "ac": "click",  // action: 'click', 'fireShortcut', 'use', 'talk', 'attack', 'touch', etc.
 *   "pa": {  // params
 *     "clickType": "left",  // for 'click' action
 *     "shortcutNr": 2,      // for 'fireShortcut' action
 *     ...
 *   }
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntityInteractionHandler implements MessageHandler {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService redisMessaging;
    private final GameplayService gameplay;

    @Override
    public String getMessageType() {
        return "e.int.r";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Entity interaction from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        // Extract interaction data
        String entityId = data.has("entityId") ? data.get("entityId").asText() : null;
        Long timestamp = data.has("ts") ? data.get("ts").asLong() : null;
        String action = data.has("ac") ? data.get("ac").asText() : null;
        JsonNode params = data.has("pa") ? data.get("pa") : null;

        if (entityId == null || action == null) {
            log.warn("Entity interaction without entityId or action");
            return;
        }

        log.trace("Entity interaction received: entityId={}, action={}, user={}",
                entityId, action, session.getTitle());

        if (entityId.startsWith("@")) {
            // this is a player d not send to life server, send to gameplay service
            gameplay.onPlayerEntityInteraction(session, entityId, action, timestamp, params);
        }

        // Publish interaction to Redis for world-life processing
        publishEntityInteraction(session, entityId, action, timestamp, params);

        log.debug("Entity interaction forwarded to world-life: entityId={}, action={}, user={}",
                entityId, action, session.getTitle());
    }

    /**
     * Publish entity interaction to Redis for world-life processing.
     * Channel: world:{worldId}:e.int
     *
     * Message format:
     * {
     *   "entityId": "cow2",
     *   "action": "click",
     *   "timestamp": 1234567890,
     *   "params": {...},
     *   "userId": "user123",
     *   "sessionId": "session-abc",
     *   "title": "Player"
     * }
     *
     * @param session Player session
     * @param entityId Entity ID being interacted with
     * @param action Interaction action type
     * @param timestamp Client timestamp
     * @param params Action-specific parameters
     */
    private void publishEntityInteraction(PlayerSession session, String entityId, String action,
                                          Long timestamp, JsonNode params) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode message = objectMapper.createObjectNode();
            message.put("entityId", entityId);
            message.put("action", action);
            message.put("timestamp", timestamp != null ? timestamp : System.currentTimeMillis());

            if (params != null) {
                message.set("params", params);
            }

            // Add session/player context
            message.put("userId", session.getPlayer().user().getUserId());
            message.put("sessionId", session.getSessionId());
            message.put("title", session.getTitle());

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(session.getWorldId().getId(), "e.int", json);

            log.trace("Published entity interaction to Redis: entityId={}, action={}", entityId, action);

        } catch (Exception e) {
            log.error("Failed to publish entity interaction to Redis: entityId={}, action={}",
                    entityId, action, e);
        }
    }
}
