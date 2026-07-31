package de.mhus.nimbus.world.generator.reality;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of an end-to-end {@link RealityWorkflow} run.
 */
@Data
@Builder
public class RealityWorkflowResult {

    /** Overall success (a plan was produced; if materialize=true, it was also committed). */
    @Builder.Default
    private boolean success = false;

    /** Whether Stage D actually wrote to the DB. */
    @Builder.Default
    private boolean materialized = false;

    /** Whether the refine loop converged (valid + balance-accepted). */
    @Builder.Default
    private boolean converged = false;

    private RealityPlan plan;
    private ValidationReport report;
    private JudgeVerdict verdict;
    private RealityItemResult itemResult;
    private MaterializeResult loreResult;
    private MaterializeResult creatureResult;
    private MaterializeResult ruleResult;
    private MaterializeResult docsResult;

    /** documentId of the persisted reality_plan snapshot (null if not materialized). */
    private String planDocId;
    /** documentId of the persisted reality_manifest (null if not materialized). */
    private String manifestDocId;

    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> log = new ArrayList<>();

    public static RealityWorkflowResult failure(String error, List<String> log) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return RealityWorkflowResult.builder()
                .success(false)
                .errors(errors)
                .log(log == null ? new ArrayList<>() : log)
                .build();
    }
}
