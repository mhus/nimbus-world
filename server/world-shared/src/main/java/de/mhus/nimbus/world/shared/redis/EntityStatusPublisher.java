package de.mhus.nimbus.world.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.types.EntityStatusUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Publisher service for broadcasting entity status updates via Redis.
 *
 * This service is used by world-player, world-life, or other services to publish
 * entity status changes to all pods via Redis pub/sub.
 *
 * Channel: world:{worldId}:e.s.u
 *
 * Status fields are dynamic and not predefined - they can be any key-value pairs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityStatusPublisher {

    public static final String GONE = "gone";

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;

    /**
     * Publish entity status updates to Redis.
     *
     * @param worldId World ID
     * @param statusUpdates List of status updates to broadcast
     * @param originatingSessionId Session ID that originated this update (null for server-side)
     */
    public void publishStatusUpdates(
        String worldId,
        List<EntityStatusUpdate> statusUpdates,
        String originatingSessionId
    ) {
        if (statusUpdates == null || statusUpdates.isEmpty()) {
            log.debug("No status updates to publish for world {}", worldId);
            return;
        }

        try {
            // Build Redis message
            ObjectNode message = objectMapper.createObjectNode();
            message.set("statusUpdates", objectMapper.valueToTree(statusUpdates));
            message.set("affectedChunks", objectMapper.createArrayNode()); // Empty = broadcast to all
            if (originatingSessionId != null) {
                message.put("originatingSessionId", originatingSessionId);
            }
            message.put("worldId", worldId);

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, "e.s.u", json);

            log.debug("Published {} entity status updates to Redis for world {} [origin={}]",
                statusUpdates.size(), worldId, originatingSessionId);

        } catch (Exception e) {
            log.error("Failed to publish entity status updates to Redis for world {}", worldId, e);
        }
    }

    /**
     * Publish entity status updates to Redis with chunk filtering.
     *
     * @param worldId World ID
     * @param statusUpdates List of status updates to broadcast
     * @param affectedChunks List of affected chunks (for filtering)
     * @param originatingSessionId Session ID that originated this update (null for server-side)
     */
    public void publishStatusUpdatesWithChunks(
        String worldId,
        List<EntityStatusUpdate> statusUpdates,
        List<EntityStatusBroadcastMessage.ChunkCoordinate> affectedChunks,
        String originatingSessionId
    ) {
        if (statusUpdates == null || statusUpdates.isEmpty()) {
            log.debug("No status updates to publish for world {}", worldId);
            return;
        }

        try {
            // Build Redis message
            ObjectNode message = objectMapper.createObjectNode();
            message.set("statusUpdates", objectMapper.valueToTree(statusUpdates));

            ArrayNode chunksArray = objectMapper.createArrayNode();
            if (affectedChunks != null) {
                for (EntityStatusBroadcastMessage.ChunkCoordinate chunk : affectedChunks) {
                    ObjectNode chunkNode = objectMapper.createObjectNode();
                    chunkNode.put("cx", chunk.getCx());
                    chunkNode.put("cz", chunk.getCz());
                    chunksArray.add(chunkNode);
                }
            }
            message.set("affectedChunks", chunksArray);

            if (originatingSessionId != null) {
                message.put("originatingSessionId", originatingSessionId);
            }
            message.put("worldId", worldId);

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, "e.s.u", json);

            log.debug("Published {} entity status updates to Redis for world {} ({} chunks) [origin={}]",
                statusUpdates.size(), worldId,
                affectedChunks != null ? affectedChunks.size() : 0,
                originatingSessionId);

        } catch (Exception e) {
            log.error("Failed to publish entity status updates to Redis for world {}", worldId, e);
        }
    }

    /**
     * Publish a single entity status update.
     *
     * @param worldId World ID
     * @param entityId Entity ID
     * @param statusFields Status fields as map (dynamic key-value pairs)
     * @param originatingSessionId Originating session ID (to prevent echo)
     */
    public void publishStatusUpdate(
        String worldId,
        String entityId,
        Map<String, Object> statusFields,
        String originatingSessionId
    ) {
        if (statusFields == null || statusFields.isEmpty()) {
            log.warn("Cannot publish empty status update for entity {}", entityId);
            return;
        }

        EntityStatusUpdate dto = EntityStatusUpdate.builder()
            .entityId(entityId)
            .status(statusFields)
            .build();

        publishStatusUpdates(worldId, List.of(dto), originatingSessionId);
    }

    /**
     * Publish a single entity status update with chunk filtering.
     *
     * @param worldId World ID
     * @param entityId Entity ID
     * @param statusFields Status fields as map
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @param originatingSessionId Originating session ID (to prevent echo)
     */
    public void publishStatusUpdateToChunk(
        String worldId,
        String entityId,
        Map<String, Object> statusFields,
        int cx,
        int cz,
        String originatingSessionId
    ) {
        if (statusFields == null || statusFields.isEmpty()) {
            log.warn("Cannot publish empty status update for entity {}", entityId);
            return;
        }

        EntityStatusUpdate dto = EntityStatusUpdate.builder()
            .entityId(entityId)
            .status(statusFields)
            .build();

        List<EntityStatusBroadcastMessage.ChunkCoordinate> chunks = List.of(
            new EntityStatusBroadcastMessage.ChunkCoordinate(cx, cz)
        );

        publishStatusUpdatesWithChunks(worldId, List.of(dto), chunks, originatingSessionId);
    }
}
