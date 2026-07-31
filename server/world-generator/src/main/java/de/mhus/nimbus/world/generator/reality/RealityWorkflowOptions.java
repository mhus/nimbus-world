package de.mhus.nimbus.world.generator.reality;

import lombok.Builder;
import lombok.Data;

/**
 * Options for the end-to-end {@link RealityWorkflow}.
 */
@Data
@Builder
public class RealityWorkflowOptions {

    /** provider:model for expand/refine/judge (e.g. "cortecs:deepseek-v4-pro"); null = default chain. */
    private String modelName;

    /** Run B1 lore elaboration (phase 2: per-chapter deep lore). */
    @Builder.Default
    private boolean elaborateLore = true;

    /** Run the B2 mechanical catalog expansion (items/classes/creatures/rules, lore-first). */
    @Builder.Default
    private boolean expand = true;

    /** Run the C2 balance judge inside the refine loop. */
    @Builder.Default
    private boolean useJudge = true;

    /** Max refine iterations (B2). */
    @Builder.Default
    private int refineIterations = 3;

    /**
     * Materialize into the DB (Stage D): persist reality_plan, create items + icons, write manifest.
     * When false, only plan (A→C) runs and the plan is returned without any DB writes.
     */
    @Builder.Default
    private boolean materialize = true;

    /** Within Stage D, generate the item catalog (items + transparent icons). */
    @Builder.Default
    private boolean generateItems = true;

    /** Within Stage D, materialize lore/factions/npcs as documents (D1). */
    @Builder.Default
    private boolean generateLore = true;

    /** Within Stage D, materialize logic/building rules (D6). */
    @Builder.Default
    private boolean generateRules = true;

    /** Within Stage D, materialize creature presets as entity models (D5). */
    @Builder.Default
    private boolean generateCreatures = true;

    /** Within Stage D, write the design-rationale + world-directives documents. */
    @Builder.Default
    private boolean generateDocs = true;

    public static RealityWorkflowOptions defaults() {
        return RealityWorkflowOptions.builder().build();
    }
}
