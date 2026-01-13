package de.mhus.nimbus.world.shared.layer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for WLayer entities.
 */
@Repository
public interface WLayerRepository extends MongoRepository<WLayer, String> {

    /**
     * Find layer by world ID and name.
     */
    Optional<WLayer> findByWorldIdAndName(String worldId, String name);

    /**
     * Find layer by layer data ID.
     */
    Optional<WLayer> findByLayerDataId(String layerDataId);

    /**
     * Find layer by world ID and layer data ID.
     * This is the preferred method as only worldId + layerDataId is guaranteed unique.
     */
    Optional<WLayer> findByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Find all layers for a world, sorted by order ascending.
     */
    List<WLayer> findByWorldIdOrderByOrderAsc(String worldId);

    /**
     * Find enabled layers for a world, sorted by order ascending.
     * Performance-critical query for chunk regeneration.
     */
    List<WLayer> findByWorldIdAndEnabledOrderByOrderAsc(String worldId, boolean enabled);

    /**
     * Find layers affecting a specific chunk.
     * Returns layers where allChunks=true OR chunk is in affectedChunks list.
     */
    @Query("{ 'worldId': ?0, 'enabled': true, $or: [ { 'allChunks': true }, { 'affectedChunks': ?1 } ] }")
    List<WLayer> findLayersAffectingChunk(String worldId, String chunkKey);

    /**
     * Delete all layers for a world.
     */
    void deleteByWorldId(String worldId);
}
