package de.mhus.nimbus.world.life.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import de.mhus.nimbus.world.life.service.MultiWorldChunkService;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Listens for periodic full chunk list updates from world-player pods.
 * Channel: world:{worldId}:c.full
 *
 * Dynamically subscribes to all enabled worlds discovered from MongoDB.
 *
 * Message format:
 * {
 *   "podId": "world-player-xyz",
 *   "timestamp": 1234567890,
 *   "chunks": [{"cx": 6, "cz": -13}, ...]
 * }
 *
 * This replaces the old request/response mechanism with a push-based approach.
 * world-player pods automatically publish their registered chunks every minute.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkListFullUpdateListener {

    private final WorldRedisMessagingService redisMessaging;
    private final MultiWorldChunkService multiWorldChunkService;
    private final WorldDiscoveryService worldDiscoveryService;
    private final ObjectMapper objectMapper;

    private final Set<WorldId> subscribedWorlds = new HashSet<>();

    @PostConstruct
    public void initialize() {
        updateSubscriptions();
    }

    /**
     * Periodically check for new worlds and update subscriptions.
     * Runs every minute.
     */
    @Scheduled(fixedDelay = 60000)
    public void updateSubscriptions() {
        Set<WorldId> knownWorlds = worldDiscoveryService.getKnownWorldIds();

        // Subscribe to new worlds
        for (WorldId worldId : knownWorlds) {
            if (!subscribedWorlds.contains(worldId)) {
                redisMessaging.subscribe(worldId.getId(), "c.full", (topic, message) -> handleFullUpdate(worldId, message));
                subscribedWorlds.add(worldId);
                log.info("Subscribed to chunk list full updates for world: {}", worldId);
            }
        }

        // Unsubscribe from removed worlds
        Set<WorldId> toRemove = new HashSet<>(subscribedWorlds);
        toRemove.removeAll(knownWorlds);
        for (WorldId worldId : toRemove) {
            subscribedWorlds.remove(worldId);
            log.info("Unsubscribed from chunk list full updates for world: {}", worldId);
        }
    }

    /**
     * Handle full chunk list update from a world-player pod.
     *
     * @param worldId World ID
     * @param message JSON message
     */
    private void handleFullUpdate(WorldId worldId, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);

            String podId = data.has("podId") ? data.get("podId").asText() : "unknown";
            long timestamp = data.has("timestamp") ? data.get("timestamp").asLong() : 0;
            JsonNode chunksNode = data.get("chunks");

            if (chunksNode == null || !chunksNode.isArray()) {
                log.warn("Invalid full chunk update from pod {} for world {}: missing chunks array", podId, worldId);
                return;
            }

            // Parse chunk coordinates
            List<ChunkCoordinate> chunks = new ArrayList<>();
            for (JsonNode chunkNode : chunksNode) {
                int cx = chunkNode.has("cx") ? chunkNode.get("cx").asInt() : 0;
                int cz = chunkNode.has("cz") ? chunkNode.get("cz").asInt() : 0;
                chunks.add(new ChunkCoordinate(cx, cz));
            }

            // Add chunks to world's chunk service (additive operation)
            multiWorldChunkService.addChunks(worldId, chunks);

            log.trace("World {}: Received full chunk update: podId={}, chunks={}, timestamp={}",
                    worldId, podId, chunks.size(), timestamp);

        } catch (Exception e) {
            log.error("Failed to handle full chunk update for world {}: {}", worldId, message, e);
        }
    }
}
