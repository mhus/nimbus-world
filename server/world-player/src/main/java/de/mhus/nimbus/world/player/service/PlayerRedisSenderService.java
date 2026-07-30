package de.mhus.nimbus.world.player.service;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlayerRedisSenderService {

    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService redisMessaging;

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
    public void publishEntityInteraction(PlayerSession session, String entityId, String action,
                                         Long timestamp, JsonNode params) {
        try {
            tools.jackson.databind.node.ObjectNode message = objectMapper.createObjectNode();
            message.put("entityId", entityId);
            message.put("action", action);
            message.put("timestamp", timestamp != null ? timestamp : System.currentTimeMillis());

            if (params != null) {
                message.set("params", params);
            }

            // Add session/player context
            message.put("userId", session.getPlayer().user().getName());
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
