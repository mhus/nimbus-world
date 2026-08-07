package de.mhus.nimbus.world.generator.reality;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * End-to-end orchestrator for the Reality Generator pipeline (Stages A→D of
 * {@code workflow-architecture.md}). Runs entirely synchronously:
 * <pre>
 *   A Intake   : load the reality_instructions document
 *   B0 Parse   : RealityPlanParser         (text -> RealityPlan)
 *   B1 Expand  : RealityCatalogExpander    (fill the catalog from the preset)   [optional]
 *   B2 Refine  : RealityRefiner            (loop: C1 validate + C2 judge -> revise)
 *   -- gate --  : only a structurally valid plan (no C1 errors) is materialized
 *   Snapshot   : persist reality_plan
 *   D4 Items   : RealityItemGenerator      (items + transparent icons)          [optional]
 *   Manifest   : persist reality_manifest
 * </pre>
 * The chat provider (expand/refine/judge) is selectable via {@link RealityWorkflowOptions#getModelName()}.
 * With {@code materialize=false} only A→C run and no DB write happens (pure planning).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityWorkflow {

    private static final String MANIFEST_COLLECTION = "reality_manifest";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final RealitySeedGenerator seedGenerator;
    private final RealityLoreElaborator loreElaborator;
    private final RealityPlanParser parser;
    private final RealityCatalogExpander expander;
    private final RealityRefiner refiner;
    private final RealityItemGenerator itemGenerator;
    private final RealityLoreMaterializer loreMaterializer;
    private final RealityRuleMaterializer ruleMaterializer;
    private final RealityCreatureMaterializer creatureMaterializer;
    private final RealityDocsMaterializer docsMaterializer;
    private final WDocumentService documentService;

    /**
     * Generate the reality of a region from its instruction document.
     *
     * @param regionId          the region (or a world id; item/asset services resolve the region)
     * @param instructionsDocId documentId of the {@code reality_instructions} document
     * @param options           pipeline options
     * @return the workflow result
     */
    public RealityWorkflowResult generate(WorldId regionId, String instructionsDocId,
                                          RealityWorkflowOptions options) {
        List<String> log = new ArrayList<>();
        if (regionId == null || Strings.isBlank(instructionsDocId)) {
            return RealityWorkflowResult.failure("regionId and instructionsDocId are required", log);
        }
        WorldId region = regionId.toRegionCollection();

        // A — Intake
        Optional<WDocument> docOpt = documentService.findByDocumentId(region, instructionsDocId);
        if (docOpt.isEmpty() || Strings.isBlank(docOpt.get().getContent())) {
            return RealityWorkflowResult.failure(
                    "Instruction document not found or empty: " + instructionsDocId, log);
        }
        log.add("A intake: loaded instructions " + instructionsDocId);

        // B0 — Seed (phase 1, high temperature: creative skeleton)
        RealityPlanResult seed = seedGenerator.generate(docOpt.get().getContent(), options.getModelName());
        if (seed.hasFailed()) {
            RealityWorkflowResult r = RealityWorkflowResult.failure("Seed generation failed", log);
            r.getErrors().addAll(seed.getErrors());
            return r;
        }
        RealityPlan plan = seed.getPlan();
        log.add("B0 seed: " + count(plan.getOutline()) + " chapters, "
                + count(plan.getBackgroundPowers()) + " background powers");

        // B1 — Elaborate lore (phase 2, low temperature, per chapter with rolling summary)
        if (options.isElaborateLore()) {
            RealityPlanResult elaborated = loreElaborator.elaborate(plan, options.getModelName());
            if (elaborated.isSuccessful()) {
                plan = elaborated.getPlan();
                log.add("B1 elaborate: " + count(plan.getLore()) + " lore chapters");
            } else {
                log.add("B1 elaborate failed: " + String.join("; ", elaborated.getErrors()));
            }
        }

        // B2 — Mechanical catalog, derived from the lore (lore-first)
        if (options.isExpand()) {
            RealityPlanResult expanded = expander.expand(plan, options.getModelName());
            if (expanded.isSuccessful()) {
                plan = expanded.getPlan();
                log.add("B2 mechanical: " + count(plan.getItems()) + " items, "
                        + count(plan.getItemClasses()) + " classes");
            } else {
                log.add("B2 mechanical skipped/failed: " + String.join("; ", expanded.getErrors()));
            }
        }

        // B2 — Refine (runs C1 + C2 internally)
        RefineResult refine = refiner.refine(plan, RefineOptions.builder()
                .modelName(options.getModelName())
                .maxIterations(options.getRefineIterations())
                .useJudge(options.isUseJudge())
                .build());
        plan = refine.getPlan();
        log.addAll(refine.getLog());
        ValidationReport report = refine.getFinalReport();
        JudgeVerdict verdict = refine.getFinalVerdict();

        RealityWorkflowResult.RealityWorkflowResultBuilder result = RealityWorkflowResult.builder()
                .success(true)
                .converged(refine.isConverged())
                .balanceChecked(refine.isBalanceChecked())
                .plan(plan)
                .report(report)
                .verdict(verdict)
                .log(log);

        // Gate + optional materialization
        if (!options.isMaterialize()) {
            log.add("materialize=false: plan-only, no DB writes");
            return result.materialized(false).build();
        }
        // No report means "not validated" — that must block materialization just like a failed
        // validation does, otherwise an unvalidated plan would be written to the DB.
        if (report == null) {
            log.add("gate: no validation report -> NOT materialized");
            return result.materialized(false)
                    .errors(new ArrayList<>(List.of("no validation report available"))).build();
        }
        if (report.hasErrors()) {
            log.add("gate: " + report.errors().size() + " structural error(s) -> NOT materialized");
            List<String> errors = new ArrayList<>();
            report.errors().forEach(e -> errors.add(e.getCode() + ": " + e.getMessage()));
            return result.materialized(false).errors(errors).build();
        }

        // D — Commit
        String planJson;
        try {
            planJson = MAPPER.writeValueAsString(plan);
        } catch (Exception e) {
            return result.materialized(false)
                    .errors(new ArrayList<>(List.of("serialize plan failed: " + e.getMessage()))).build();
        }
        String planDocId = parser.savePlan(region, planJson);
        log.add("snapshot: saved reality_plan " + planDocId);

        RealityItemResult itemResult = null;
        if (options.isGenerateItems()) {
            itemResult = itemGenerator.generateItems(region, plan);
            log.add("D4 items: created " + itemResult.getItemsCreated() + ", icons "
                    + itemResult.getIconsGenerated() + ", errors " + itemResult.getErrors().size());
        }

        MaterializeResult loreResult = null;
        if (options.isGenerateLore()) {
            loreResult = loreMaterializer.materialize(region, plan);
            log.add("D1 lore: " + loreResult.getCreated() + " docs, " + loreResult.getErrors().size() + " errors");
        }
        MaterializeResult creatureResult = null;
        if (options.isGenerateCreatures()) {
            creatureResult = creatureMaterializer.materialize(region, plan);
            log.add("D5 creatures: " + creatureResult.getCreated() + " models, "
                    + creatureResult.getErrors().size() + " errors");
        }
        MaterializeResult ruleResult = null;
        if (options.isGenerateRules()) {
            ruleResult = ruleMaterializer.materialize(region, plan);
            log.add("D6 rules: " + ruleResult.getCreated() + " rules, " + ruleResult.getErrors().size() + " errors");
        }
        MaterializeResult docsResult = null;
        if (options.isGenerateDocs()) {
            docsResult = docsMaterializer.materialize(region, plan);
            log.add("D docs: " + docsResult.getCreated() + " (design + world-directives), "
                    + docsResult.getErrors().size() + " errors");
        }

        // Collect the per-entry errors of every enabled Stage-D step. A run that wrote entities but
        // hit errors is "partial", not successful — reporting it as success would hide a region
        // whose items point at textures that were never generated.
        List<String> stageErrors = collectStageErrors(itemResult, loreResult, creatureResult,
                ruleResult, docsResult);

        String manifestDocId = saveManifest(region, plan, report, verdict, itemResult,
                loreResult, creatureResult, ruleResult, planDocId, refine.isConverged(),
                refine.isBalanceChecked(), refine.getJudgeErrors());
        log.add("manifest: saved reality_manifest " + manifestDocId);
        if (!stageErrors.isEmpty()) {
            log.add("PARTIAL: " + stageErrors.size() + " error(s) during materialization");
        }

        return result
                .success(stageErrors.isEmpty())
                .partial(!stageErrors.isEmpty())
                .materialized(true)
                .errors(stageErrors)
                .itemResult(itemResult)
                .loreResult(loreResult)
                .creatureResult(creatureResult)
                .ruleResult(ruleResult)
                .docsResult(docsResult)
                .planDocId(planDocId)
                .manifestDocId(manifestDocId)
                .build();
    }

    /** Flatten the error lists of all Stage-D results that actually ran. */
    private static List<String> collectStageErrors(RealityItemResult itemResult,
                                                   MaterializeResult... materializeResults) {
        List<String> errors = new ArrayList<>();
        if (itemResult != null) {
            errors.addAll(itemResult.getErrors());
        }
        for (MaterializeResult r : materializeResults) {
            if (r != null) {
                errors.addAll(r.getErrors());
            }
        }
        return errors;
    }

    private String saveManifest(WorldId region, RealityPlan plan, ValidationReport report,
                                JudgeVerdict verdict, RealityItemResult itemResult,
                                MaterializeResult loreResult, MaterializeResult creatureResult,
                                MaterializeResult ruleResult, String planDocId, boolean converged,
                                boolean balanceChecked, List<String> judgeErrors) {
        Manifest manifest = new Manifest();
        manifest.setBalanceChecked(balanceChecked);
        if (judgeErrors != null && !judgeErrors.isEmpty()) {
            manifest.setJudgeErrors(judgeErrors);
        }
        manifest.setRegionId(region.getId());
        manifest.setPlanDocId(planDocId);
        manifest.setConverged(converged);
        manifest.setItemCount(count(plan.getItems()));
        manifest.setItemClassCount(count(plan.getItemClasses()));
        manifest.setItemsCreated(itemResult == null ? 0 : itemResult.getItemsCreated());
        manifest.setIconsGenerated(itemResult == null ? 0 : itemResult.getIconsGenerated());
        manifest.setLoreDocuments(loreResult == null ? 0 : loreResult.getCreated());
        manifest.setCreatureModels(creatureResult == null ? 0 : creatureResult.getCreated());
        manifest.setRulesCreated(ruleResult == null ? 0 : ruleResult.getCreated());
        if (report != null) {
            manifest.setValidationErrors(report.errors().size());
            manifest.setValidationWarnings(report.warnings().size());
        }
        if (verdict != null && verdict.isConclusive()) {
            manifest.setBalanceScore(verdict.getScore());
            manifest.setBalanceAcceptable(verdict.isAcceptable());
        }
        if (itemResult != null && !itemResult.getErrors().isEmpty()) {
            manifest.setItemErrors(itemResult.getErrors());
        }

        String json;
        try {
            json = MAPPER.writeValueAsString(manifest);
        } catch (Exception e) {
            json = "{\"error\":\"failed to serialize manifest\"}";
        }
        final String content = json;
        // Use the id of the persisted document: save() de-duplicates by name, so on a re-run the
        // existing manifest is updated and keeps its original documentId.
        WDocument saved = documentService.save(region, MANIFEST_COLLECTION, UUID.randomUUID().toString(), doc -> {
            doc.setName("reality-manifest");
            doc.setTitle("Reality Manifest");
            doc.setContent(content);
            doc.setFormat("json");
            doc.setType("reality_manifest");
        });
        return saved.getDocumentId();
    }

    private static int count(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /** Typed summary persisted as the reality_manifest document content. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Manifest {
        private String regionId;
        private String planDocId;
        private boolean converged;
        private int itemCount;
        private int itemClassCount;
        private int itemsCreated;
        private int iconsGenerated;
        private int loreDocuments;
        private int creatureModels;
        private int rulesCreated;
        private int validationErrors;
        private int validationWarnings;
        /**
         * Whether the balance judge produced a verdict at all. When false, {@code balanceScore} and
         * {@code balanceAcceptable} are absent because the balance was never assessed — not because
         * it was assessed and found unremarkable.
         */
        private boolean balanceChecked;
        private Integer balanceScore;
        private Boolean balanceAcceptable;
        /** Why the judge could not produce a verdict (empty/absent when it ran). */
        private List<String> judgeErrors;
        private List<String> itemErrors;
    }
}
