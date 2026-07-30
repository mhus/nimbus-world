package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WHexGrid entities.
 * Provides standard CRUD operations and custom queries for hex grid management.
 */
@Repository
public interface WHexGridRepository extends MongoRepository<WHexGrid, String> {

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Finds all hex grids at the given world and position (may be multiple with different epoches).
     */
    List<WHexGrid> findAllByWorldIdAndPosition(String worldId, String position);

    // EPOCH-UNFILTERED: returns data across all epochs. Batch variant for looking
    // up several positions (e.g. hex neighbors) in a single query.
    List<WHexGrid> findAllByWorldIdAndPositionIn(String worldId, Collection<String> positions);

    /**
     * Finds a hex grid by world ID, position, and epoch.
     */
    Optional<WHexGrid> findByWorldIdAndPositionAndEpochesContaining(String worldId, String position, int epoch);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Finds all hex grids in a world.
     */
    List<WHexGrid> findByWorldId(String worldId);

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
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
