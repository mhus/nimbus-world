package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WChunkRepository extends MongoRepository<WChunk, String> {
    Optional<WChunk> findByWorldIdAndChunk(String worldId, String chunk);
    boolean existsByWorldIdAndChunk(String worldId, String chunk);
    void deleteByWorldIdAndChunk(String worldId, String chunk);
    List<WChunk> findByWorldId(String worldId);
    List<WChunk> findByWorldIdAndChunkContaining(String worldId, String chunk);
}

