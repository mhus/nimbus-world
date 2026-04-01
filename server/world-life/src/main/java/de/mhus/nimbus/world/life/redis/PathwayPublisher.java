package de.mhus.nimbus.world.life.redis;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import de.mhus.nimbus.world.shared.redis.PathwayBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.redis.WorldRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Publishes entity pathways to world-player pods via Redis.
 * Channel: world:{worldId}:e.p
 *
 * Additionally maintains:
 * - Individual pathway cache per entity: key "npc-pathway:{entityId}" (TTL 30s)
 * - Chunk-entity index: key "chunk-entities:{cx:cz}" → Redis SET of entityIds (TTL 5min)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PathwayPublisher {

    private static final String NPC_PATHWAY_PREFIX = "npc-pathway:";
    private static final String CHUNK_ENTITIES_PREFIX = "chunk-entities:";
    private static final Duration NPC_PATHWAY_TTL = Duration.ofSeconds(30);
    private static final Duration CHUNK_INDEX_TTL = Duration.ofMinutes(5);

    private final WorldRedisMessagingService redisMessaging;
    private final WorldRedisService worldRedisService;
    private final EngineMapper engineMapper;

    /**
     * Publish entity pathways to Redis for distribution to clients.
     * Also caches each pathway individually and updates chunk-entity index.
     *
     * @param worldId World ID
     * @param pathways List of entity pathways
     * @param affectedChunks Chunks that contain these pathways
     */
    public void publishPathways(WorldId worldId, List<EntityPathway> pathways, Set<ChunkCoordinate> affectedChunks) {
        if (pathways == null || pathways.isEmpty()) {
            return;
        }

        try {
            // Convert to PathwayBroadcastMessage format
            List<PathwayBroadcastMessage.PathwayContainer> containers = pathways.stream()
                    .map(pathway -> PathwayBroadcastMessage.PathwayContainer.builder()
                            .pathway(pathway)
                            .sessionId(null)
                            .worldId(worldId.getId())
                            .build())
                    .collect(Collectors.toList());

            List<PathwayBroadcastMessage.ChunkCoordinate> chunks = affectedChunks.stream()
                    .map(chunk -> new PathwayBroadcastMessage.ChunkCoordinate(chunk.getCx(), chunk.getCz()))
                    .collect(Collectors.toList());

            PathwayBroadcastMessage message = PathwayBroadcastMessage.builder()
                    .containers(containers)
                    .affectedChunks(chunks)
                    .build();

            // Serialize and publish via Pub/Sub (unchanged)
            String json = engineMapper.writeValueAsString(message);
            redisMessaging.publish(worldId.getId(), "e.p", json);

            // Cache individual pathways and update chunk-entity index
            for (EntityPathway pathway : pathways) {
                cacheIndividualPathway(worldId, pathway);
                updateChunkEntityIndex(worldId, pathway.getEntityId(), affectedChunks);
            }

            log.trace("World {}: Published {} pathways to Redis, affecting {} chunks",
                    worldId, pathways.size(), affectedChunks.size());

        } catch (Exception e) {
            log.error("World {}: Failed to publish pathways to Redis: {} pathways", worldId, pathways.size(), e);
        }
    }

    /**
     * Publish a single pathway.
     */
    public void publishPathway(WorldId worldId, EntityPathway pathway, Set<ChunkCoordinate> affectedChunks) {
        publishPathways(worldId, List.of(pathway), affectedChunks);
    }

    /**
     * Clean up Redis entries for an entity that is being unloaded.
     *
     * @param worldId World ID
     * @param entityId Entity ID
     * @param affectedChunks Chunk keys the entity was in
     */
    public void cleanupEntityFromRedis(WorldId worldId, String entityId, Collection<String> affectedChunks) {
        try {
            // Delete individual pathway cache
            worldRedisService.deleteValue(worldId.getId(), NPC_PATHWAY_PREFIX + entityId);

            // Remove from chunk-entity index sets
            if (affectedChunks != null) {
                for (String chunkKey : affectedChunks) {
                    worldRedisService.removeFromSet(worldId.getId(), CHUNK_ENTITIES_PREFIX + chunkKey, entityId);
                }
            }

            log.debug("World {}: Cleaned up Redis for entity {} from {} chunks",
                    worldId, entityId, affectedChunks != null ? affectedChunks.size() : 0);
        } catch (Exception e) {
            log.error("World {}: Failed to clean up Redis for entity {}", worldId, entityId, e);
        }
    }

    /**
     * Remove only the pathway cache for an entity (e.g. on death — entity stays visible but stops moving).
     */
    public void removePathway(WorldId worldId, String entityId) {
        worldRedisService.deleteValue(worldId.getId(), NPC_PATHWAY_PREFIX + entityId);
    }

    private void cacheIndividualPathway(WorldId worldId, EntityPathway pathway) {
        try {
            String json = engineMapper.writeValueAsString(pathway);
            worldRedisService.putValue(worldId.getId(), NPC_PATHWAY_PREFIX + pathway.getEntityId(), json, NPC_PATHWAY_TTL);
        } catch (Exception e) {
            log.error("World {}: Failed to cache pathway for entity {}", worldId, pathway.getEntityId(), e);
        }
    }

    private void updateChunkEntityIndex(WorldId worldId, String entityId, Set<ChunkCoordinate> affectedChunks) {
        for (ChunkCoordinate chunk : affectedChunks) {
            String chunkKey = CHUNK_ENTITIES_PREFIX + chunk.toKey();
            worldRedisService.addToSet(worldId.getId(), chunkKey, entityId);
            worldRedisService.setExpire(worldId.getId(), chunkKey, CHUNK_INDEX_TTL);
        }
    }
}
