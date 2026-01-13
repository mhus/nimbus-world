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

import java.util.HashSet;
import java.util.Set;

/**
 * Redis listener for effect trigger events.
 * Receives effect trigger events from Redis and distributes to relevant sessions.
 *
 * Distribution: Broadcasts to all chunks listed in the "chunks" array.
 *
 * Redis message format:
 * {
 *   "sessionId": "abc123",
 *   "userId": "user123",
 *   "title": "Player",
 *   "entityId": "@player_1234",
 *   "effectId": "effect_123",
 *   "chunks": [{"x":1,"z":4}, {"x":2,"z":4}],
 *   "effect": { ... }
 * }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptEffectTriggerBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final BroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribeToWorlds() {
        // Subscribe to ALL worlds using pattern: world:*:e.t
        redisMessaging.subscribeToAllWorlds("s.t", this::handleEffectTrigger);
        log.info("Subscribed to effect trigger events for all worlds (pattern: world:*:s.t)");
    }

    private void handleEffectTrigger(String topic, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);
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
                log.warn("Effect trigger without sessionId, ignoring");
                return;
            }

            // Build client message (without internal metadata)
            ObjectNode clientData = objectMapper.createObjectNode();
            if (data.has("entityId")) clientData.put("entityId", data.get("entityId").asText());
            if (data.has("effectId")) clientData.put("effectId", data.get("effectId").asText());
            if (data.has("chunks")) clientData.set("chunks", data.get("chunks"));
            if (data.has("effect")) clientData.set("effect", data.get("effect"));

            // Broadcast to all affected chunks (with deduplication across chunks)
            if (chunks != null && chunks.size() > 0) {
                int totalSent = broadcastService.broadcastToWorldMultiChunk(
                        worldId, "s.t", clientData, originatingSessionId, chunks);

                log.trace("Distributed effect trigger to {} unique sessions across {} chunks",
                        totalSent, chunks.size());
            } else {
                // No chunks specified, broadcast to entire world
                int sent = broadcastService.broadcastToWorld(worldId, "s.t", clientData, originatingSessionId, null, null);
                log.trace("Distributed effect trigger to {} sessions (world-wide)", sent);
            }

        } catch (Exception e) {
            log.error("Failed to handle effect trigger from Redis: {}", message, e);
        }
    }

}
