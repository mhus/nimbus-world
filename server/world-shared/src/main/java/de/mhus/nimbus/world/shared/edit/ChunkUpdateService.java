package de.mhus.nimbus.world.shared.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.WDirtyChunk;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WLayerOverlayService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.redis.WorldRedisLockService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Service for regenerating chunks from layers.
 * Processes dirty chunks and publishes updates via Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkUpdateService {

    private final WDirtyChunkService dirtyChunkService;
    private final WLayerOverlayService overlayService;
    private final WChunkService chunkService;
    private final WorldRedisMessagingService redisMessaging;
    private final WorldRedisLockService lockService;
    private final ObjectMapper objectMapper;

    @Value("${world.control.chunk-update-batch-size:10}")
    private int batchSize;

    /**
     * Regenerate a single dirty chunk and publish update event.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key
     * @return true if successfully regenerated
     */
    @Transactional
    public boolean regenerateChunk(String worldId, String chunkKey) {
        try {
            log.debug("Regenerating chunk: world={} chunk={}", worldId, chunkKey);

            // Generate merged chunk from layers
            Optional<ChunkData> chunkDataOpt = overlayService.generateChunk(worldId, chunkKey);

            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );

            if (chunkDataOpt.isEmpty()) {
                log.info("No layers affecting chunk, deleting WChunk: world={} chunk={}", worldId, chunkKey);
                // Delete existing WChunk if no layers define it
                chunkService.delete(wid, chunkKey);
                publishChunkUpdate(worldId, chunkKey, null);
                return true;
            }

            // Save to WChunk
            ChunkData chunkData = chunkDataOpt.get();
            chunkService.saveChunk(wid, chunkKey, chunkData);

            // Publish update event to Redis
            publishChunkUpdate(worldId, chunkKey, chunkData);

            log.info("Regenerated chunk: world={} chunk={} blocks={}",
                    worldId, chunkKey, chunkData.getBlocks() != null ? chunkData.getBlocks().size() : 0);

            return true;

        } catch (Exception e) {
            log.error("Failed to regenerate chunk: world={} chunk={}", worldId, chunkKey, e);
            return false;
        }
    }

    /**
     * Process dirty chunks for all worlds.
     * Loads all worldIds that have dirty chunks and processes each.
     *
     * @param maxChunks Maximum chunks to process per world
     * @return Total number of chunks successfully regenerated across all worlds
     */
    @Transactional
    public int processDirtyChunks(int maxChunks) {
        // Get all worldIds that have dirty chunks
        List<String> worldIds = dirtyChunkService.getWorldIdsWithDirtyChunks();

        if (worldIds.isEmpty()) {
            log.trace("No dirty chunks to process in any world");
            return 0;
        }

        log.debug("Processing dirty chunks for {} worlds", worldIds.size());

        int totalProcessed = 0;
        for (String worldId : worldIds) {
            int processed = processDirtyChunks(worldId, maxChunks);
            totalProcessed += processed;
        }

        if (totalProcessed > 0) {
            log.info("Processed dirty chunks across all worlds: total={} worlds={}",
                    totalProcessed, worldIds.size());
        }

        return totalProcessed;
    }

    /**
     * Process batch of dirty chunks for a specific world (oldest first).
     * Acquires a distributed lock to prevent concurrent processing.
     *
     * @param worldId   World identifier
     * @param maxChunks Maximum chunks to process
     * @return Number of chunks successfully regenerated
     */
    @Transactional
    public int processDirtyChunks(String worldId, int maxChunks) {
        // Try to acquire lock
        String lockToken = lockService.acquireLock(worldId);
        if (lockToken == null) {
            log.trace("Chunk update already in progress for world: {}", worldId);
            return 0;
        }

        try {
            // Get oldest dirty chunks via service
            List<WDirtyChunk> dirtyChunks = dirtyChunkService.getDirtyChunks(worldId, maxChunks);

            if (dirtyChunks.isEmpty()) {
                log.trace("No dirty chunks to process for world: {}", worldId);
                return 0;
            }

            log.debug("Processing {} dirty chunks for world: {}", dirtyChunks.size(), worldId);

            int successCount = 0;
            for (WDirtyChunk dirtyChunk : dirtyChunks) {
                // Refresh lock every chunk to prevent timeout
                lockService.refreshLock(worldId, lockToken, Duration.ofMinutes(1));

                if (regenerateChunk(worldId, dirtyChunk.getChunkKey())) {
                    // Remove from dirty queue
                    dirtyChunkService.clearDirtyChunk(worldId, dirtyChunk.getChunkKey());
                    successCount++;
                } else {
                    // Retry later: mark as dirty again (updates timestamp)
                    dirtyChunkService.markChunkDirty(worldId, dirtyChunk.getChunkKey(),
                            "regeneration_failed_retry");
                }
            }

            log.info("Processed dirty chunks: world={} success={}/{}",
                    worldId, successCount, dirtyChunks.size());

            return successCount;

        } finally {
            // Always release lock
            lockService.releaseLock(worldId, lockToken);
        }
    }

    /**
     * Update a chunk asynchronously if no lock is held, otherwise mark as dirty.
     * This method should be called after saveTerrainChunk or saveModel.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key
     * @param reason   Reason for update
     */
    public void updateChunkAsync(String worldId, String chunkKey, String reason) {
        // Check if lock is held
        if (lockService.isLocked(worldId)) {
            // Lock is held - mark chunk as dirty for later processing
            dirtyChunkService.markChunkDirty(worldId, chunkKey, reason);
            log.debug("Chunk update lock held, marked as dirty: world={} chunk={} reason={}",
                    worldId, chunkKey, reason);
        } else {
            // No lock - try to update immediately
            String lockToken = lockService.acquireLock(worldId, Duration.ofSeconds(30));
            if (lockToken != null) {
                try {
                    // Update chunk immediately
                    if (regenerateChunk(worldId, chunkKey)) {
                        log.debug("Chunk updated immediately: world={} chunk={}", worldId, chunkKey);
                    } else {
                        // Failed - mark as dirty
                        dirtyChunkService.markChunkDirty(worldId, chunkKey, reason + "_failed");
                        log.warn("Immediate chunk update failed, marked as dirty: world={} chunk={}",
                                worldId, chunkKey);
                    }
                } finally {
                    lockService.releaseLock(worldId, lockToken);
                }
            } else {
                // Could not acquire lock - mark as dirty
                dirtyChunkService.markChunkDirty(worldId, chunkKey, reason);
                log.debug("Could not acquire lock, marked as dirty: world={} chunk={}", worldId, chunkKey);
            }
        }
    }

    /**
     * Publish chunk update event to Redis.
     * world-player pods will receive this and send updates to clients.
     */
    private void publishChunkUpdate(String worldId, String chunkKey, ChunkData chunkData) {
        try {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("chunkKey", chunkKey);

            if (chunkData != null) {
                message.put("cx", chunkData.getCx());
                message.put("cz", chunkData.getCz());
                message.put("blockCount", chunkData.getBlocks() != null ? chunkData.getBlocks().size() : 0);
            } else {
                message.put("deleted", true);
            }

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, "c.update", json);

            log.trace("Published chunk update event: world={} chunk={}", worldId, chunkKey);

        } catch (Exception e) {
            log.error("Failed to publish chunk update event: world={} chunk={}",
                    worldId, chunkKey, e);
        }
    }
}
