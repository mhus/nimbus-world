package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.session.WSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles user movement update messages from clients.
 * Message type: "u.m" (User Movement, Client → Server)
 *
 * Client sends player position and/or rotation updates.
 * Server publishes to Redis for multi-pod broadcasting.
 *
 * Expected data:
 * {
 *   "p": {"x": 100.5, "y": 65.0, "z": -200.5},  // optional, position
 *   "r": {"y": 90.0, "p": 0.0}  // optional, rotation: yaw, pitch
 * }
 *
 * Redis broadcast includes sessionId and chunk coordinates:
 * {
 *   "sessionId": "abc123",
 *   "userId": "user123",
 *   "title": "Player",
 *   "p": {"x": 100.5, "y": 65.0, "z": -200.5},
 *   "r": {"y": 90.0, "p": 0.0},
 *   "cx": 6,  // chunk x coordinate
 *   "cz": -13 // chunk z coordinate
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserMovementUpdateHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final WorldRedisMessagingService redisMessaging;
    private final WSessionService wSessionService;

    @Override
    public String getMessageType() {
        return "u.m";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        log.debug("Received user movement update: sessionId={}, authenticated={}",
            session.getSessionId(), session.isAuthenticated());

        if (!session.isAuthenticated()) {
            log.warn("User movement update from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        // Extract position and rotation (both optional)
        JsonNode position = data.has("p") ? data.get("p") : null;
        JsonNode rotation = data.has("r") ? data.get("r") : null;

        if (position == null && rotation == null) {
            log.warn("User movement update without position or rotation");
            return;
        }

        // Extract coordinates if position present
        Double x = null, y = null, z = null;
        Integer cx = null, cz = null;
        if (position != null) {
            x = position.has("x") ? position.get("x").asDouble() : null;
            y = position.has("y") ? position.get("y").asDouble() : null;
            z = position.has("z") ? position.get("z").asDouble() : null;

            // Calculate chunk coordinates from world position
            if (x != null && z != null) {
                cx = (int) Math.floor(x / 16);  // chunkSize = 16
                cz = (int) Math.floor(z / 16);
            }
        }

        // TODO: Validate position (check world bounds)
        // TODO: Validate player is not moving too fast (anti-cheat)
        // TODO: Update player chunk registration if changed

        // Extract rotation if present
        Double yaw = null, pitch = null;
        if (rotation != null) {
            yaw = rotation.has("y") ? rotation.get("y").asDouble() : null;
            pitch = rotation.has("p") ? rotation.get("p").asDouble() : null;
        }

        // Store position and rotation in Redis (separate from WSession)
        log.debug("Storing position in Redis: sessionId={}, x={}, y={}, z={}, cx={}, cz={}",
            session.getSessionId(), x, y, z, cx, cz);
        try {
            wSessionService.updatePosition(session.getSessionId(), x, y, z, cx, cz, yaw, pitch);
            log.debug("Position stored successfully in Redis for session {}", session.getSessionId());
        } catch (Exception e) {
            log.error("Failed to store position in Redis for session {}", session.getSessionId(), e);
        }

        // Publish to Redis for multi-pod broadcasting
        publishToRedis(session, data, cx, cz);

        log.trace("User movement update: session={}, pos=({}, {}, {}), chunk=({}, {})",
                session.getSessionId(),
                x != null ? x : "null",
                y != null ? y : "null",
                z != null ? z : "null",
                cx != null ? cx : "null",
                cz != null ? cz : "null");
    }

    /**
     * Publish movement update to Redis for broadcasting to all pods.
     */
    private void publishToRedis(PlayerSession session, JsonNode originalData, Integer cx, Integer cz) {
        try {
            // Build enriched message with session info and chunk coordinates
            ObjectNode enriched = objectMapper.createObjectNode();
            enriched.put("sessionId", session.getSessionId());
            enriched.put("userId", session.getPlayer().user().getUserId());
            enriched.put("title", session.getTitle());

            // Copy original data
            if (originalData.has("p")) {
                enriched.set("p", originalData.get("p"));
            }
            if (originalData.has("r")) {
                enriched.set("r", originalData.get("r"));
            }

            // Add chunk coordinates if available
            if (cx != null) enriched.put("cx", cx);
            if (cz != null) enriched.put("cz", cz);

            String json = objectMapper.writeValueAsString(enriched);
            redisMessaging.publish(session.getWorldId().getId(), "u.m", json);

            log.trace("Published movement update to Redis: worldId={}, sessionId={}",
                    session.getWorldId(), session.getSessionId());

        } catch (Exception e) {
            log.error("Failed to publish movement update to Redis", e);
        }
    }
}
