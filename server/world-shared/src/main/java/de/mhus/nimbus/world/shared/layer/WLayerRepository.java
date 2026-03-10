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

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find layer by world ID and name.
     */
    Optional<WLayer> findByWorldIdAndName(String worldId, String name);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find layer by world ID and layer data ID.
     * This is the preferred method as only worldId + layerDataId is guaranteed unique.
     */
    Optional<WLayer> findByWorldIdAndLayerDataId(String worldId, String layerDataId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find all layers for a world, sorted by order ascending.
     */
    List<WLayer> findByWorldIdOrderByOrderAsc(String worldId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find enabled layers for a world, sorted by order ascending.
     * Performance-critical query for chunk regeneration.
     */
    List<WLayer> findByWorldIdAndEnabledOrderByOrderAsc(String worldId, boolean enabled);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find layers affecting a specific chunk.
     * Returns layers where allChunks=true OR chunk is in affectedChunks list.
     */
    @Query("{ 'worldId': ?0, 'enabled': true, $or: [ { 'allChunks': true }, { 'affectedChunks': ?1 } ] }")
    List<WLayer> findLayersAffectingChunk(String worldId, String chunkKey);

    // Epoch-aware queries

    /**
     * Find all layers for a world filtered by epoch, sorted by order ascending.
     */
    List<WLayer> findByWorldIdAndEpochesContainingOrderByOrderAsc(String worldId, int epoch);

    /**
     * Find enabled layers for a world filtered by epoch, sorted by order ascending.
     */
    List<WLayer> findByWorldIdAndEnabledAndEpochesContainingOrderByOrderAsc(String worldId, boolean enabled, int epoch);

    /**
     * Find layers affecting a specific chunk filtered by epoch.
     */
    @Query("{ 'worldId': ?0, 'enabled': true, 'epoches': ?2, $or: [ { 'allChunks': true }, { 'affectedChunks': ?1 } ] }")
    List<WLayer> findLayersAffectingChunkAndEpoch(String worldId, String chunkKey, int epoch);

    /**
     * Delete all layers for a world.
     */
    void deleteByWorldId(String worldId);
}
