package de.mhus.nimbus.world.shared.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Interface for job executors.
 * Each executor implementation handles a specific job type.
 * Executors are discovered via Spring ApplicationContext and registered by name.
 */
public interface JobExecutor {

    ObjectMapper mapper = new ObjectMapper();

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
            String resultData,
            String errorMessage
    ) {
        /**
         * Create a successful result without data.
         */
        public static JobResult success() {
            return new JobResult(true, null, null);
        }

        /**
         * Create a successful result with data.
         */
        public static JobResult success(String resultData) {
            return new JobResult(true, resultData, null);
        }

        public static JobResult success(Map<String, Object> resultData) {
            return new JobResult(true, JobExecutor.mapToString(resultData), null);
        }

        /**
         * Create a failure result with error message.
         */
        public static JobResult failure(String errorMessage) {
            return new JobResult(false, null, errorMessage);
        }
    }

    static String mapToString(Map<String, Object> resultData) {
        try {
            return mapper.writeValueAsString(resultData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize resultData map to JSON", e);
        }
    }

    static Map<String, Object> stringToMap(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to Map<String, String>", e);
        }
    }

}
