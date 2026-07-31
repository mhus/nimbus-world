package de.mhus.nimbus.world.generator.reality;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the B2 refine loop: the (best) plan plus how it got there.
 */
@Data
@Builder
public class RefineResult {

    /** The resulting plan (possibly still imperfect if not converged). */
    private RealityPlan plan;

    /** Number of revise iterations actually performed. */
    @Builder.Default
    private int iterations = 0;

    /** True if the final plan is valid (no C1 errors) and balance-accepted (or judge inconclusive). */
    @Builder.Default
    private boolean converged = false;

    /** Final mechanical validation report of {@link #plan}. */
    private ValidationReport finalReport;

    /** Final balance verdict of {@link #plan} (null if judge disabled). */
    private JudgeVerdict finalVerdict;

    /** Human-readable per-step trace. */
    @Builder.Default
    private List<String> log = new ArrayList<>();
}
