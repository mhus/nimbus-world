package de.mhus.nimbus.world.player.ws;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.generated.types.Waypoint;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.ENTITY_POSES;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.world.player.config.PathwayBroadcastSettings;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionClosedConsumer;
import de.mhus.nimbus.world.player.session.SessionPingConsumer;
import de.mhus.nimbus.world.shared.redis.PathwayBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Service for generating and broadcasting entity pathways to Redis.
 *
 * Responsibilities:
 * 1. Scheduled task (100ms): Generate and broadcast pathways
 * 2. Redis caching: Store pathways for new sessions joining
 * 3. SessionPingConsumer: Refresh cache on ping (prevent timeout)
 * 4. SessionClosedConsumer: Clean up cache on disconnect
 *
 * Redis Cache:
 * - Key: "pathway:{worldId}:{sessionId}"
 * - Value: EntityPathway JSON
 * - TTL: Based on ping interval + buffer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PathwayBroadcastService implements SessionPingConsumer, SessionClosedConsumer {

    private final SessionManager sessionManager;
    private final WorldRedisMessagingService redisMessaging;
    private final EngineMapper engineMapper;
    private final PathwayBroadcastSettings properties;
    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_KEY_PREFIX = "pathway:";

    /**
     * Scheduled task: Generate and broadcast entity pathways every 100ms.
     *
     * Spring @Scheduled with fixedRateString from properties.
     */
    @Scheduled(fixedRateString = "${world.player.pathway-broadcast-interval-ms:100}")
    public void broadcastPathways() {
        try {
            // Group pathway containers by worldId
            Map<String, List<PathwayBroadcastMessage.PathwayContainer>> containersByWorld = new HashMap<>();

            // Iterate all active sessions
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                if (!session.isAuthenticated()) continue;

                // Skip if position hasn't been updated recently
                if (session.isUpdateStale(properties.getEntityUpdateTimeoutMs())) {
                    continue;
                }

                // Skip if position hasn't changed
                if (!session.isPositionChanged()) {
                    continue;
                }

                // Generate pathway for this session
                EntityPathway pathway = generatePathway(session);
                if (pathway != null) {
                    String worldId = session.getWorldId().getId();

                    // Create container with session metadata
                    PathwayBroadcastMessage.PathwayContainer container = PathwayBroadcastMessage.PathwayContainer.builder()
                        .pathway(pathway)
                        .sessionId(session.getSessionId())
                        .worldId(worldId)
                        .build();

                    containersByWorld
                        .computeIfAbsent(worldId, k -> new ArrayList<>())
                        .add(container);

                    // Cache pathway in Redis for new sessions joining later
                    cachePathway(worldId, session.getSessionId(), pathway, session.getPingInterval());
                }
            }

            // Publish pathways to Redis (grouped by world)
            for (Map.Entry<String, List<PathwayBroadcastMessage.PathwayContainer>> entry : containersByWorld.entrySet()) {
                publishPathways(entry.getKey(), entry.getValue());
            }

            if (!containersByWorld.isEmpty()) {
                log.trace("Broadcasted {} pathways across {} worlds",
                    containersByWorld.values().stream().mapToInt(List::size).sum(),
                    containersByWorld.size());
            }

        } catch (Exception e) {
            log.error("Error in pathway broadcast task", e);
        }
    }

    /**
     * Generate EntityPathway from PlayerSession state.
     *
     * Creates a predicted pathway with:
     * - Current position as start waypoint
     * - Predicted target position (current + velocity * predictionTime)
     */
    private EntityPathway generatePathway(PlayerSession session) {
        try {
            Vector3 position = session.getLastPosition();
            Rotation rotation = session.getLastRotation();
            Vector3 velocity = session.getLastVelocity();
            ENTITY_POSES pose = session.getLastPose();

            if (position == null) return null;

            long now = System.currentTimeMillis();
            long predictionMs = properties.getPathwayPredictionTimeMs();

            // Calculate predicted target position
            Vector3 targetPosition = position;
            if (velocity != null &&
                (Math.abs(velocity.getX()) > 0.001 ||
                 Math.abs(velocity.getY()) > 0.001 ||
                 Math.abs(velocity.getZ()) > 0.001)) {

                double predictionSec = predictionMs / 1000.0;
                targetPosition = Vector3.builder()
                    .x(position.getX() + velocity.getX() * predictionSec)
                    .y(position.getY() + velocity.getY() * predictionSec)
                    .z(position.getZ() + velocity.getZ() * predictionSec)
                    .build();
            }

            // Create waypoints: start (now) → target (now + 100ms)
            List<Waypoint> waypoints = new ArrayList<>();

            // Start waypoint (current position)
            waypoints.add(Waypoint.builder()
                .timestamp(now)
                .target(position)
                .rotation(rotation != null ? rotation : Rotation.builder().y(0.0).p(0.0).build())
                .pose(pose != null ? pose : ENTITY_POSES.IDLE)
                .build());

            // Target waypoint (predicted position)
            waypoints.add(Waypoint.builder()
                .timestamp(now + predictionMs)
                .target(targetPosition)
                .rotation(rotation != null ? rotation : Rotation.builder().y(0.0).p(0.0).build())
                .pose(pose != null ? pose : ENTITY_POSES.IDLE)
                .build());

            // Build EntityPathway
            return EntityPathway.builder()
                .entityId(session.getEntityId())  // Format: "@userId:characterId"
                .startAt(now)
                .waypoints(waypoints)
                .isLooping(false)
                .queryAt(now)
                .idlePose(pose)
                .physicsEnabled(true)
                .velocity(velocity)
                .grounded(false)  // Testing: set to true
                .build();

        } catch (Exception e) {
            log.error("Failed to generate pathway for session {}",
                session.getSessionId(), e);
            return null;
        }
    }

    /**
     * Publish pathways to Redis for broadcasting to all pods.
     *
     * Message format:
     * {
     *   "containers": [PathwayContainer, ...],
     *   "affectedChunks": [{"cx": 6, "cz": -13}, ...]
     * }
     */
    private void publishPathways(String worldId, List<PathwayBroadcastMessage.PathwayContainer> containers) {
        try {
            // Determine affected chunks from pathways
            Set<ChunkCoordinate> affectedChunks = new HashSet<>();

            for (PathwayBroadcastMessage.PathwayContainer container : containers) {
                EntityPathway pathway = container.getPathway();
                for (Waypoint wp : pathway.getWaypoints()) {
                    Vector3 pos = wp.getTarget();
                    int cx = (int) Math.floor(pos.getX() / 16);
                    int cz = (int) Math.floor(pos.getZ() / 16);
                    affectedChunks.add(new ChunkCoordinate(cx, cz));
                }
            }

            // Build Redis message
            ObjectNode message = engineMapper.createObjectNode();
            message.set("containers", engineMapper.valueToTree(containers));

            ArrayNode chunksArray = engineMapper.createArrayNode();
            for (ChunkCoordinate chunk : affectedChunks) {
                ObjectNode chunkNode = engineMapper.createObjectNode();
                chunkNode.put("cx", chunk.cx);
                chunkNode.put("cz", chunk.cz);
                chunksArray.add(chunkNode);
            }
            message.set("affectedChunks", chunksArray);

            String json = engineMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, "e.p", json);

            log.trace("Published {} pathway containers to Redis for world {} ({} chunks)",
                containers.size(), worldId, affectedChunks.size());

        } catch (Exception e) {
            log.error("Failed to publish pathways to Redis", e);
        }
    }

    /**
     * Cache pathway in Redis for new sessions joining.
     *
     * @param worldId World ID
     * @param sessionId Session ID
     * @param pathway Entity pathway to cache
     * @param pingInterval Ping interval in seconds (for TTL calculation)
     */
    private void cachePathway(String worldId, String sessionId, EntityPathway pathway, int pingInterval) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + worldId + ":" + sessionId;
            String json = engineMapper.writeValueAsString(pathway);

            // TTL = pingInterval + 20 seconds buffer
            long ttlSeconds = pingInterval + 20;
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(ttlSeconds));

            log.trace("Cached pathway for session {} in world {} (TTL={}s)", sessionId, worldId, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to cache pathway for session {}", sessionId, e);
        }
    }

    /**
     * Get cached pathways for a specific chunk.
     * Returns all cached pathways from sessions that have positions in the chunk.
     *
     * @param worldId World ID
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return List of cached pathways affecting this chunk
     */
    public List<EntityPathway> getCachedPathwaysForChunk(String worldId, int cx, int cz) {
        List<EntityPathway> pathways = new ArrayList<>();

        try {
            // Scan all pathway cache keys for this world
            String pattern = CACHE_KEY_PREFIX + worldId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys != null) {
                for (String key : keys) {
                    String json = redisTemplate.opsForValue().get(key);
                    if (json != null) {
                        EntityPathway pathway = engineMapper.readValue(json, EntityPathway.class);

                        // Check if pathway affects this chunk
                        if (pathwayAffectsChunk(pathway, cx, cz)) {
                            pathways.add(pathway);
                        }
                    }
                }
            }

            log.debug("Found {} cached pathways for chunk ({}, {}) in world {}",
                    pathways.size(), cx, cz, worldId);

        } catch (Exception e) {
            log.error("Failed to get cached pathways for chunk ({}, {})", cx, cz, e);
        }

        return pathways;
    }

    /**
     * Check if pathway affects a specific chunk.
     */
    private boolean pathwayAffectsChunk(EntityPathway pathway, int cx, int cz) {
        if (pathway.getWaypoints() == null) return false;

        for (Waypoint waypoint : pathway.getWaypoints()) {
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
     * Delete cached pathway for a session.
     *
     * @param worldId World ID
     * @param sessionId Session ID
     */
    private void deleteCachedPathway(String worldId, String sessionId) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + worldId + ":" + sessionId;
            redisTemplate.delete(cacheKey);
            log.debug("Deleted cached pathway for session {} in world {}", sessionId, worldId);
        } catch (Exception e) {
            log.error("Failed to delete cached pathway for session {}", sessionId, e);
        }
    }

    /**
     * SessionPingConsumer implementation.
     * Called on every ping to refresh cache TTL.
     */
    @Override
    public ACTION onSessionPing(PlayerSession session) {
        if (session.isAuthenticated() && session.getLastPosition() != null) {
            // Regenerate and cache pathway to refresh TTL
            EntityPathway pathway = generatePathway(session);
            if (pathway != null) {
                String worldId = session.getWorldId().getId();
                cachePathway(worldId, session.getSessionId(), pathway, session.getPingInterval());
            }
        }
        return ACTION.NONE;
    }

    /**
     * SessionClosedConsumer implementation.
     * Called when session is closed to clean up cache.
     */
    @Override
    public void onSessionClosed(PlayerSession session) {
        if (session.getWorldId() != null && session.getSessionId() != null) {
            deleteCachedPathway(session.getWorldId().getId(), session.getSessionId());
        }
    }

    /**
     * Helper class for chunk coordinate deduplication.
     */
    @Data
    @AllArgsConstructor
    private static class ChunkCoordinate {
        int cx, cz;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoordinate)) return false;
            ChunkCoordinate that = (ChunkCoordinate) o;
            return cx == that.cx && cz == that.cz;
        }

        @Override
        public int hashCode() {
            return Objects.hash(cx, cz);
        }
    }
}
