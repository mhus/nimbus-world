package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WChest entities.
 */
@Repository
public interface WChestRepository extends MongoRepository<WChest, String> {

    /**
     * Find all chests for a specific world.
     */
    List<WChest> findByWorldId(String worldId);

    /**
     * Find all chests for a specific user in a world.
     */
    List<WChest> findByWorldIdAndUserId(String worldId, String userId);

    /**
     * Find all chests of a specific type in a world.
     */
    List<WChest> findByWorldIdAndType(String worldId, WChest.ChestType type);

    /**
     * Find chest by worldId and name.
     */
    Optional<WChest> findByWorldIdAndName(String worldId, String name);

}
