package de.mhus.nimbus.world.shared.layer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB Repository for WEditCache entities.
 */
@Repository
public interface WEditCacheRepository extends MongoRepository<WEditCache, String> {

    /**
     * Find all cached blocks for a specific world and chunk.
     * Returns list because no lock on table - may contain duplicates.
     *
     * @param worldId World identifier
     * @param chunk Chunk identifier
     * @return List of cached blocks (may contain duplicates)
     */
    List<WEditCache> findByWorldIdAndChunk(String worldId, String chunk);

    /**
     * Find all cached blocks for a specific world and layer.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return List of cached blocks
     */
    List<WEditCache> findByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Find all cached blocks for a specific world, layer and chunk.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param chunk Chunk identifier
     * @return List of cached blocks (may contain duplicates)
     */
    List<WEditCache> findByWorldIdAndLayerDataIdAndChunk(String worldId, String layerDataId, String chunk);

    /**
     * Find cached block by world, layer, and coordinates.
     * With unique index, this should return at most one result.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return List of cached blocks (should be 0 or 1 with unique index)
     */
    List<WEditCache> findByWorldIdAndLayerDataIdAndXAndYAndZ(String worldId, String layerDataId, int x, int y, int z);

    /**
     * Find cached block by world, layer, model, and coordinates.
     * With unique index, this returns at most one result.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param modelName Model name (null for GROUND layers)
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Cached block if found, empty list otherwise
     */
    List<WEditCache> findByWorldIdAndLayerDataIdAndModelNameAndXAndYAndZ(String worldId, String layerDataId, String modelName, int x, int y, int z);

    /**
     * Delete all cached blocks for a specific world and layer.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     */
    void deleteByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Count cached blocks for a specific world and layer.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return Number of cached blocks
     */
    long countByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Check existence of cached blocks for a specific world and chunk.
     * @param id
     * @param chunkKey
     * @return
     */
    boolean existsByWorldIdAndChunk(String id, String chunkKey);
}
