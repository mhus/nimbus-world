package de.mhus.nimbus.world.shared.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a follow-up job that will be started after a job completes.
 * The follow-up job will automatically receive the previous job's id, result, and errorMessage as parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextJob {

    /**
     * Executor name to use for the next job (maps to JobExecutor bean).
     * Example: "chunk-regeneration", "world-export", "layer-cleanup"
     */
    private String executor;

    /**
     * Job type for the next job (for monitoring/debugging).
     * Could be same as executor or more specific.
     */
    private String type;

    /**
     * Additional parameters for the next job.
     * Will be merged with automatic parameters (previousJobId, previousJobResult, previousJobErrorMessage).
     */
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();
}
