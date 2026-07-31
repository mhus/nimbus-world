package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiModelService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline tests for the B2 refine loop. Validator + parser are real (deterministic); the AI judge and
 * the revise chat are mocked.
 */
class RealityRefinerTest {

    private final AiModelService aiModelService = mock(AiModelService.class);
    private final RealityJudge judge = mock(RealityJudge.class);
    private final RealityRefiner refiner =
            new RealityRefiner(aiModelService, new RealityPlanParser(null, null), new RealityValidator(), judge);

    /** A plan with a structural C1 error: item references an item class that is not defined. */
    private RealityPlan brokenPlan() {
        RealityPlan plan = new RealityPlan();
        RealityPlan.Meta meta = new RealityPlan.Meta();
        meta.setRegionId("duskmoor");
        plan.setMeta(meta);
        RealityPlan.ItemSpec sword = new RealityPlan.ItemSpec();
        sword.setName("Steel Sword");
        sword.setType("weapon");
        sword.setItemClass("steel"); // undefined -> unknown_item_class ERROR
        plan.setItems(List.of(sword));
        return plan;
    }

    /** JSON of a clean, valid plan (no undefined class reference). */
    private static final String CLEAN_PLAN_JSON = """
            { "meta": { "regionId": "duskmoor" },
              "items": [ { "name": "Steel Sword", "type": "weapon", "tier": "STEEL" } ] }
            """;

    private JudgeVerdict acceptable() {
        JudgeVerdict v = new JudgeVerdict();
        v.setAcceptable(true);
        v.setScore(90);
        return v;
    }

    private void stubReviseReturns(String json) throws Exception {
        AiChat chat = mock(AiChat.class);
        when(aiModelService.createChat(any(), any())).thenReturn(Optional.of(chat));
        when(chat.ask(anyString())).thenReturn(json);
    }

    @Test
    void refinesAwayAStructuralErrorAndConverges() throws Exception {
        when(judge.judge(any(), any())).thenReturn(acceptable());
        stubReviseReturns(CLEAN_PLAN_JSON);

        RefineResult result = refiner.refine(brokenPlan(), RefineOptions.builder().maxIterations(3).build());

        assertThat(result.isConverged()).isTrue();
        assertThat(result.getIterations()).isEqualTo(1);
        assertThat(result.getFinalReport().isValid()).isTrue();
        assertThat(result.getPlan().getItems()).hasSize(1);
    }

    @Test
    void doesNothingWhenAlreadyGood() {
        // valid plan + acceptable balance -> converged immediately, no revise call
        RealityPlan plan = new RealityPlan();
        RealityPlan.ItemSpec rock = new RealityPlan.ItemSpec();
        rock.setName("Rock");
        rock.setType("material");
        rock.setSource("stone block");
        plan.setItems(List.of(rock));
        when(judge.judge(any(), any())).thenReturn(acceptable());

        RefineResult result = refiner.refine(plan);

        assertThat(result.isConverged()).isTrue();
        assertThat(result.getIterations()).isZero();
        verify(aiModelService, never()).createChat(any(), any()); // no revise
    }

    @Test
    void stopsAtIterationLimitWhenNeverConverging() throws Exception {
        // revise keeps returning a still-broken plan -> C1 error persists
        RealityPlan stillBroken = brokenPlan();
        String stillBrokenJson = new tools.jackson.databind.json.JsonMapper().writeValueAsString(stillBroken);
        when(judge.judge(any(), any())).thenReturn(acceptable());
        stubReviseReturns(stillBrokenJson);

        RefineResult result = refiner.refine(brokenPlan(), RefineOptions.builder().maxIterations(2).build());

        assertThat(result.isConverged()).isFalse();
        assertThat(result.getIterations()).isEqualTo(2);
        assertThat(result.getFinalReport().hasErrors()).isTrue();
    }

    @Test
    void passesSelectedProviderToJudgeAndRevise() throws Exception {
        String model = "cortecs:deepseek-v4-pro";
        when(judge.judge(any(), any())).thenReturn(acceptable());
        stubReviseReturns(CLEAN_PLAN_JSON);

        refiner.refine(brokenPlan(), RefineOptions.withModel(model));

        verify(judge, atLeastOnce()).judge(any(), eq(model)); // judge used the selected provider
        verify(aiModelService).createChat(eq(model), any());  // revise used the selected provider
    }

    @Test
    void blocksOnMajorBalanceFindingEvenIfStructurallyValid() throws Exception {
        // structurally valid plan, but judge rejects on balance -> loop tries to refine
        RealityPlan plan = new RealityPlan();
        RealityPlan.ItemSpec rock = new RealityPlan.ItemSpec();
        rock.setName("Rock");
        rock.setType("material");
        rock.setSource("stone");
        plan.setItems(List.of(rock));

        JudgeVerdict rejected = new JudgeVerdict();
        rejected.setAcceptable(false);
        rejected.setScore(40);
        rejected.setFindings(List.of(new JudgeFinding("economy", "major", "price bands off", "fix bands")));
        when(judge.judge(any(), any())).thenReturn(rejected); // always rejects
        stubReviseReturns(new tools.jackson.databind.json.JsonMapper()
                .writeValueAsString(plan)); // revise returns same (still rejected)

        RefineResult result = refiner.refine(plan, RefineOptions.builder().maxIterations(2).build());

        assertThat(result.isConverged()).isFalse();          // balance never accepted
        assertThat(result.getIterations()).isEqualTo(2);
        assertThat(result.getFinalReport().isValid()).isTrue(); // structurally fine though
    }
}
