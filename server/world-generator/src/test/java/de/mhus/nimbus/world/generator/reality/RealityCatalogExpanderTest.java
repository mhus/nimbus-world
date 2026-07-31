package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiModelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Offline tests for B1 catalog expansion. The AI chat is mocked (no network). */
class RealityCatalogExpanderTest {

    private final AiModelService aiModelService = mock(AiModelService.class);
    private final RealityPlanParser parser = new RealityPlanParser(null, null); // parseJson only
    private final RealityCatalogExpander expander = new RealityCatalogExpander(aiModelService, parser);

    private RealityPlan smallPlan() {
        RealityPlan plan = new RealityPlan();
        RealityPlan.Meta meta = new RealityPlan.Meta();
        meta.setRegionId("duskmoor");
        plan.setMeta(meta);
        RealityPlan.ItemSpec spade = new RealityPlan.ItemSpec();
        spade.setName("Peat Spade");
        spade.setType("tool");
        plan.setItems(List.of(spade));
        return plan;
    }

    @Test
    void expandsPlanViaAiAndParsesResult() throws Exception {
        AiChat chat = mock(AiChat.class);
        when(aiModelService.createChat(anyString(), any())).thenReturn(Optional.of(chat));
        String expandedJson = """
                {
                  "meta": { "regionId": "duskmoor" },
                  "itemClasses": [ { "name": "iron", "rank": 2, "tier": "IRON" } ],
                  "items": [
                    { "name": "Peat Spade", "type": "tool", "itemClass": "iron" },
                    { "name": "Peat Brick", "type": "material", "source": "peat block" },
                    { "name": "One-Up Forever", "type": "super", "consumable": false, "persistent": true, "effect": "extra_life" }
                  ]
                }
                """;
        when(chat.ask(anyString())).thenReturn("```json\n" + expandedJson + "\n```");

        RealityPlanResult result = expander.expand(smallPlan());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getPlan().getItems()).hasSize(3);
        assertThat(result.getPlan().getItemClasses()).hasSize(1);

        // The prompt must carry the current plan + directives.
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        verify(chat).ask(promptCap.capture());
        String prompt = promptCap.getValue();
        assertThat(prompt).contains("duskmoor");        // serialized current plan
        assertThat(prompt).contains("Peat Spade");
        assertThat(prompt).contains("one_up");           // super-items directive default
    }

    @Test
    void skipsExpansionWhenDisabled() {
        RealityPlan plan = smallPlan();
        RealityPlan.GenerationControls controls = new RealityPlan.GenerationControls();
        controls.setExpandCatalog(false);
        plan.getMeta().setControls(controls);

        RealityPlanResult result = expander.expand(plan);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getPlan()).isSameAs(plan);      // unchanged
        verify(aiModelService, never()).createChat(anyString(), any());
    }

    @Test
    void failsWhenNoChatModel() {
        when(aiModelService.createChat(anyString(), any())).thenReturn(Optional.empty());
        RealityPlanResult result = expander.expand(smallPlan());
        assertThat(result.hasFailed()).isTrue();
    }

    @Test
    void buildsCoverageDirectiveFromControls() {
        RealityPlan plan = smallPlan();
        RealityPlan.GenerationControls c = new RealityPlan.GenerationControls();
        c.setTargetItemCount(200);
        c.setCategoryCoverage(Map.of("weapon", 30));
        plan.getMeta().setControls(c);

        String directive = expander.buildCoverageDirective(plan);
        assertThat(directive).contains("200");
        assertThat(directive).contains("weapon").contains("30");
    }

    @Test
    void superItemsDirectiveDefaultsToOneUp() {
        assertThat(expander.buildSuperItemsDirective(smallPlan())).contains("one_up");
    }
}
