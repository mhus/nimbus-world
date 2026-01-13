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
     * Finds a hex grid by world ID and position.
     *
     * @param worldId The world identifier
     * @param position The position key in format "q:r"
     * @return Optional containing the hex grid if found
     */
    Optional<WHexGrid> findByWorldIdAndPosition(String worldId, String position);

    /**
     * Finds all hex grids in a world.
     *
     * @param worldId The world identifier
     * @return List of all hex grids in the world
     */
    List<WHexGrid> findByWorldId(String worldId);

    /**
     * Finds hex grids in a world filtered by enabled status.
     *
     * @param worldId The world identifier
     * @param enabled The enabled status to filter by
     * @return List of hex grids matching the criteria
     */
    List<WHexGrid> findByWorldIdAndEnabled(String worldId, boolean enabled);

    /**
     * Checks if a hex grid exists at the given world and position.
     *
     * @param worldId The world identifier
     * @param position The position key in format "q:r"
     * @return true if a hex grid exists at this position
     */
    boolean existsByWorldIdAndPosition(String worldId, String position);
}
