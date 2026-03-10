package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WHexGrid entities.
 * Provides standard CRUD operations and custom queries for hex grid management.
 */
@Repository
public interface WHexGridRepository extends MongoRepository<WHexGrid, String> {

    /**
     * Finds all hex grids at the given world and position (may be multiple with different epoches).
     */
    List<WHexGrid> findAllByWorldIdAndPosition(String worldId, String position);

    /**
     * Finds a hex grid by world ID, position, and epoch.
     */
    Optional<WHexGrid> findByWorldIdAndPositionAndEpochesContaining(String worldId, String position, int epoch);

    /**
     * Finds all hex grids in a world.
     */
    List<WHexGrid> findByWorldId(String worldId);

    /**
     * Finds hex grids in a world filtered by enabled status.
     */
    List<WHexGrid> findByWorldIdAndEnabled(String worldId, boolean enabled);

    /**
     * Finds all hex grids in a world that are active in the given epoch.
     */
    List<WHexGrid> findByWorldIdAndEpochesContaining(String worldId, int epoch);

    /**
     * Finds enabled hex grids in a world that are active in the given epoch.
     */
    List<WHexGrid> findByWorldIdAndEnabledAndEpochesContaining(String worldId, boolean enabled, int epoch);
}
