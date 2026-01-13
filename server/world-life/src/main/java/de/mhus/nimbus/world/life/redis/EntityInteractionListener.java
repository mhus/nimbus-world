package de.mhus.nimbus.world.life.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.service.EntityInteractionService;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Listens for entity interaction events from world-player pods.
 * Channel: world:{worldId}:e.int
 *
 * Dynamically subscribes to all enabled worlds discovered from MongoDB.
 *
 * When a player interacts with an entity (click, talk, attack, etc.),
 * world-player forwards the interaction to world-life via Redis.
 *
 * world-life processes the interaction and may:
 * - Update entity behavior
 * - Generate new pathways
 * - Trigger scripts/effects
 * - Update entity state
 *
 * Message format:
 * {
 *   "entityId": "cow2",
 *   "action": "click",
 *   "timestamp": 1234567890,
 *   "params": {...},
 *   "userId": "user123",
 *   "sessionId": "session-abc",
 *   "title": "Player"
 * }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityInteractionListener {

    private final WorldRedisMessagingService redisMessaging;
    private final EntityInteractionService interactionService;
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
                redisMessaging.subscribe(worldId.getId(), "e.int", (topic, message) -> handleEntityInteraction(worldId, message));
                subscribedWorlds.add(worldId);
                log.info("Subscribed to entity interactions for world: {}", worldId);
            }
        }

        // Unsubscribe from removed worlds
        Set<WorldId> toRemove = new HashSet<>(subscribedWorlds);
        toRemove.removeAll(knownWorlds);
        for (WorldId worldId : toRemove) {
            subscribedWorlds.remove(worldId);
            log.info("Unsubscribed from entity interactions for world: {}", worldId);
        }
    }

    /**
     * Handle entity interaction from Redis.
     *
     * @param worldId World ID
     * @param message JSON message
     */
    private void handleEntityInteraction(WorldId worldId, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);

            String entityId = data.has("entityId") ? data.get("entityId").asText() : null;
            String action = data.has("action") ? data.get("action").asText() : null;
            Long timestamp = data.has("timestamp") ? data.get("timestamp").asLong() : null;
            JsonNode params = data.has("params") ? data.get("params") : null;

            // Player/session context
            String userId = data.has("userId") ? data.get("userId").asText() : null;
            String sessionId = data.has("sessionId") ? data.get("sessionId").asText() : null;
            String displayName = data.has("title") ? data.get("title").asText() : null;

            if (entityId == null || action == null) {
                log.warn("Invalid entity interaction message for world {}: missing entityId or action", worldId);
                return;
            }

            // Process interaction via service
            interactionService.handleInteraction(
                    worldId,
                    entityId,
                    action,
                    timestamp,
                    params,
                    userId,
                    sessionId,
                    displayName
            );

            log.debug("World {}: Handled entity interaction: entityId={}, action={}, user={}",
                    worldId, entityId, action, displayName);

        } catch (Exception e) {
            log.error("Failed to handle entity interaction for world {}: {}", worldId, message, e);
        }
    }
}
