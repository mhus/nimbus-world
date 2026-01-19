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
     * Find all terrain chunks for a world.
     */
    List<WLayerTerrain> findByWorldId(String worldId);

    /**
     * Delete all chunks for a terrain layer.
     */
    void deleteByWorldIdAndLayerDataId(String worldId, String layerDataId);

    Optional<WLayerTerrain> findByWorldIdAndLayerDataIdAndChunkKey(String worldId, String layerDataId, String chunkKey);

    List<WLayerTerrain> findByWorldIdAndLayerDataId(String worldId, String layerDataId);
}
