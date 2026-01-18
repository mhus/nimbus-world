package de.mhus.nimbus.world.shared.layer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for WLayerTerrain entities.
 */
@Repository
public interface WLayerTerrainRepository extends MongoRepository<WLayerTerrain, String> {

    /**
     * Find specific chunk in terrain layer.
     */
    Optional<WLayerTerrain> findByLayerDataIdAndChunkKey(String layerDataId, String chunkKey);

    /**
     * Find all chunks in a terrain layer.
     */
    List<WLayerTerrain> findByLayerDataId(String layerDataId);

    /**
     * Find all terrain chunks for a world.
     */
    List<WLayerTerrain> findByWorldId(String worldId);

    /**
     * Delete all chunks for a terrain layer.
     */
    void deleteByLayerDataId(String layerDataId);

    /**
     * Delete specific chunk in a terrain layer.
     */
    void deleteByLayerDataIdAndChunkKey(String layerDataId, String chunkKey);
}
