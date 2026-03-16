package de.mhus.nimbus.world.shared.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.WDirtyChunk;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerOverlayService;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.redis.WorldRedisLockService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for regenerating chunks from layers.
 * Processes dirty chunks and publishes updates via Redis.
 *
 * Epoch-aware rendering:
 * - Loads all enabled layers affecting a chunk
 * - For each defined epoch, determines active layers (epoch IN layer.epoches)
 * - Groups epochs with identical layer combinations
 * - Renders one WChunk per group with the grouped epoches array
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkUpdateService {

    private final WDirtyChunkService dirtyChunkService;
    private final WLayerOverlayService overlayService;
    private final WLayerService layerService;
    private final WChunkService chunkService;
    private final WWorldService worldService;
    private final WorldRedisMessagingService redisMessaging;
    private final WorldRedisLockService lockService;
    private final ObjectMapper objectMapper;

    @Value("${world.control.chunk-update-batch-size:10}")
    private int batchSize;

    /**
     * Regenerate a single dirty chunk with epoch-aware rendering.
     *
     * Algorithm:
     * 1. Load WWorld to get epoch definitions
     * 2. Load ALL enabled layers affecting this chunk (all epochs)
     * 3. For each epoch, determine which layers are active (epoch IN layer.epoches)
     * 4. Group epochs with identical active layer sets
     * 5. Delete all old WChunks for this chunkKey
     * 6. For each group: generate chunk from those layers, save with epoches array
     *
     * If no epochs are defined in WWorld, falls back to single-chunk rendering (backward compatible).
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key
     * @return true if successfully regenerated
     */
    @Transactional
    public boolean regenerateChunk(String worldId, String chunkKey) {
        try {
            log.debug("Regenerating chunk: world={} chunk={}", worldId, chunkKey);

            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );

            // Load world for epoch definitions
            WWorld world = worldService.getByWorldId(wid.toBaseWorldId().getId()).orElse(null);
            List<WEpochMeta> epochMetas = (world != null && world.getEpoches() != null)
                    ? world.getEpoches() : List.of();

            if (epochMetas.isEmpty()) {
                log.warn("No epochs defined for world={}, deleting chunk={}", worldId, chunkKey);
                chunkService.deleteAllChunkVersions(wid, chunkKey);
                publishChunkUpdate(worldId, chunkKey, null, null);
                return true;
            }

            return regenerateChunkWithEpoches(worldId, chunkKey, wid, epochMetas);

        } catch (Exception e) {
            log.error("Failed to regenerate chunk: world={} chunk={}", worldId, chunkKey, e);
            return false;
        }
    }

    /**
     * Epoch-aware chunk rendering.
     * Groups epochs by their active layer combination and renders one WChunk per group.
     */
    private boolean regenerateChunkWithEpoches(String worldId, String chunkKey, WorldId wid,
                                                List<WEpochMeta> epochMetas) {
        // 1. Load ALL enabled layers affecting this chunk (no epoch filter)
        List<WLayer> allLayers = layerService.getLayersAffectingChunk(worldId, chunkKey);

        // 2. For each epoch, determine active layers
        // Key: sorted layer IDs string, Value: list of epoch numbers
        Map<String, List<Integer>> layerGroupToEpoches = new LinkedHashMap<>();
        Map<String, List<WLayer>> layerGroupToLayers = new LinkedHashMap<>();

        for (WEpochMeta epochMeta : epochMetas) {
            int epoch = epochMeta.getEpoch();

            // Filter layers active in this epoch
            List<WLayer> activeLayers = allLayers.stream()
                    .filter(layer -> isLayerActiveInEpoch(layer, epoch))
                    .collect(Collectors.toList());

            // Create a key from the sorted layer IDs to identify identical combinations
            String groupKey = activeLayers.stream()
                    .map(WLayer::getLayerDataId)
                    .sorted()
                    .collect(Collectors.joining(","));

            layerGroupToEpoches.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(epoch);
            layerGroupToLayers.putIfAbsent(groupKey, activeLayers);
        }

        // 3. Delete all old WChunks for this chunkKey
        chunkService.deleteAllChunkVersions(wid, chunkKey);

        // 4. For each group: render and save
        int totalGroups = layerGroupToEpoches.size();
        int renderedGroups = 0;
        List<Integer> allRenderedEpoches = new ArrayList<>();

        for (var entry : layerGroupToEpoches.entrySet()) {
            String groupKey = entry.getKey();
            List<Integer> epoches = entry.getValue();
            List<WLayer> layers = layerGroupToLayers.get(groupKey);

            if (layers.isEmpty()) {
                log.debug("No layers for epoch group {}, skipping chunk: world={} chunk={}",
                        epoches, worldId, chunkKey);
                continue;
            }

            // Generate chunk from specific layer subset with epoch for hex grid filtering
            Integer representativeEpoch = epoches.isEmpty() ? null : epoches.getFirst();
            Optional<ChunkData> chunkDataOpt = overlayService.generateChunk(worldId, chunkKey, layers, representativeEpoch);

            if (chunkDataOpt.isEmpty()) {
                log.debug("Empty chunk for epoch group {}: world={} chunk={}",
                        epoches, worldId, chunkKey);
                continue;
            }

            // Save with epoches assignment
            chunkService.saveChunkWithEpoches(wid, chunkKey, chunkDataOpt.get(), epoches);
            allRenderedEpoches.addAll(epoches);
            renderedGroups++;

            log.debug("Rendered chunk for epochs {}: world={} chunk={} layers={} blocks={}",
                    epoches, worldId, chunkKey, layers.size(),
                    chunkDataOpt.get().getBlocks() != null ? chunkDataOpt.get().getBlocks().size() : 0);
        }

        // Publish update event with affected epoches so listeners can filter by session epoch
        publishChunkUpdate(worldId, chunkKey, null, allRenderedEpoches);

        log.info("Regenerated chunk with epochs: world={} chunk={} groups={}/{} epochs={}",
                worldId, chunkKey, renderedGroups, totalGroups, epochMetas.size());

        return true;
    }

    /**
     * Check if a layer is active in a given epoch.
     * A layer with an empty epoches list is NOT active in any epoch.
     */
    private boolean isLayerActiveInEpoch(WLayer layer, int epoch) {
        if (layer.getEpoches() == null || layer.getEpoches().isEmpty()) {
            return false;
        }
        return layer.getEpoches().contains(epoch);
    }

    /**
     * Process dirty chunks for all worlds.
     *
     * @param maxChunks Maximum chunks to process per world
     * @return Total number of chunks successfully regenerated across all worlds
     */
    @Transactional
    public int processDirtyChunks(int maxChunks) {
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
        String lockToken = lockService.acquireLock(worldId);
        if (lockToken == null) {
            log.trace("Chunk update already in progress for world: {}", worldId);
            return 0;
        }

        try {
            List<WDirtyChunk> dirtyChunks = dirtyChunkService.getDirtyChunks(worldId, maxChunks);

            if (dirtyChunks.isEmpty()) {
                log.trace("No dirty chunks to process for world: {}", worldId);
                return 0;
            }

            log.debug("Processing {} dirty chunks for world: {}", dirtyChunks.size(), worldId);

            int successCount = 0;
            for (WDirtyChunk dirtyChunk : dirtyChunks) {
                lockService.refreshLock(worldId, lockToken, Duration.ofMinutes(1));

                if (regenerateChunk(worldId, dirtyChunk.getChunkKey())) {
                    dirtyChunkService.clearDirtyChunk(worldId, dirtyChunk.getChunkKey());
                    successCount++;
                } else {
                    dirtyChunkService.markChunkDirty(worldId, dirtyChunk.getChunkKey(),
                            "regeneration_failed_retry");
                }
            }

            log.info("Processed dirty chunks: world={} successful={}/{}",
                    worldId, successCount, dirtyChunks.size());

            return successCount;

        } finally {
            lockService.releaseLock(worldId, lockToken);
        }
    }

    /**
     * Update a chunk asynchronously if no lock is held, otherwise mark as dirty.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key
     * @param reason   Reason for update
     */
    public void updateChunkAsync(String worldId, String chunkKey, String reason) {
        if (lockService.isLocked(worldId)) {
            dirtyChunkService.markChunkDirty(worldId, chunkKey, reason);
            log.debug("Chunk update lock held, marked as dirty: world={} chunk={} reason={}",
                    worldId, chunkKey, reason);
        } else {
            String lockToken = lockService.acquireLock(worldId, Duration.ofSeconds(30));
            if (lockToken != null) {
                try {
                    if (regenerateChunk(worldId, chunkKey)) {
                        log.debug("Chunk updated immediately: world={} chunk={}", worldId, chunkKey);
                    } else {
                        dirtyChunkService.markChunkDirty(worldId, chunkKey, reason + "_failed");
                        log.warn("Immediate chunk update failed, marked as dirty: world={} chunk={}",
                                worldId, chunkKey);
                    }
                } finally {
                    lockService.releaseLock(worldId, lockToken);
                }
            } else {
                dirtyChunkService.markChunkDirty(worldId, chunkKey, reason);
                log.debug("Could not acquire lock, marked as dirty: world={} chunk={}", worldId, chunkKey);
            }
        }
    }

    /**
     * Publish chunk update event to Redis.
     *
     * @param worldId   World identifier
     * @param chunkKey  Chunk key (format "cx:cz")
     * @param chunkData Chunk data (null if deleted or epoch-aware reload)
     * @param epoches   Epoches affected by this update (null = all epoches, i.e. legacy/no filtering)
     */
    private void publishChunkUpdate(String worldId, String chunkKey, ChunkData chunkData, List<Integer> epoches) {
        try {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("chunkKey", chunkKey);

            if (chunkData != null) {
                message.put("cx", chunkData.getCx());
                message.put("cz", chunkData.getCz());
                message.put("blockCount", chunkData.getBlocks() != null ? chunkData.getBlocks().size() : 0);
            } else {
                // Parse cx/cz from chunkKey for epoch-aware updates without ChunkData
                String[] parts = chunkKey.split(":");
                message.put("cx", Integer.parseInt(parts[0]));
                message.put("cz", Integer.parseInt(parts[1]));
            }

            if (epoches != null) {
                var epochArray = message.putArray("epoches");
                epoches.forEach(epochArray::add);
            }

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, "c.update", json);

            log.trace("Published chunk update event: world={} chunk={} epoches={}", worldId, chunkKey, epoches);

        } catch (Exception e) {
            log.error("Failed to publish chunk update event: world={} chunk={}",
                    worldId, chunkKey, e);
        }
    }
}
