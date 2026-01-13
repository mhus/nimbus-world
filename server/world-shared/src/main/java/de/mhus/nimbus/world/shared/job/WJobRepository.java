package de.mhus.nimbus.world.shared.job;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB Repository for WJob entities.
 */
@Repository
public interface WJobRepository extends MongoRepository<WJob, String> {

    /**
     * Find all jobs for a world.
     */
    List<WJob> findByWorldId(String worldId);

    /**
     * Find jobs by status (for processing).
     * Orders by priority (DESC) then createdAt (ASC).
     */
    List<WJob> findByStatusAndEnabledOrderByPriorityDescCreatedAtAsc(
            String status, boolean enabled);

    /**
     * Find jobs by world and status.
     */
    List<WJob> findByWorldIdAndStatus(String worldId, String status);

    /**
     * Find jobs by world, executor and status.
     */
    List<WJob> findByWorldIdAndExecutorAndStatus(
            String worldId, String executor, String status);

    /**
     * Find completed or failed jobs older than cutoff time (for cleanup).
     */
    List<WJob> findByStatusInAndCompletedAtBefore(
            List<String> statuses, Instant cutoffTime);

    /**
     * Count jobs by world and status.
     */
    long countByWorldIdAndStatus(String worldId, String status);

    /**
     * Count pending jobs by executor.
     */
    long countByExecutorAndStatus(String executor, String status);

    /**
     * Check if job exists.
     */
    boolean existsByWorldIdAndId(String worldId, String id);
}
