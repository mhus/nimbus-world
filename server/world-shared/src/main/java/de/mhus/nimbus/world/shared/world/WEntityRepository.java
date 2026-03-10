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
    Optional<WEntity> findByWorldIdAndEntityId(String worldId, String entityId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldId(String worldId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldIdAndModelId(String worldId, String modelId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldIdAndEnabled(String worldId, boolean enabled);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    boolean existsByWorldIdAndEntityId(String worldId, String entityId);

    void deleteByWorldIdAndEntityId(String worldId, String entityId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldIdAndEntityIdStartingWith(String worldId, String entityIdPrefix);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldIdAndSourceAndAffectedChunksIn(String worldId, String source, java.util.Collection<String> chunkKeys);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WEntity> findByWorldIdAndEnabledAndAffectedChunksIn(String worldId, boolean enabled, java.util.Collection<String> chunkKeys);

    // Epoch-aware queries

    Optional<WEntity> findByWorldIdAndEntityIdAndEpochesContaining(String worldId, String entityId, int epoch);

    List<WEntity> findByWorldIdAndEpochesContaining(String worldId, int epoch);

    List<WEntity> findByWorldIdAndEnabledAndEpochesContaining(String worldId, boolean enabled, int epoch);

    List<WEntity> findByWorldIdAndEnabledAndAffectedChunksInAndEpochesContaining(String worldId, boolean enabled, java.util.Collection<String> chunkKeys, int epoch);

    /**
     * Delete all entities for a world.
     * Used for instance cleanup (hard delete of all COW data).
     */
    void deleteByWorldId(String worldId);
}
