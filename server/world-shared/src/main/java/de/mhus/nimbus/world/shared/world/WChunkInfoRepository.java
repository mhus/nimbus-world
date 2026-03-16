package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WChunkInfoRepository extends MongoRepository<WChunkInfo, String> {

    Optional<WChunkInfo> findByWorldIdAndChunk(String worldId, String chunk);

    List<WChunkInfo> findAllByWorldIdAndChunk(String worldId, String chunk);

    void deleteByWorldIdAndChunk(String worldId, String chunk);

    void deleteAllByWorldIdAndChunk(String worldId, String chunk);

    Optional<WChunkInfo> findByWorldIdAndChunkAndEpochesContaining(String worldId, String chunk, int epoch);

    List<WChunkInfo> findByWorldIdAndEpochesContaining(String worldId, int epoch);
}
