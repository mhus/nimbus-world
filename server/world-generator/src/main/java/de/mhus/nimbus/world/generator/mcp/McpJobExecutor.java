package de.mhus.nimbus.world.generator.mcp;

import de.mhus.nimbus.world.shared.job.JobStatus;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.job.WJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Helper service for synchronous MCP job execution.
 * Uses builder pattern for flexible job configuration.
 *
 * Usage:
 * <pre>
 * JobExecutionResult result = mcpJobExecutor.builder()
 *     .worldId("demo-world")
 *     .layer("ground")
 *     .executor("asset-image-generator")
 *     .parameter("prompt", "stone brick texture")
 *     .timeout(180000)
 *     .build(mcpJobExecutor)
 *     .executeAndWait();
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpJobExecutor {

    private final WJobService jobService;

    /**
     * Create a new builder for job execution.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for configuring job execution.
     */
    public static class Builder {
        private String worldId;
        private String layer;
        private String executor;
        private Map<String, String> parameters = new HashMap<>();
        private long timeoutMs = 300000; // Default: 5 minutes

        public Builder worldId(String worldId) {
            this.worldId = worldId;
            return this;
        }

        public Builder layer(String layer) {
            this.layer = layer;
            return this;
        }

        public Builder executor(String executor) {
            this.executor = executor;
            return this;
        }

        public Builder parameter(String key, String value) {
            this.parameters.put(key, value);
            return this;
        }

        public Builder parameters(Map<String, String> params) {
            if (params != null) {
                this.parameters.putAll(params);
            }
            return this;
        }

        public Builder timeout(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public ExecutionContext build(McpJobExecutor executor) {
            if (worldId == null || worldId.isBlank()) {
                throw new IllegalArgumentException("worldId is required");
            }
            if (this.executor == null || this.executor.isBlank()) {
                throw new IllegalArgumentException("executor is required");
            }
            return new ExecutionContext(executor, this);
        }
    }

    /**
     * Execution context for running a configured job.
     */
    public static class ExecutionContext {
        private final McpJobExecutor jobExecutor;
        private final Builder config;

        private ExecutionContext(McpJobExecutor jobExecutor, Builder config) {
            this.jobExecutor = jobExecutor;
            this.config = config;
        }

        /**
         * Execute job synchronously and wait for completion.
         *
         * @return execution result
         * @throws McpJobException if job fails or times out
         */
        public JobExecutionResult executeAndWait() throws McpJobException {
            return jobExecutor.executeJobSync(config);
        }

        /**
         * Execute job asynchronously and return jobId immediately.
         *
         * @return job ID for status polling
         */
        public String executeAsync() {
            WJob job = jobExecutor.createJob(config);
            return job.getId();
        }
    }

    /**
     * Result record containing job execution details.
     */
    public record JobExecutionResult(
        String jobId,
        ExecutionStatus status,
        String result,
        String error,
        Long durationMs,
        Instant startedAt,
        Instant completedAt
    ) {}

    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        SUCCESS,
        FAILURE,
        TIMEOUT
    }

    /**
     * Create a job without waiting for execution.
     */
    private WJob createJob(Builder config) {
        Map<String, String> jobParams = new HashMap<>(config.parameters);

        // Add layer parameter if present
        if (config.layer != null && !config.layer.isBlank()) {
            jobParams.put("layer", config.layer);
        }

        return jobService.createJob(
            config.worldId,
            config.executor,
            config.executor,
            jobParams
        );
    }

    /**
     * Execute job synchronously with polling.
     */
    private JobExecutionResult executeJobSync(Builder config) throws McpJobException {
        // Create job
        WJob job = createJob(config);
        String jobId = job.getId();

        log.info("MCP Job created: id={} world={} executor={}", jobId, config.worldId, config.executor);

        // Poll for completion
        long startTime = System.currentTimeMillis();
        long pollInterval = 500; // 500ms

        try {
            while (true) {
                long elapsed = System.currentTimeMillis() - startTime;

                // Check timeout
                if (elapsed > config.timeoutMs) {
                    log.warn("MCP Job timeout: id={} elapsed={}ms timeout={}ms",
                        jobId, elapsed, config.timeoutMs);

                    throw new McpJobTimeoutException(
                        String.format("Job exceeded timeout of %dms (elapsed: %dms)",
                            config.timeoutMs, elapsed),
                        jobId);
                }

                // Poll job status
                Optional<WJob> jobOpt = jobService.getJob(jobId);
                if (jobOpt.isEmpty()) {
                    throw new McpJobException("Job not found: " + jobId);
                }

                WJob currentJob = jobOpt.get();
                JobStatus status = JobStatus.valueOf(currentJob.getStatus());

                // Check completion
                switch (status) {
                    case COMPLETED -> {
                        Long duration = calculateDuration(currentJob);
                        log.info("MCP Job completed: id={} duration={}ms", jobId, duration);

                        return new JobExecutionResult(
                            jobId,
                            ExecutionStatus.SUCCESS,
                            currentJob.getResult(),
                            null,
                            duration,
                            currentJob.getStartedAt(),
                            currentJob.getCompletedAt()
                        );
                    }
                    case FAILED -> {
                        Long duration = calculateDuration(currentJob);
                        log.error("MCP Job failed: id={} error={}", jobId, currentJob.getErrorMessage());

                        return new JobExecutionResult(
                            jobId,
                            ExecutionStatus.FAILURE,
                            null,
                            currentJob.getErrorMessage(),
                            duration,
                            currentJob.getStartedAt(),
                            currentJob.getCompletedAt()
                        );
                    }
                    case PENDING, RUNNING -> {
                        // Continue polling
                        Thread.sleep(pollInterval);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpJobException("Job execution interrupted", e);
        }
    }

    /**
     * Calculate duration between started and completed timestamps.
     */
    private Long calculateDuration(WJob job) {
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            return job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli();
        }
        return null;
    }
}
