package de.mhus.nimbus.world.life.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.config.WorldLifeSettings;
import de.mhus.nimbus.world.life.model.EntityOwnership;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing entity ownership across multiple world-life pods.
 *
 * Uses Redis heartbeat-based ownership coordination:
 * - Each pod claims entities and sends periodic heartbeats (every 5s)
 * - Other pods see the heartbeats and skip simulation for those entities
 * - If heartbeats stop (pod crashed), entities become orphaned
 * - Orphaned entities can be claimed by other pods
 *
 * Channel: world:{worldId}:e.o
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityOwnershipService {

    private final WorldRedisMessagingService redisMessaging;
    private final WorldLifeSettings properties;
    private final ObjectMapper objectMapper;

    /**
     * Registry of all known entity ownerships (from all pods).
     * Maps composite key (worldId:entityId) → EntityOwnership
     *
     * Note: Entity IDs are only unique within a world, not globally.
     */
    private final Map<String, EntityOwnership> ownershipRegistry = new ConcurrentHashMap<>();

    /**
     * Set of entities owned by this pod.
     * Uses composite key format: worldId:entityId
     */
    private final Set<String> ownedEntities = ConcurrentHashMap.newKeySet();

    /**
     * Pod identifier (Kubernetes hostname or configured ID).
     */
    private String podId;

    @PostConstruct
    public void initialize() {
        // Get pod ID from environment or default
        podId = System.getenv().getOrDefault("HOSTNAME", "world-life-local");

        // Subscribe to ownership announcements for all worlds using pattern subscription
        redisMessaging.subscribeToAllWorlds("e.o", this::handleOwnershipAnnouncement);

        log.info("EntityOwnershipService initialized: podId={}", podId);
    }

    /**
     * Create composite key for entity ownership.
     * Entity IDs are only unique within a world, so we use worldId:entityId.
     *
     * @param worldId World ID
     * @param entityId Entity ID
     * @return Composite key
     */
    private String makeEntityKey(WorldId worldId, String entityId) {
        return worldId + ":" + entityId;
    }

    /**
     * Claim ownership of an entity.
     * Checks if entity is already owned by another pod (non-stale).
     * Publishes claim announcement to Redis if successful.
     *
     * @param worldId World ID
     * @param entityId Entity identifier
     * @param chunk Current chunk where entity is located
     * @return True if claim was successful, false if entity is owned by another pod
     */
    public boolean claimEntity(WorldId worldId, String entityId, String chunk) {
        long timestamp = System.currentTimeMillis();
        String entityKey = makeEntityKey(worldId, entityId);

        // Check if already owned by another pod (non-stale)
        EntityOwnership existing = ownershipRegistry.get(entityKey);
        if (existing != null &&
                !existing.getPodId().equals(podId) &&
                !existing.isStale(timestamp, properties.getOwnershipStaleThresholdMs())) {

            log.trace("World {}: Entity {} already owned by pod {}", worldId, entityId, existing.getPodId());
            return false;
        }

        // Claim entity
        EntityOwnership ownership = new EntityOwnership(entityId, worldId.getId(), podId, timestamp, timestamp, chunk);
        ownershipRegistry.put(entityKey, ownership);
        ownedEntities.add(entityKey);

        // Publish claim announcement
        publishOwnershipAnnouncement(worldId, "claim", entityId, chunk);

        log.debug("World {}: Claimed entity {} in chunk {}", worldId, entityId, chunk);
        return true;
    }

    /**
     * Release ownership of an entity.
     * Publishes release announcement to Redis.
     *
     * @param worldId World ID
     * @param entityId Entity identifier
     */
    public void releaseEntity(WorldId worldId, String entityId) {
        String entityKey = makeEntityKey(worldId, entityId);
        EntityOwnership ownership = ownershipRegistry.remove(entityKey);
        ownedEntities.remove(entityKey);

        if (ownership != null) {
            publishOwnershipAnnouncement(worldId, "release", entityId, ownership.getCurrentChunk());
            log.debug("World {}: Released entity {}", worldId, entityId);
        }
    }

    /**
     * Send heartbeats for all owned entities.
     * Scheduled task runs every 5 seconds (configurable).
     */
    @Scheduled(fixedDelayString = "#{${world.life.ownership-heartbeat-interval-ms:5000}}")
    public void sendHeartbeats() {
        if (ownedEntities.isEmpty()) {
            return;
        }

        long timestamp = System.currentTimeMillis();

        for (String entityKey : ownedEntities) {
            EntityOwnership ownership = ownershipRegistry.get(entityKey);
            if (ownership != null) {
                ownership.setLastHeartbeat(timestamp);
                publishOwnershipAnnouncement(WorldId.unchecked(ownership.getWorldId()), "claim", ownership.getEntityId(), ownership.getCurrentChunk());
            }
        }

        log.trace("Sent heartbeats for {} entities", ownedEntities.size());
    }

    /**
     * Check if entity is owned by this pod.
     *
     * @param worldId World ID
     * @param entityId Entity identifier
     * @return True if owned by this pod
     */
    public boolean isOwnedByThisPod(WorldId worldId, String entityId) {
        String entityKey = makeEntityKey(worldId, entityId);
        return ownedEntities.contains(entityKey);
    }

    /**
     * Check if entity is orphaned (no owner or stale ownership).
     *
     * @param worldId World ID
     * @param entityId Entity identifier
     * @return True if entity is orphaned
     */
    public boolean isOrphaned(WorldId worldId, String entityId) {
        String entityKey = makeEntityKey(worldId, entityId);
        EntityOwnership ownership = ownershipRegistry.get(entityKey);

        if (ownership == null) {
            return true; // No ownership record
        }

        long currentTime = System.currentTimeMillis();
        return ownership.isStale(currentTime, properties.getOwnershipStaleThresholdMs());
    }

    /**
     * Get all orphaned entities.
     *
     * @return List of entity IDs with stale ownership
     */
    public List<String> getOrphanedEntities() {
        long currentTime = System.currentTimeMillis();
        long staleThreshold = properties.getOwnershipStaleThresholdMs();

        List<String> orphans = new ArrayList<>();

        for (Map.Entry<String, EntityOwnership> entry : ownershipRegistry.entrySet()) {
            if (entry.getValue().isStale(currentTime, staleThreshold)) {
                orphans.add(entry.getKey());
            }
        }

        return orphans;
    }

    /**
     * Get number of entities owned by this pod.
     *
     * @return Count of owned entities
     */
    public int getOwnedEntityCount() {
        return ownedEntities.size();
    }

    /**
     * Handle ownership announcement from Redis.
     *
     * Message format:
     * {
     *   "action": "claim" | "release",
     *   "entityId": "entity_001",
     *   "podId": "world-life-1",
     *   "timestamp": 1234567890,
     *   "chunk": "6:-13"
     * }
     *
     * @param topic Redis topic (format: "world:{worldId}:e.o")
     * @param message JSON message
     */
    private void handleOwnershipAnnouncement(String topic, String message) {
        try {
            // Extract worldId from topic (format: "world:{worldId}:e.o")
            WorldId worldId = extractWorldIdFromTopic(topic);

            JsonNode data = objectMapper.readTree(message);

            String action = data.has("action") ? data.get("action").asText() : null;
            String entityId = data.has("entityId") ? data.get("entityId").asText() : null;
            String podIdFromMessage = data.has("podId") ? data.get("podId").asText() : null;
            long timestamp = data.has("timestamp") ? data.get("timestamp").asLong() : System.currentTimeMillis();
            String chunk = data.has("chunk") ? data.get("chunk").asText() : null;

            if (action == null || entityId == null || podIdFromMessage == null) {
                log.warn("Invalid ownership announcement: {}", message);
                return;
            }

            if ("claim".equals(action)) {
                String entityKey = makeEntityKey(worldId, entityId);
                EntityOwnership ownership = new EntityOwnership(entityId, worldId.getId(), podIdFromMessage, timestamp, timestamp, chunk);
                ownershipRegistry.put(entityKey, ownership);

                log.trace("World {}: Entity {} claimed by pod {} in chunk {}", worldId, entityId, podIdFromMessage, chunk);

            } else if ("release".equals(action)) {
                String entityKey = makeEntityKey(worldId, entityId);
                ownershipRegistry.remove(entityKey);
                log.trace("World {}: Entity {} released by pod {}", worldId, entityId, podIdFromMessage);

            } else {
                log.warn("Unknown ownership action: {}", action);
            }

        } catch (Exception e) {
            log.error("Failed to handle ownership announcement: {}", message, e);
        }
    }

    /**
     * Extract worldId from Redis topic.
     * Format: "world:{worldId}:e.o" → worldId
     *
     * @param topic Redis topic
     * @return World ID
     */
    private WorldId extractWorldIdFromTopic(String topic) {
        String[] parts = topic.split(":");
        return WorldId.unchecked(parts.length >= 2 ? parts[1] : "unknown"); // TODO throw if invalid?
    }

    /**
     * Publish ownership announcement to Redis.
     *
     * @param worldId World ID
     * @param action "claim" or "release"
     * @param entityId Entity identifier
     * @param chunk Current chunk
     */
    private void publishOwnershipAnnouncement(WorldId worldId, String action, String entityId, String chunk) {
        try {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("action", action);
            message.put("entityId", entityId);
            message.put("podId", podId);
            message.put("timestamp", System.currentTimeMillis());
            message.put("chunk", chunk);

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId.getId(), "e.o", json);

            log.trace("World {}: Published ownership announcement: action={}, entityId={}, chunk={}",
                    worldId, action, entityId, chunk);

        } catch (Exception e) {
            log.error("World {}: Failed to publish ownership announcement: entityId={}", worldId, entityId, e);
        }
    }
}
