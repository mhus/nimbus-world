package de.mhus.nimbus.world.shared.job;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB Entity for async job execution across world pods.
 * Jobs are processed by JobProcessingScheduler using Redis locks for multi-pod safety.
 */
@Document(collection = "w_jobs")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_status_created_idx",
                def = "{ 'worldId': 1, 'status': 1, 'createdAt': 1 }"),
        @CompoundIndex(name = "world_executor_status_idx",
                def = "{ 'worldId': 1, 'executor': 1, 'status': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WJob implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier where this job belongs.
     */
    @Indexed
    private String worldId;

    /**
     * Executor name to use (maps to JobExecutor bean).
     * Example: "chunk-regeneration", "world-export", "layer-cleanup"
     */
    @Indexed
    private String executor;

    /**
     * Job type (for monitoring/debugging).
     * Could be same as executor or more specific.
     */
    private String type;

    /**
     * Job status: PENDING, RUNNING, COMPLETED, FAILED
     */
    @Indexed
    private String status;

    /**
     * Job parameters (executor-specific).
     */
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Result data from execution (JSON, text, or status info).
     * Populated on COMPLETED status.
     */
    private String result;

    /**
     * Error message if status is FAILED.
     */
    private String errorMessage;

    /**
     * Priority (1-10, higher = more urgent).
     * Default: 5
     */
    @Builder.Default
    private int priority = 5;

    /**
     * Maximum retry count for failed jobs.
     * 0 = no retries
     */
    @Builder.Default
    private int maxRetries = 0;

    /**
     * Current retry attempt (incremented on failure).
     */
    @Builder.Default
    private int retryCount = 0;

    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant modifiedAt;

    /**
     * Soft delete flag.
     */
    @Indexed
    @Builder.Default
    private boolean enabled = true;

    /**
     * Configuration for the next job to start on successful completion.
     */
    private NextJob onSuccess;

    /**
     * Configuration for the next job to start on failure.
     */
    private NextJob onError;

    /**
     * Initialize timestamps for new job.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        modifiedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        modifiedAt = Instant.now();
    }

    /**
     * Mark job as started.
     */
    public void markStarted() {
        startedAt = Instant.now();
        status = JobStatus.RUNNING.name();
        touchUpdate();
    }

    /**
     * Mark job as completed with optional result.
     */
    public void markCompleted(String result) {
        completedAt = Instant.now();
        status = JobStatus.COMPLETED.name();
        this.result = result;
        touchUpdate();
    }

    /**
     * Mark job as failed with error message.
     */
    public void markFailed(String errorMessage) {
        completedAt = Instant.now();
        status = JobStatus.FAILED.name();
        this.errorMessage = errorMessage;
        retryCount++;
        touchUpdate();
    }

    /**
     * Check if job can be retried.
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }
}
