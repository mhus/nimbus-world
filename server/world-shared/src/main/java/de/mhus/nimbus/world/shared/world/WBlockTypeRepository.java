package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WBlockType entities.
 */
@Repository
public interface WBlockTypeRepository extends MongoRepository<WBlockType, String> {
    
    List<WBlockType> findByWorldId(String worldId);

    List<WBlockType> findByWorldIdAndEnabled(String worldId, boolean enabled);

    Optional<WBlockType> findByWorldIdAndBlockId(String worldId, String blockId);
}
