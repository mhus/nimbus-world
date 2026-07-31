package de.mhus.nimbus.world.generator.reality;

import lombok.Builder;
import lombok.Data;

/**
 * Options for the B2 refine loop.
 */
@Data
@Builder
public class RefineOptions {

    /** provider:model to use for judge + revise (e.g. "cortecs:deepseek-v4-pro"); null = default chain. */
    private String modelName;

    /** Maximum number of revise iterations. */
    @Builder.Default
    private int maxIterations = 3;

    /** Whether to run the AI balance judge (C2) as part of the convergence criterion. */
    @Builder.Default
    private boolean useJudge = true;

    public static RefineOptions defaults() {
        return RefineOptions.builder().build();
    }

    public static RefineOptions withModel(String modelName) {
        return RefineOptions.builder().modelName(modelName).build();
    }
}
