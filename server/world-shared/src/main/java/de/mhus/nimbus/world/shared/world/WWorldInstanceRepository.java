package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WWorldInstance.
 * Provides CRUD operations and queries for World Instances.
 */
@Repository
public interface WWorldInstanceRepository extends MongoRepository<WWorldInstance, String> {

    /**
     * Find a world instance by its instanceId.
     *
     * @param instanceId The instanceId
     * @return Optional containing the instance if found
     */
    Optional<WWorldInstance> findByInstanceId(String instanceId);

    /**
     * Find all instances based on a specific world.
     *
     * @param worldId The worldId
     * @return List of instances
     */
    List<WWorldInstance> findByWorldId(String worldId);

    /**
     * Find all instances created by a specific player.
     *
     * @param creator The playerId of the creator
     * @return List of instances
     */
    List<WWorldInstance> findByCreator(String creator);

    /**
     * Find all instances where a specific player is allowed.
     * This searches in the players list.
     *
     * @param playerId The playerId
     * @return List of instances
     */
    List<WWorldInstance> findByPlayersContaining(String playerId);

    /**
     * Find all enabled instances based on a specific world.
     *
     * @param worldId The worldId
     * @param enabled The enabled flag
     * @return List of instances
     */
    List<WWorldInstance> findByWorldIdAndEnabled(String worldId, boolean enabled);

    /**
     * Find all enabled instances created by a specific player.
     *
     * @param creator The playerId of the creator
     * @param enabled The enabled flag
     * @return List of instances
     */
    List<WWorldInstance> findByCreatorAndEnabled(String creator, boolean enabled);

    /**
     * Check if an instance exists by instanceId.
     *
     * @param instanceId The instanceId
     * @return true if exists, false otherwise
     */
    boolean existsByInstanceId(String instanceId);

    /**
     * Delete an instance by instanceId.
     *
     * @param instanceId The instanceId
     */
    void deleteByInstanceId(String instanceId);

    /**
     * Count instances by worldId.
     *
     * @param worldId The worldId
     * @return Number of instances
     */
    long countByWorldId(String worldId);

    /**
     * Count instances by creator.
     *
     * @param creator The playerId of the creator
     * @return Number of instances
     */
    long countByCreator(String creator);
}
