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
     * Find all chests for a specific player in a world.
     */
    List<WChest> findByWorldIdAndPlayerId(String worldId, String playerId);

    /**
     * Find all chests of a specific type in a world.
     */
    List<WChest> findByWorldIdAndType(String worldId, WChest.ChestType type);

    /**
     * Find chest by worldId and name.
     */
    Optional<WChest> findByWorldIdAndName(String worldId, String name);

    /**
     * Find player chest by worldId, playerId and type.
     */
    Optional<WChest> findFirstByWorldIdAndPlayerIdAndType(String worldId, String playerId, WChest.ChestType type);

}
