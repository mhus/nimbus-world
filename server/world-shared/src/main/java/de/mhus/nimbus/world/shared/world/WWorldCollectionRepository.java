package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WWorldCollection.
 * Provides CRUD operations for World Collections.
 */
@Repository
public interface WWorldCollectionRepository extends MongoRepository<WWorldCollection, String> {

    /**
     * Find a world collection by its worldId.
     *
     * @param worldId The worldId (must start with '@')
     * @return Optional containing the collection if found
     */
    Optional<WWorldCollection> findByWorldId(String worldId);

    /**
     * Check if a world collection exists by worldId.
     *
     * @param worldId The worldId (must start with '@')
     * @return true if the collection exists, false otherwise
     */
    boolean existsByWorldId(String worldId);

    /**
     * Find all enabled world collections.
     *
     * @param enabled The enabled flag
     * @return List of world collections
     */
    List<WWorldCollection> findByEnabled(boolean enabled);

    /**
     * Delete a world collection by worldId.
     *
     * @param worldId The worldId (must start with '@')
     */
    void deleteByWorldId(String worldId);
}
