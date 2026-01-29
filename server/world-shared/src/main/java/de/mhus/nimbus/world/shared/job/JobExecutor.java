package de.mhus.nimbus.world.shared.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.utils.CastUtil;

import java.util.Map;

/**
 * Interface for job executors.
 * Each executor implementation handles a specific job type.
 * Executors are discovered via Spring ApplicationContext and registered by name.
 */
public interface JobExecutor {

    String PREVIOUS_JOB_ID = "previousJobId";
    String PREVIOUS_JOB_RESULT = "previousJobResult";
    String PREVIOUS_JOB_ERROR_MESSAGE = "previousJobErrorMessage";

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
     * @return JobResult with successful status and optional result data
     * @throws JobExecutionException if execution fails
     */
    JobResult execute(WJob job) throws JobExecutionException;

    /**
     * Result of job execution.
     *
     * @param successful True if job completed successfully
     * @param resultData Optional result data (can be JSON, text, etc.)
     * @param errorMessage Error message if failed
     */
    record JobResult(
            boolean successful,
            boolean async,
            String resultData,
            String errorMessage
    ) {
        /**
         * Create a successful result without data.
         */
        public static JobResult success() {
            return new JobResult(true, false, null, null);
        }

        /**
         * Create a successful result with data.
         */
        public static JobResult success(String resultData) {
            return new JobResult(true, false, resultData, null);
        }

        /**
         * Create a successful result with data.
         */
        public static JobResult async(String resultData) {
            return new JobResult(true, true, resultData, null);
        }

        /**
         * Create a successful result with data.
         */
        public static JobResult async(Map<String, Object> resultData) {
            return new JobResult(true, true, CastUtil.mapToString(resultData), null);
        }

        public static JobResult success(Map<String, Object> resultData) {
            return new JobResult(true, false, CastUtil.mapToString(resultData), null);
        }

        /**
         * Create a failure result with error message.
         */
        public static JobResult failure(String errorMessage) {
            return new JobResult(false, false, null, errorMessage);
        }
    }

}
