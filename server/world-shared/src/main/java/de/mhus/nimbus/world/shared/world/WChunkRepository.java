package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WChunkRepository extends MongoRepository<WChunk, String> {
    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    Optional<WChunk> findByWorldIdAndChunk(String worldId, String chunk);
    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WChunk> findAllByWorldIdAndChunk(String worldId, String chunk);
    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    boolean existsByWorldIdAndChunk(String worldId, String chunk);
    void deleteByWorldIdAndChunk(String worldId, String chunk);
    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WChunk> findByWorldId(String worldId);
    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    List<WChunk> findByWorldIdAndChunkContaining(String worldId, String chunk);

    void deleteAllByWorldIdAndChunk(String worldId, String chunk);

    // Epoch-aware queries
    Optional<WChunk> findByWorldIdAndChunkAndEpochesContaining(String worldId, String chunk, int epoch);
    List<WChunk> findByWorldIdAndEpochesContaining(String worldId, int epoch);
}

