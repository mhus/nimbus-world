package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.ws.BroadcastService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Redis listener for user movement updates.
 * Receives movement events from Redis and distributes to relevant sessions via BroadcastService.
 *
 * Redis message format:
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
@Service
@RequiredArgsConstructor
@Slf4j
public class MovementBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final BroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    /**
     * Subscribe to all active worlds on startup.
     * TODO: Dynamically subscribe when new worlds become active
     */
    @PostConstruct
    public void subscribeToWorlds() {
        // Subscribe to "main" world for now
        // In production, subscribe to all active worlds dynamically
        subscribeToWorld("main");
    }

    /**
     * Subscribe to movement updates for a specific world.
     */
    public void subscribeToWorld(String worldId) {
        redisMessaging.subscribe(worldId, "u.m", (topic, message) -> {
            handleMovementUpdate(worldId, message);
        });
        log.info("Subscribed to movement updates for world: {}", worldId);
    }

    /**
     * Handle incoming movement update from Redis.
     */
    private void handleMovementUpdate(String worldId, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);

            // Extract metadata
            String originatingSessionId = data.has("sessionId") ? data.get("sessionId").asText() : null;
            Integer cx = data.has("cx") ? data.get("cx").asInt() : null;
            Integer cz = data.has("cz") ? data.get("cz").asInt() : null;

            if (originatingSessionId == null) {
                log.warn("Movement update without sessionId, ignoring");
                return;
            }

            // Build client message (without internal metadata)
            ObjectNode clientData = objectMapper.createObjectNode();
            if (data.has("userId")) clientData.put("userId", data.get("userId").asText());
            if (data.has("title")) clientData.put("title", data.get("title").asText());
            if (data.has("p")) clientData.set("p", data.get("p"));
            if (data.has("r")) clientData.set("r", data.get("r"));

            // Delegate to BroadcastService for session filtering and distribution
            broadcastService.broadcastToWorld(worldId, "u.m", clientData, originatingSessionId, cx, cz);

        } catch (Exception e) {
            log.error("Failed to handle movement update from Redis: {}", message, e);
        }
    }

    /**
     * Unsubscribe from world (e.g., when shutting down).
     */
    public void unsubscribeFromWorld(String worldId) {
        redisMessaging.unsubscribe(worldId, "u.m");
        log.info("Unsubscribed from movement updates for world: {}", worldId);
    }
}
