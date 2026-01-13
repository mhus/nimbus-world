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
 * Listens for chunk registration updates from world-player pods.
 * Channel: world:{worldId}:c.r
 *
 * Dynamically subscribes to all enabled worlds discovered from MongoDB.
 *
 * Message format:
 * {
 *   "action": "add" | "remove",
 *   "chunks": [{"cx": 6, "cz": -13}, ...],
 *   "sessionId": "..."
 * }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkRegistrationListener {

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
                redisMessaging.subscribe(worldId.getId(), "c.r", (topic, message) -> handleChunkRegistration(worldId, message));
                subscribedWorlds.add(worldId);
                log.info("Subscribed to chunk registrations for world: {}", worldId);
            }
        }

        // Unsubscribe from removed worlds
        Set<WorldId> toRemove = new HashSet<>(subscribedWorlds);
        toRemove.removeAll(knownWorlds);
        for (WorldId worldId : toRemove) {
            subscribedWorlds.remove(worldId);
            multiWorldChunkService.removeWorld(worldId);
            log.info("Unsubscribed from chunk registrations for world: {}", worldId);
        }
    }

    /**
     * Handle chunk registration update from Redis.
     *
     * @param worldId World ID
     * @param message JSON message
     */
    private void handleChunkRegistration(WorldId worldId, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);

            String action = data.has("action") ? data.get("action").asText() : null;
            JsonNode chunksNode = data.get("chunks");

            if (action == null || chunksNode == null || !chunksNode.isArray()) {
                log.warn("Invalid chunk registration message for world {}: {}", worldId, message);
                return;
            }

            // Parse chunk coordinates
            List<ChunkCoordinate> chunks = new ArrayList<>();
            for (JsonNode chunkNode : chunksNode) {
                int cx = chunkNode.has("cx") ? chunkNode.get("cx").asInt() : 0;
                int cz = chunkNode.has("cz") ? chunkNode.get("cz").asInt() : 0;
                chunks.add(new ChunkCoordinate(cx, cz));
            }

            // Update chunk service based on action
            switch (action) {
                case "add" -> {
                    multiWorldChunkService.addChunks(worldId, chunks);
                    log.trace("World {}: Added {} chunks from registration update", worldId, chunks.size());
                }
                case "remove" -> {
                    multiWorldChunkService.removeChunks(worldId, chunks);
                    log.trace("World {}: Removed {} chunks from registration update", worldId, chunks.size());
                }
                default -> log.warn("Unknown chunk registration action for world {}: {}", worldId, action);
            }

        } catch (Exception e) {
            log.error("Failed to handle chunk registration update for world {}: {}", worldId, message, e);
        }
    }
}
