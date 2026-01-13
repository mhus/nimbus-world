package de.mhus.nimbus.world.shared.session;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for WPlayerSession entities.
 * Manages player session state persistence in MongoDB.
 */
@Repository
public interface WPlayerSessionRepository extends MongoRepository<WPlayerSession, String> {

    /**
     * Find player session by worldId and playerId.
     * This is the primary lookup method.
     * If multiple sessions exist (duplicates), returns the most recently updated one.
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId
     * @return Optional containing the session if found
     */
    Optional<WPlayerSession> findFirstByWorldIdAndPlayerIdOrderByUpdatedAtDesc(String worldId, String playerId);

    /**
     * Find all player sessions by worldId and playerId, ordered by updatedAt descending.
     * Used for duplicate cleanup.
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId
     * @return List of sessions ordered by updatedAt DESC
     */
    List<WPlayerSession> findByWorldIdAndPlayerIdOrderByUpdatedAtDesc(String worldId, String playerId);

    /**
     * Check if a session exists for worldId and playerId.
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId
     * @return true if exists, false otherwise
     */
    boolean existsByWorldIdAndPlayerId(String worldId, String playerId);

    /**
     * Delete player session by worldId and playerId.
     *
     * @param worldId The full worldId (including instance)
     * @param playerId The playerId
     */
    void deleteByWorldIdAndPlayerId(String worldId, String playerId);

    /**
     * Find all sessions for a specific world.
     * Useful for admin/debugging purposes.
     *
     * @param worldId The full worldId (including instance)
     * @return List of sessions
     */
    List<WPlayerSession> findByWorldId(String worldId);

    /**
     * Find all sessions for a specific player.
     * Useful for admin/debugging purposes.
     *
     * @param playerId The playerId
     * @return List of sessions
     */
    List<WPlayerSession> findByPlayerId(String playerId);

    /**
     * Count sessions by worldId.
     *
     * @param worldId The full worldId (including instance)
     * @return Number of sessions
     */
    long countByWorldId(String worldId);

    /**
     * Count sessions by playerId.
     *
     * @param playerId The playerId
     * @return Number of sessions
     */
    long countByPlayerId(String playerId);

    /**
     * Find sessions updated before a specific timestamp.
     * Useful for cleanup of stale sessions.
     *
     * @param cutoff The cutoff timestamp
     * @return List of sessions
     */
    List<WPlayerSession> findByUpdatedAtBefore(Instant cutoff);

    /**
     * Delete sessions updated before a specific timestamp.
     * Useful for cleanup of stale sessions.
     *
     * @param cutoff The cutoff timestamp
     */
    void deleteByUpdatedAtBefore(Instant cutoff);
}
