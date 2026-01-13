package de.mhus.nimbus.world.shared.job;

/**
 * Interface for job executors.
 * Each executor implementation handles a specific job type.
 * Executors are discovered via Spring ApplicationContext and registered by name.
 */
public interface JobExecutor {

    /**
     * Get executor name (must be unique).
     * Example: "chunk-regeneration", "world-export", "layer-cleanup"
     *
     * @return Unique executor name
     */
    String getExecutorName();

    /**
     * Execute the job.
     *
     * @param job Job to execute
     * @return JobResult with success status and optional result data
     * @throws JobExecutionException if execution fails
     */
    JobResult execute(WJob job) throws JobExecutionException;

    /**
     * Result of job execution.
     *
     * @param success True if job completed successfully
     * @param resultData Optional result data (can be JSON, text, etc.)
     * @param errorMessage Error message if failed
     */
    record JobResult(
            boolean success,
            String resultData,
            String errorMessage
    ) {
        /**
         * Create a success result without data.
         */
        public static JobResult ofSuccess() {
            return new JobResult(true, null, null);
        }

        /**
         * Create a success result with data.
         */
        public static JobResult ofSuccess(String resultData) {
            return new JobResult(true, resultData, null);
        }

        /**
         * Create a failure result with error message.
         */
        public static JobResult ofFailure(String errorMessage) {
            return new JobResult(false, null, errorMessage);
        }
    }
}
