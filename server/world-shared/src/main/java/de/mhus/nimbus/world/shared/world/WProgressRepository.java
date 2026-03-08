package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WProgress entities.
 */
@Repository
public interface WProgressRepository extends MongoRepository<WProgress, String> {

    /**
     * Find all progress entries for a specific world.
     */
    List<WProgress> findByWorldId(String worldId);

    /**
     * Find all progress entries for a player in a world.
     */
    List<WProgress> findByWorldIdAndPlayerId(String worldId, String playerId);

    /**
     * Find all progress entries for a player in a world with a specific type.
     */
    List<WProgress> findByWorldIdAndPlayerIdAndType(String worldId, String playerId, String type);

    /**
     * Find all progress entries for a player in a world for a specific quest.
     */
    List<WProgress> findByWorldIdAndPlayerIdAndQuest(String worldId, String playerId, String quest);

    /**
     * Find a specific progress entry by world, player, type, and quest.
     */
    Optional<WProgress> findByWorldIdAndPlayerIdAndTypeAndQuest(String worldId, String playerId, String type, String quest);

    /**
     * Find a progress entry by its progressId.
     */
    Optional<WProgress> findByProgressId(String progressId);

    /**
     * Delete all progress for a player in a world.
     */
    void deleteByWorldIdAndPlayerId(String worldId, String playerId);

    /**
     * Delete all progress for a world.
     * Used for instance cleanup (hard delete of all instance-specific data).
     */
    void deleteByWorldId(String worldId);
}
