package de.mhus.nimbus.world.control.service;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB Repository for WWorldEditSettings entities.
 */
@Repository
public interface WWorldEditSettingsRepository extends MongoRepository<WWorldEditSettings, String> {

    /**
     * Find settings by world ID and user ID.
     * @param worldId World identifier
     * @param userId User identifier (not player ID)
     * @return Optional containing the settings if found
     */
    Optional<WWorldEditSettings> findByWorldIdAndUserId(String worldId, String userId);

    /**
     * Delete settings by world ID and user ID.
     * @param worldId World identifier
     * @param userId User identifier
     */
    void deleteByWorldIdAndUserId(String worldId, String userId);
}
