package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.ws.BroadcastService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Redis listener for effect parameter update events.
 * Receives effect parameter update events from Redis and distributes to relevant sessions.
 *
 * Distribution: Broadcasts to all chunks listed in the "chunks" array.
 *
 * Redis message format:
 * {
 *   "sessionId": "abc123",
 *   "userId": "user123",
 *   "title": "Player",
 *   "effectId": "effect_123",
 *   "paramName": "targetPos",
 *   "value": {"x": -1.5, "y": 66.5, "z": 1.5},
 *   "chunks": [{"cx":0,"cz":0}, {"cx":-1,"cz":0}]
 * }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptEffectUpdateBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final BroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribeToWorlds() {
        // Subscribe to ALL worlds using pattern: world:*:s.u
        redisMessaging.subscribeToAllWorlds("s.u", this::handleEffectUpdate);
        log.info("Subscribed to effect update events for all worlds (pattern: world:*:e.u)");
    }

    private void handleEffectUpdate(String topic, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);

            // Extract worldId from topic: "world:main:e.u" -> "main"
            String worldId = data.has("worldId") ? data.get("worldId").asText(null) : null;
            if (worldId == null) {
                log.warn("Could not extract worldId from topic: {}", topic);
                return;
            }

            // Extract metadata
            String originatingSessionId = data.has("sessionId") ? data.get("sessionId").asText() : null;
            ArrayNode chunks = data.has("chunks") && data.get("chunks").isArray()
                    ? (ArrayNode) data.get("chunks") : null;

            if (originatingSessionId == null) {
                log.warn("Effect update without sessionId, ignoring");
                return;
            }

            // Build client message (without internal metadata)
            ObjectNode clientData = objectMapper.createObjectNode();
            if (data.has("effectId")) clientData.put("effectId", data.get("effectId").asText());
            if (data.has("paramName")) clientData.put("paramName", data.get("paramName").asText());
            if (data.has("value")) clientData.set("value", data.get("value"));
            if (data.has("targeting")) clientData.set("targeting", data.get("targeting"));
//            if (data.has("chunks")) clientData.set("chunks", data.get("chunks"));

            // Broadcast to all affected chunks (with deduplication across chunks)
            if (chunks != null && chunks.size() > 0) {
                int totalSent = broadcastService.broadcastToWorldMultiChunk(
                        worldId, "s.u", clientData, originatingSessionId, chunks);

                log.trace("Distributed effect update to {} unique sessions across {} chunks",
                        totalSent, chunks.size());
            } else {
                // No chunks specified, broadcast to entire world
                int sent = broadcastService.broadcastToWorld(worldId, "s.u", clientData, originatingSessionId, null, null);
                log.trace("Distributed effect update to {} sessions (world-wide)", sent);
            }

        } catch (Exception e) {
            log.error("Failed to handle effect update from Redis: {}", message, e);
        }
    }

}
