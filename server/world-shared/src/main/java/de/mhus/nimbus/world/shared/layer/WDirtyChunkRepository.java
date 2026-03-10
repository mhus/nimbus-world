package de.mhus.nimbus.world.shared.layer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for WDirtyChunk entities.
 */
@Repository
public interface WDirtyChunkRepository extends MongoRepository<WDirtyChunk, String> {

    /**
     * Find specific dirty chunk.
     */
    Optional<WDirtyChunk> findByWorldIdAndChunkKey(String worldId, String chunkKey);

    /**
     * Find all dirty chunks for a world, ordered by timestamp (oldest first).
     */
    List<WDirtyChunk> findByWorldIdOrderByTimestampAsc(String worldId);

    /**
     * Check if chunk is dirty.
     */
    boolean existsByWorldIdAndChunkKey(String worldId, String chunkKey);

    /**
     * Delete specific dirty chunk.
     */
    void deleteByWorldIdAndChunkKey(String worldId, String chunkKey);

    /**
     * Count dirty chunks for a world (monitoring).
     */
    long countByWorldId(String worldId);

    /**
     * Find all distinct world IDs that have dirty chunks.
     */
    @org.springframework.data.mongodb.repository.Aggregation(pipeline = {
        "{ '$group': { '_id': '$worldId' } }",
        "{ '$project': { 'worldId': '$_id', '_id': 0 } }"
    })
    List<String> findDistinctWorldIds();
}
