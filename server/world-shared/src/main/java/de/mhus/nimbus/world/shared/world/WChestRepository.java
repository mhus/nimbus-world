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
     * Find all chests for a specific region.
     */
    List<WChest> findByRegionId(String regionId);

    /**
     * Find all chests for a specific world.
     */
    List<WChest> findByWorldId(String worldId);

    /**
     * Find chest by regionId and name (unique constraint).
     */
    Optional<WChest> findByRegionIdAndName(String regionId, String name);

    /**
     * Find all chests for a specific user in a region.
     */
    List<WChest> findByRegionIdAndUserId(String regionId, String userId);

    /**
     * Find all chests for a specific user in a world.
     */
    List<WChest> findByWorldIdAndUserId(String worldId, String userId);

    /**
     * Find all chests of a specific type in a region.
     */
    List<WChest> findByRegionIdAndType(String regionId, WChest.ChestType type);

    /**
     * Find all chests of a specific type in a world.
     */
    List<WChest> findByWorldIdAndType(String worldId, WChest.ChestType type);

    /**
     * Find chest by worldId and name.
     */
    Optional<WChest> findByWorldIdAndName(String worldId, String name);

    /**
     * Delete chest by regionId and name.
     */
    void deleteByRegionIdAndName(String regionId, String name);
}
