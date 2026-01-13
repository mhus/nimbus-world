package de.mhus.nimbus.world.shared.layer;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.world.shared.edit.BlockUpdateService;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing WEditCache entities.
 * Provides business logic for edit cache operations including creation, retrieval, and cleanup.
 *
 * Important: Since no lock is used on the table, duplicate entries may occur.
 * All find methods handle this by returning the first entry and deleting duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WEditCacheService {

    private final WEditCacheRepository repository;
    private final BlockUpdateService blockUpdateService;

    /**
     * Find specific cached block by world, layer, model, and coordinates.
     * Uses unique index for exact lookup.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param modelName Model name (null for GROUND layers)
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Cached block if found, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<WEditCache> findByCoordinates(String worldId, String layerDataId, String modelName, int x, int y, int z) {
        List<WEditCache> results = repository.findByWorldIdAndLayerDataIdAndModelNameAndXAndYAndZ(
                worldId, layerDataId, modelName, x, y, z);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find cached blocks by world, layer, and coordinates (all modelNames).
     * May return multiple blocks if different models exist at the same position.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return First cached block if found, empty otherwise
     */
    @Transactional
    public Optional<WEditCache> findByCoordinates(String worldId, String layerDataId, int x, int y, int z) {
        List<WEditCache> results = repository.findByWorldIdAndLayerDataIdAndXAndYAndZ(worldId, layerDataId, x, y, z);
        return cleanupDuplicates(results, "worldId={}, layerDataId={}, x={}, y={}, z={}", worldId, layerDataId, x, y, z);
    }

    /**
     * Find all cached blocks for a specific world and chunk.
     * Removes duplicates for each unique coordinate within the chunk.
     *
     * @param worldId World identifier
     * @param chunk Chunk identifier
     * @return List of cached blocks (no duplicates)
     */
    @Transactional
    public List<WEditCache> findByWorldIdAndChunk(String worldId, String chunk) {
        List<WEditCache> results = repository.findByWorldIdAndChunk(worldId, chunk);
        log.debug("Found {} cached blocks for worldId={}, chunk={}", results.size(), worldId, chunk);
        return results;
    }

    /**
     * Find all cached blocks for a specific world and layer.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return List of cached blocks
     */
    @Transactional(readOnly = true)
    public List<WEditCache> findByWorldIdAndLayerDataId(String worldId, String layerDataId) {
        return repository.findByWorldIdAndLayerDataId(worldId, layerDataId);
    }

    /**
     * Find all cached blocks for a specific world, layer and chunk.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param chunk Chunk identifier
     * @return List of cached blocks
     */
    @Transactional(readOnly = true)
    public List<WEditCache> findByWorldIdAndLayerDataIdAndChunk(String worldId, String layerDataId, String chunk) {
        return repository.findByWorldIdAndLayerDataIdAndChunk(worldId, layerDataId, chunk);
    }

    public void doSetAndSendBlock(WWorld world, String layerDataId, String modelName, Block block, int group) {

        setBlock(world, layerDataId, modelName, block, group);

        // Set source field for block update
        String source = layerDataId + ":" + (modelName == null ? "" : modelName);
        block.setSource(source);

        // Send block update to client with source parameter
        boolean sent = blockUpdateService.sendBlockUpdateWithSource(world.getWorldId(), "", block, source, null);
        if (!sent) {
            log.warn("Failed to send block update to clients: position={}", block.getPosition());
        }

    }

    public WEditCache setBlock(WWorld world, String layerDataId, String modelName, Block block, int group) {
        LayerBlock layerBlock = LayerBlock.builder()
                .block(block)
                .group(group)
                .build();
        return setBlock(world, layerDataId, modelName, layerBlock);
    }
        /**
         * Set or update a block in the edit cache.
         * Uses unique index on (worldId, layerDataId, modelName, x, y, z) to prevent duplicates.
         * Checks if entry exists and updates it, or creates a new one.
         *
         * @param world World Object
         * @param layerDataId Layer data identifier
         * @param modelName Model name (null for GROUND layers)
         * @param block Block data
         * @return Saved cache entry
         */
    @Transactional
    public WEditCache setBlock(WWorld world, String layerDataId, String modelName, LayerBlock block) {
        var x = block.getBlock().getPosition().getX();
        var y = block.getBlock().getPosition().getY();
        var z = block.getBlock().getPosition().getZ();
        var chunk = world.getChunkKey(x, z);

        // Find existing entry using all unique index fields including modelName
        List<WEditCache> existing = repository.findByWorldIdAndLayerDataIdAndModelNameAndXAndYAndZ(
                world.getWorldId(), layerDataId, modelName, x, y, z);

        WEditCache cache;
        if (existing.isEmpty()) {
            // Create new entry
            cache = WEditCache.builder()
                    .worldId(world.getWorldId())
                    .layerDataId(layerDataId)
                    .modelName(modelName)
                    .x(x)
                    .y(y)
                    .z(z)
                    .chunk(chunk)
                    .block(block)
                    .build();
            cache.touchCreate();
            log.debug("Creating new cache entry: worldId={}, layerDataId={}, modelName={}, x={}, y={}, z={}, chunk={}",
                    world.getWorldId(), layerDataId, modelName, x, y, z, chunk);
        } else {
            // Update existing entry (unique index guarantees only one result)
            cache = existing.get(0);
            cache.setBlock(block);
            cache.setChunk(chunk); // Update chunk in case it changed
            cache.touchUpdate();
            log.debug("Updating existing cache entry: id={}, worldId={}, layerDataId={}, modelName={}, x={}, y={}, z={}, chunk={}",
                    cache.getId(), world.getWorldId(), layerDataId, modelName, x, y, z, chunk);
        }

        return repository.save(cache);
    }

    /**
     * Delete a specific cached block by coordinates and modelName.
     * Uses unique index to find exact block.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param modelName Model name (null for GROUND layers)
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if block was deleted
     */
    @Transactional
    public boolean deleteBlock(String worldId, String layerDataId, String modelName, int x, int y, int z) {
        List<WEditCache> existing = repository.findByWorldIdAndLayerDataIdAndModelNameAndXAndYAndZ(
                worldId, layerDataId, modelName, x, y, z);
        if (!existing.isEmpty()) {
            repository.delete(existing.get(0)); // Unique index guarantees only one result
            log.debug("Deleted cache entry for worldId={}, layerDataId={}, modelName={}, x={}, y={}, z={}",
                    worldId, layerDataId, modelName, x, y, z);
            return true;
        }
        return false;
    }

    /**
     * Delete all cached blocks at the given coordinates (all modelNames).
     * Used when you want to delete all blocks at a position regardless of model.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if at least one block was deleted
     */
    @Transactional
    public boolean deleteBlock(String worldId, String layerDataId, int x, int y, int z) {
        List<WEditCache> existing = repository.findByWorldIdAndLayerDataIdAndXAndYAndZ(worldId, layerDataId, x, y, z);
        if (!existing.isEmpty()) {
            existing.forEach(repository::delete);
            log.debug("Deleted {} cache entries for worldId={}, layerDataId={}, x={}, y={}, z={}",
                    existing.size(), worldId, layerDataId, x, y, z);
            return true;
        }
        return false;
    }

    /**
     * Delete a specific cached block and send update to clients.
     * Uses unique index to delete exact block by modelName.
     *
     * @param world World object
     * @param layerDataId Layer data identifier
     * @param modelName Model name (null for GROUND layers)
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if block was deleted
     */
    public boolean doDeleteAndSendBlock(WWorld world, String layerDataId, String modelName, int x, int y, int z) {
        boolean deleted = deleteBlock(world.getWorldId(), layerDataId, modelName, x, y, z);
        if (deleted) {
            // Send block update to clients indicating deletion (null block)
            String source = layerDataId + ":" + (modelName == null ? "delete" : modelName + ":delete");
            boolean sent = blockUpdateService.sendBlockUpdateWithSource(world.getWorldId(), "", BlockUtil.createAirBlock(x, y, z), source, null);
            if (!sent) {
                log.warn("Failed to send block deletion update to clients: position=({}, {}, {})", x, y, z);
            }
        }
        return deleted;
    }

    /**
     * Delete all cached blocks at position and send update to clients.
     * Deletes all blocks at the position regardless of modelName.
     *
     * @param world World object
     * @param layerDataId Layer data identifier
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if at least one block was deleted
     */
    public boolean doDeleteAndSendBlock(WWorld world, String layerDataId, int x, int y, int z) {
        boolean deleted = deleteBlock(world.getWorldId(), layerDataId, x, y, z);
        if (deleted) {
            // Send block update to clients indicating deletion (null block)
            boolean sent = blockUpdateService.sendBlockUpdateWithSource(world.getWorldId(), "", BlockUtil.createAirBlock(x, y, z), layerDataId + ":delete", null);
            if (!sent) {
                log.warn("Failed to send block deletion update to clients: position=({}, {}, {})", x, y, z);
            }
        }
        return deleted;
    }

    /**
     * Delete all cached blocks for a specific world and layer.
     * Used when discarding changes or committing to layer.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return Number of deleted entries
     */
    @Transactional
    public long deleteByWorldIdAndLayerDataId(String worldId, String layerDataId) {
        long count = repository.countByWorldIdAndLayerDataId(worldId, layerDataId);
        repository.deleteByWorldIdAndLayerDataId(worldId, layerDataId);
        log.info("Deleted {} cache entries for worldId={}, layerDataId={}", count, worldId, layerDataId);
        return count;
    }

    /**
     * Count cached blocks for a specific world and layer.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return Number of cached blocks
     */
    @Transactional(readOnly = true)
    public long countByWorldIdAndLayerDataId(String worldId, String layerDataId) {
        return repository.countByWorldIdAndLayerDataId(worldId, layerDataId);
    }

    /**
     * Helper method to clean up duplicate entries.
     * Returns the first entry if exists, deletes the rest.
     */
    private Optional<WEditCache> cleanupDuplicates(List<WEditCache> results, String logPattern, Object... logArgs) {
        if (results.isEmpty()) {
            return Optional.empty();
        }

        if (results.size() > 1) {
            log.warn("Found {} duplicate cache entries for " + logPattern + ", keeping first and deleting {} duplicates",
                    results.size(), logArgs[0], logArgs[1], logArgs[2], logArgs[3], results.size() - 1);
            for (int i = 1; i < results.size(); i++) {
                repository.delete(results.get(i));
            }
        }

        return Optional.of(results.get(0));
    }

    @Transactional(readOnly = true)
    public boolean existsByWorldIdAndChunk(String id, String chunkKey) {
        return repository.existsByWorldIdAndChunk(id, chunkKey);
    }
}
