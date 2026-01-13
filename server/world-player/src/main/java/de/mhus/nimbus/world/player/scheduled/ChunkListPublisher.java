package de.mhus.nimbus.world.player.scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Periodically publishes all registered chunks to world-life pods.
 * Runs every minute (configurable) and sends a full list of chunks
 * from all authenticated sessions on this pod.
 *
 * This replaces the old request/response mechanism with a push-based approach.
 * world-life receives these updates and maintains chunks with TTL tracking.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkListPublisher {

    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * Publish full chunk list from all authenticated sessions.
     * Runs at fixed intervals to keep world-life updated.
     */
    @Scheduled(fixedDelayString = "#{${world.player.chunk-publish-interval-ms:60000}}")
    public void publishChunkList() {
        try {
            // Get pod identifier for debugging
            String podId = System.getenv("HOSTNAME");
            if (podId == null || podId.isBlank()) {
                podId = "world-player-local";
            }

            // Collect all chunks from authenticated sessions
            Set<String> allChunkKeys = new HashSet<>();
            String worldId = null;
            int authenticatedSessionCount = 0;

            Map<String, PlayerSession> allSessions = sessionManager.getAllSessions();
            for (PlayerSession session : allSessions.values()) {
                // Only include authenticated sessions
                if (!session.isAuthenticated()) {
                    continue;
                }

                authenticatedSessionCount++;

                // Get world ID from first authenticated session
                if (worldId == null && session.getWorldId() != null) {
                    worldId = session.getWorldId().getId();
                }

                // Collect all registered chunks from this session
                allChunkKeys.addAll(session.getRegisteredChunks());
            }

            // Skip if no authenticated sessions
            if (authenticatedSessionCount == 0) {
                log.trace("No authenticated sessions, skipping chunk list publish");
                return;
            }

            // Skip if no world ID found (shouldn't happen with authenticated sessions)
            if (worldId == null || worldId.isBlank()) {
                log.warn("No world ID found in authenticated sessions, skipping chunk list publish");
                return;
            }

            // Parse chunk keys (format: "cx:cz") and create JSON array
            ArrayNode chunksArray = objectMapper.createArrayNode();
            for (String chunkKey : allChunkKeys) {
                try {
                    String[] parts = chunkKey.split(":");
                    if (parts.length == 2) {
                        int cx = Integer.parseInt(parts[0]);
                        int cz = Integer.parseInt(parts[1]);

                        ObjectNode chunkNode = objectMapper.createObjectNode();
                        chunkNode.put("cx", cx);
                        chunkNode.put("cz", cz);
                        chunksArray.add(chunkNode);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid chunk key format: {}", chunkKey);
                }
            }

            // Create message
            ObjectNode message = objectMapper.createObjectNode();
            message.put("podId", podId);
            message.put("timestamp", System.currentTimeMillis());
            message.set("chunks", chunksArray);

            // Publish to Redis
            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, "c.full", json);

            log.debug("Published chunk list: podId={}, unique chunks={}, sessions={}",
                    podId, allChunkKeys.size(), authenticatedSessionCount);

        } catch (Exception e) {
            log.error("Failed to publish chunk list", e);
        }
    }
}
