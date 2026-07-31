package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Offline tests for the RealityWorkflow orchestration (seed → elaborate → mechanical → refine → D). */
class RealityWorkflowTest {

    private final RealitySeedGenerator seedGenerator = mock(RealitySeedGenerator.class);
    private final RealityLoreElaborator loreElaborator = mock(RealityLoreElaborator.class);
    private final RealityPlanParser parser = mock(RealityPlanParser.class);
    private final RealityCatalogExpander expander = mock(RealityCatalogExpander.class);
    private final RealityRefiner refiner = mock(RealityRefiner.class);
    private final RealityItemGenerator itemGenerator = mock(RealityItemGenerator.class);
    private final RealityLoreMaterializer loreMaterializer = mock(RealityLoreMaterializer.class);
    private final RealityRuleMaterializer ruleMaterializer = mock(RealityRuleMaterializer.class);
    private final RealityCreatureMaterializer creatureMaterializer = mock(RealityCreatureMaterializer.class);
    private final RealityDocsMaterializer docsMaterializer = mock(RealityDocsMaterializer.class);
    private final WDocumentService documentService = mock(WDocumentService.class);

    private final RealityWorkflow workflow = new RealityWorkflow(
            seedGenerator, loreElaborator, parser, expander, refiner, itemGenerator,
            loreMaterializer, ruleMaterializer, creatureMaterializer, docsMaterializer, documentService);

    private final WorldId region = WorldId.of("earth616:westview").orElseThrow();

    private RealityPlan plan() {
        RealityPlan p = new RealityPlan();
        RealityPlan.ItemSpec i = new RealityPlan.ItemSpec();
        i.setName("Rock");
        i.setType("material");
        p.setItems(List.of(i));
        return p;
    }

    private ValidationReport validReport() {
        return new ValidationReport();
    }

    private ValidationReport errorReport() {
        ValidationReport r = new ValidationReport();
        r.error("unknown_item_class", "Item 'sword' references unknown class 'steel'", "sword");
        return r;
    }

    private JudgeVerdict acceptable() {
        JudgeVerdict v = new JudgeVerdict();
        v.setAcceptable(true);
        v.setScore(90);
        return v;
    }

    private void stubDocFound() {
        WDocument doc = mock(WDocument.class);
        when(doc.getContent()).thenReturn("An instruction document.");
        when(documentService.findByDocumentId(any(), eq("doc1"))).thenReturn(Optional.of(doc));
    }

    /** Stub the full B0→B3 chain (seed → elaborate → mechanical → refine). */
    private void stubPlanningHappy(ValidationReport report) {
        RealityPlan p = plan();
        when(seedGenerator.generate(anyString(), any())).thenReturn(RealityPlanResult.success(p, "{}"));
        when(loreElaborator.elaborate(any(), any())).thenReturn(RealityPlanResult.success(p, null));
        when(expander.expand(any(), any())).thenReturn(RealityPlanResult.success(p, "{}"));
        when(refiner.refine(any(), any())).thenReturn(RefineResult.builder()
                .plan(p).converged(!report.hasErrors()).iterations(1)
                .finalReport(report).finalVerdict(acceptable()).build());
    }

    private void stubMaterializers() {
        when(itemGenerator.generateItems(any(), any())).thenReturn(RealityItemResult.builder()
                .createdItemIds(new ArrayList<>(List.of("rock"))).iconsGenerated(1).build());
        when(loreMaterializer.materialize(any(), any())).thenReturn(MaterializeResult.builder().created(2).build());
        when(ruleMaterializer.materialize(any(), any())).thenReturn(MaterializeResult.builder().created(3).build());
        when(creatureMaterializer.materialize(any(), any())).thenReturn(MaterializeResult.builder().created(1).build());
        when(docsMaterializer.materialize(any(), any())).thenReturn(MaterializeResult.builder().created(2).build());
    }

    @Test
    void runsEndToEndAndMaterializes() {
        stubDocFound();
        stubPlanningHappy(validReport());
        when(parser.savePlan(any(), anyString())).thenReturn("plan-1");
        stubMaterializers();

        RealityWorkflowResult result = workflow.generate(region, "doc1", RealityWorkflowOptions.defaults());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMaterialized()).isTrue();
        assertThat(result.getPlanDocId()).isEqualTo("plan-1");
        assertThat(result.getManifestDocId()).isNotNull();
        assertThat(result.getItemResult().getItemsCreated()).isEqualTo(1);
        assertThat(result.getLoreResult().getCreated()).isEqualTo(2);
        assertThat(result.getRuleResult().getCreated()).isEqualTo(3);
        assertThat(result.getCreatureResult().getCreated()).isEqualTo(1);
        assertThat(result.getDocsResult().getCreated()).isEqualTo(2);
        verify(seedGenerator).generate(anyString(), any());
        verify(loreElaborator).elaborate(any(), any());
        verify(parser).savePlan(any(), anyString());
        verify(itemGenerator).generateItems(any(), any());
        verify(docsMaterializer).materialize(any(), any());
        verify(documentService).save(any(), eq("reality_manifest"), anyString(), any());
    }

    @Test
    void failsWhenInstructionDocumentMissing() {
        when(documentService.findByDocumentId(any(), eq("doc1"))).thenReturn(Optional.empty());

        RealityWorkflowResult result = workflow.generate(region, "doc1", RealityWorkflowOptions.defaults());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isMaterialized()).isFalse();
        verify(seedGenerator, never()).generate(anyString(), any());
    }

    @Test
    void failsWhenSeedFails() {
        stubDocFound();
        when(seedGenerator.generate(anyString(), any())).thenReturn(RealityPlanResult.failure("bad seed", "x"));

        RealityWorkflowResult result = workflow.generate(region, "doc1", RealityWorkflowOptions.defaults());

        assertThat(result.isSuccess()).isFalse();
        verify(loreElaborator, never()).elaborate(any(), any());
        verify(expander, never()).expand(any(), any());
        verify(refiner, never()).refine(any(), any());
        verify(itemGenerator, never()).generateItems(any(), any());
    }

    @Test
    void doesNotMaterializeWhenStructuralErrorsRemain() {
        stubDocFound();
        stubPlanningHappy(errorReport()); // refine leaves a C1 error

        RealityWorkflowResult result = workflow.generate(region, "doc1", RealityWorkflowOptions.defaults());

        assertThat(result.isSuccess()).isTrue();        // a plan was produced
        assertThat(result.isMaterialized()).isFalse();  // but not committed
        assertThat(result.getErrors()).isNotEmpty();
        verify(parser, never()).savePlan(any(), anyString());
        verify(itemGenerator, never()).generateItems(any(), any());
        verify(docsMaterializer, never()).materialize(any(), any());
        verify(documentService, never()).save(any(), eq("reality_manifest"), anyString(), any());
    }

    @Test
    void planOnlyWhenMaterializeFalse() {
        stubDocFound();
        stubPlanningHappy(validReport());

        RealityWorkflowResult result = workflow.generate(region, "doc1",
                RealityWorkflowOptions.builder().materialize(false).build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMaterialized()).isFalse();
        assertThat(result.getPlan()).isNotNull();
        verify(parser, never()).savePlan(any(), anyString());
        verify(itemGenerator, never()).generateItems(any(), any());
        verify(documentService, never()).save(any(), anyString(), anyString(), any());
    }

    @Test
    void passesSelectedProviderThroughAllAiPhases() {
        stubDocFound();
        stubPlanningHappy(validReport());
        when(parser.savePlan(any(), anyString())).thenReturn("plan-1");
        stubMaterializers();
        String model = "cortecs:deepseek-v4-pro";

        workflow.generate(region, "doc1", RealityWorkflowOptions.builder().modelName(model).build());

        verify(seedGenerator).generate(anyString(), eq(model));
        verify(loreElaborator).elaborate(any(), eq(model));
        verify(expander).expand(any(), eq(model));
        ArgumentCaptor<RefineOptions> optsCap = ArgumentCaptor.forClass(RefineOptions.class);
        verify(refiner).refine(any(), optsCap.capture());
        assertThat(optsCap.getValue().getModelName()).isEqualTo(model);
    }
}
