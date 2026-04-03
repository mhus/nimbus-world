package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WEntity entities (instances in the world).
 */
@Repository
public interface WEntityRepository extends MongoRepository<WEntity, String> {

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    Optional<WEntity> findByWorldIdAndName(String worldId, String entityId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldId(String worldId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldIdAndModelId(String worldId, String modelId);

    List<WEntity> findByWorldIdAndEnabled(String worldId, boolean enabled);
    List<WEntity> findByWorldIdAndEnabledAndTombstone(String worldId, boolean enabled, boolean tombstone);

    boolean existsByWorldIdAndName(String worldId, String entityId);

    void deleteByWorldIdAndName(String worldId, String entityId);

    List<WEntity> findByWorldIdAndNameStartingWith(String worldId, String entityIdPrefix);

    List<WEntity> findByWorldIdAndSourceAndAffectedChunksIn(String worldId, String source, java.util.Collection<String> chunkKeys);

    List<WEntity> findByWorldIdAndEnabledAndAffectedChunksIn(String worldId, boolean enabled, java.util.Collection<String> chunkKeys);
    List<WEntity> findByWorldIdAndEnabledAndTombstoneAndAffectedChunksIn(String worldId, boolean enabled, boolean tombstone, java.util.Collection<String> chunkKeys);

    // Epoch-aware queries

    Optional<WEntity> findByWorldIdAndNameAndEpochesContaining(String worldId, String entityId, int epoch);

    List<WEntity> findByWorldIdAndEpochesContaining(String worldId, int epoch);

    List<WEntity> findByWorldIdAndEnabledAndEpochesContaining(String worldId, boolean enabled, int epoch);
    List<WEntity> findByWorldIdAndEnabledAndTombstoneAndEpochesContaining(String worldId, boolean enabled, boolean tombstone, int epoch);

    List<WEntity> findByWorldIdAndEnabledAndAffectedChunksInAndEpochesContaining(String worldId, boolean enabled, java.util.Collection<String> chunkKeys, int epoch);
    List<WEntity> findByWorldIdAndEnabledAndTombstoneAndAffectedChunksInAndEpochesContaining(String worldId, boolean enabled, boolean tombstone, java.util.Collection<String> chunkKeys, int epoch);

    /**
     * Delete all entities for a world.
     * Used for instance cleanup (hard delete of all COW data).
     */
    void deleteByWorldId(String worldId);
}
