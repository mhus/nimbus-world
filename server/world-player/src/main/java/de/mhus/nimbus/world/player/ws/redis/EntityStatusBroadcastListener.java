package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.EntityStatusUpdate;
import de.mhus.nimbus.world.player.ws.BroadcastService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens for entity status updates from all world-player and world-life pods.
 * Channel pattern: world:*:e.s.u (subscribes to ALL worlds)
 *
 * Architecture: Each world-player pod can have sessions from any world.
 * Therefore, this listener subscribes to all worlds and filters by worldId
 * in the BroadcastService.
 *
 * Message format:
 * {
 *   "statusUpdates": [EntityStatusUpdate, ...],
 *   "affectedChunks": [{"cx": 6, "cz": -13}, ...],
 *   "originatingSessionId": "session123",
 *   "worldId": "main"
 * }
 *
 * EntityStatusUpdateDto contains:
 * - entityId: String (format: "@userId:characterId")
 * - status: Map<String, Object> (dynamic status fields, e.g., {health, healthMax, death, etc.})
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityStatusBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final BroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribeToEntityStatusUpdates() {
        // Subscribe to ALL worlds using pattern: world:*:e.s.u
        redisMessaging.subscribeToAllWorlds("e.s.u", this::handleEntityStatusUpdate);
        log.info("Subscribed to entity status updates for all worlds (pattern: world:*:e.s.u)");
    }

    /**
     * Handle entity status update from Redis (world-player, world-life, or other pods).
     *
     * @param topic Redis topic (format: "world:{worldId}:e.s.u")
     * @param message JSON message
     */
    private void handleEntityStatusUpdate(String topic, String message) {
        try {
            // Extract worldId from topic: "world:main:e.s.u" -> "main"
            String worldId = extractWorldIdFromTopic(topic);
            if (worldId == null) {
                log.warn("Could not extract worldId from topic: {}", topic);
                return;
            }

            JsonNode data = objectMapper.readTree(message);

            JsonNode statusUpdatesNode = data.get("statusUpdates");
            JsonNode affectedChunksNode = data.get("affectedChunks");
            String originatingSessionId = data.has("originatingSessionId") ?
                data.get("originatingSessionId").asText(null) : null;

            if (statusUpdatesNode == null || !statusUpdatesNode.isArray()) {
                log.warn("Invalid entity status update: missing statusUpdates array");
                return;
            }

            // Parse status updates
            List<EntityStatusUpdate> statusUpdates = new ArrayList<>();
            for (JsonNode updateNode : statusUpdatesNode) {
                EntityStatusUpdate update = objectMapper.treeToValue(updateNode, EntityStatusUpdate.class);
                statusUpdates.add(update);
            }

            // Convert status updates array to JsonNode
            JsonNode updatesArray = objectMapper.valueToTree(statusUpdates);

            // Broadcast to all sessions in affected chunks
            if (affectedChunksNode != null && affectedChunksNode.isArray()) {
                // Broadcast per chunk
                for (JsonNode chunkNode : affectedChunksNode) {
                    int cx = chunkNode.has("cx") ? chunkNode.get("cx").asInt() : 0;
                    int cz = chunkNode.has("cz") ? chunkNode.get("cz").asInt() : 0;

                    int sentCount = broadcastService.broadcastToWorld(
                        worldId,              // worldId from topic
                        "e.s.u",              // messageType
                        updatesArray,         // data (status updates ARRAY)
                        originatingSessionId, // originatingSessionId - will be filtered out!
                        cx,                   // chunk X
                        cz                    // chunk Z
                    );

                    log.trace("Broadcasted {} entity status updates to {} sessions for chunk ({}, {}) [origin={}]",
                        statusUpdates.size(), sentCount, cx, cz, originatingSessionId);
                }
            } else {
                // Broadcast to all sessions in world (no chunk filtering)
                int sentCount = broadcastService.broadcastToWorld(
                    worldId,              // worldId from topic
                    "e.s.u",              // messageType
                    updatesArray,         // data (status updates ARRAY)
                    originatingSessionId, // originatingSessionId - will be filtered out!
                    null,                 // no chunk filtering
                    null
                );

                log.trace("Broadcasted {} entity status updates to {} sessions in world {} [origin={}]",
                    statusUpdates.size(), sentCount, worldId, originatingSessionId);
            }

            log.debug("Handled entity status update: {} updates", statusUpdates.size());

        } catch (Exception e) {
            log.error("Failed to handle entity status update from topic {}: {}", topic, message, e);
        }
    }

    /**
     * Extract worldId from Redis topic.
     * Topic format: "world:{worldId}:e.s.u"
     *
     * Handles worldIds with special characters (including ':').
     * Extracts everything between "world:" and the last ":e.s.u".
     *
     * @param topic Redis topic
     * @return worldId or null if invalid format
     */
    private String extractWorldIdFromTopic(String topic) {
        if (topic == null || !topic.startsWith("world:")) {
            return null;
        }
        // Remove "world:" prefix
        String withoutPrefix = topic.substring(6);

        // Find last occurrence of ":e.s.u" and extract everything before it
        int lastIndex = withoutPrefix.lastIndexOf(":e.s.u");
        if (lastIndex > 0) {
            return withoutPrefix.substring(0, lastIndex);
        }

        return null;
    }
}
