package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.world.player.ws.BroadcastService;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.PathwayBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens for entity pathway updates from all world-player and world-life pods.
 * Channel pattern: world:*:e.p (subscribes to ALL worlds)
 *
 * Architecture: Each world-player pod can have sessions from any world.
 * Therefore, this listener subscribes to all worlds and filters by worldId
 * in the BroadcastService.
 *
 * Message format:
 * {
 *   "containers": [PathwayContainer, ...],
 *   "affectedChunks": [{"cx": 6, "cz": -13}, ...]
 * }
 *
 * PathwayContainer contains:
 * - pathway: EntityPathway
 * - sessionId: String (originating session)
 * - worldId: String
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PathwayBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final BroadcastService broadcastService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribeToPathwayUpdates() {
        // Subscribe to ALL worlds using pattern: world:*:e.p
        redisMessaging.subscribeToAllWorlds("e.p", this::handlePathwayUpdate);
        log.info("Subscribed to entity pathway updates for all worlds (pattern: world:*:e.p)");
    }

    /**
     * Handle pathway update from Redis (world-life or world-player pods).
     *
     * @param topic Redis topic (format: "world:{worldId}:e.p")
     * @param message JSON message
     */
    private void handlePathwayUpdate(String topic, String message) {
        try {
            // Extract worldId from topic: "world:main:e.p" -> "main"
            String worldId = extractWorldIdFromTopic(topic);
            if (worldId == null) {
                log.warn("Could not extract worldId from topic: {}", topic);
                return;
            }

            JsonNode data = objectMapper.readTree(message);

            JsonNode containersNode = data.get("containers");
            JsonNode affectedChunks = data.get("affectedChunks");

            if (containersNode == null || !containersNode.isArray()) {
                log.warn("Invalid pathway update: missing containers array");
                return;
            }

            if (affectedChunks == null || !affectedChunks.isArray()) {
                log.warn("Invalid pathway update: missing affectedChunks array");
                return;
            }

            // Parse containers
            List<PathwayBroadcastMessage.PathwayContainer> containers = new ArrayList<>();
            for (JsonNode containerNode : containersNode) {
                PathwayBroadcastMessage.PathwayContainer container = objectMapper.treeToValue(containerNode, PathwayBroadcastMessage.PathwayContainer.class);
                containers.add(container);
            }

            // Broadcast pathways per chunk
            for (JsonNode chunkNode : affectedChunks) {
                int cx = chunkNode.has("cx") ? chunkNode.get("cx").asInt() : 0;
                int cz = chunkNode.has("cz") ? chunkNode.get("cz").asInt() : 0;

                // Collect all pathways for this chunk (grouped by originating session)
                // We need to send them grouped because BroadcastService only accepts one originatingSessionId
                Map<String, List<EntityPathway>> pathwaysByOriginSession = new HashMap<>();

                for (PathwayBroadcastMessage.PathwayContainer container : containers) {
                    EntityPathway pathway = container.getPathway();

                    // Check if pathway affects this chunk
                    if (!pathwayAffectsChunk(pathway, cx, cz)) {
                        continue;
                    }

                    // Group by originating sessionId
                    String originSessionId = container.getSessionId() != null ? container.getSessionId() : "none";
                    pathwaysByOriginSession
                        .computeIfAbsent(originSessionId, k -> new ArrayList<>())
                        .add(pathway);
                }

                // Broadcast each group separately (different origin sessions)
                for (Map.Entry<String, List<EntityPathway>> entry : pathwaysByOriginSession.entrySet()) {
                    String originSessionId = entry.getKey().equals("none") ? null : entry.getKey();
                    List<EntityPathway> pathways = entry.getValue();

                    // Convert pathways array to JsonNode
                    JsonNode pathwaysArray = objectMapper.valueToTree(pathways);

                    // Broadcast to all sessions in this chunk (excluding originating session)
                    int sentCount = broadcastService.broadcastToWorld(
                            worldId,          // worldId from topic
                            "e.p",            // messageType
                            pathwaysArray,    // data (pathways ARRAY)
                            originSessionId,  // originatingSessionId - will be filtered out!
                            cx,               // chunk X
                            cz                // chunk Z
                    );

                    log.trace("Broadcasted {} pathways to {} sessions for chunk ({}, {}) [origin={}]",
                            pathways.size(), sentCount, cx, cz, originSessionId);
                }
            }

            log.trace("Handled pathway update: {} containers, {} chunks",
                    containers.size(), affectedChunks.size());

        } catch (Exception e) {
            log.error("Failed to handle pathway update from topic {}: {}", topic, message, e);
        }
    }

    /**
     * Check if pathway affects a specific chunk.
     * Pathway affects chunk if any waypoint is in that chunk.
     */
    private boolean pathwayAffectsChunk(EntityPathway pathway, int cx, int cz) {
        if (pathway.getWaypoints() == null) return false;

        for (de.mhus.nimbus.generated.types.Waypoint waypoint : pathway.getWaypoints()) {
            if (waypoint.getTarget() != null) {
                double x = waypoint.getTarget().getX();
                double z = waypoint.getTarget().getZ();
                int waypointCx = (int) Math.floor(x / 16);
                int waypointCz = (int) Math.floor(z / 16);
                if (waypointCx == cx && waypointCz == cz) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extract worldId from Redis topic.
     * Topic format: "world:{worldId}:e.p"
     *
     * Handles worldIds with special characters (including ':').
     * Extracts everything between "world:" and the last ":e.p".
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

        // Find last occurrence of ":e.p" and extract everything before it
        int lastIndex = withoutPrefix.lastIndexOf(":e.p");
        if (lastIndex > 0) {
            return withoutPrefix.substring(0, lastIndex);
        }

        return null;
    }
}
