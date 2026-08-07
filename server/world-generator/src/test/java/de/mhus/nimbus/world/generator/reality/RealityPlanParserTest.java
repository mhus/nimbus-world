package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline tests for the JSON → {@link RealityPlan} mapping ({@link RealityPlanParser#parseJson}).
 * The AI step is not exercised here (that is covered by a manual/live test).
 */
class RealityPlanParserTest {

    /** Parser instance for pure JSON parsing; collaborators are unused by {@code parseJson}. */
    private final RealityPlanParser parser = new RealityPlanParser(null, null);

    @Test
    void parsesFullPlanJson() {
        String json = """
                {
                  "meta": {
                    "regionId": "duskmoor",
                    "title": "Duskmoor",
                    "language": "en",
                    "version": 1,
                    "controls": { "maxItems": 45, "overwrite": false, "blocksFromShared": ["@shared:mc", "@shared:n"] }
                  },
                  "vision": "Low-magic celtic moorland fantasy.",
                  "style": {
                    "artStyle": "hand-painted icon",
                    "iconSize": 64,
                    "transparentBackground": true
                  },
                  "lore": [
                    { "title": "The Great Rain", "kind": "history", "content": "The kingdom sank into the moor." }
                  ],
                  "items": [
                    { "name": "Peat Spade", "type": "tool", "tier": "IRON", "rarity": "COMMON", "priceHint": 20 },
                    { "name": "Mistglass Compass", "type": "decoration", "loreBound": true }
                  ],
                  "creatures": [
                    { "name": "Bog Hound", "type": "animal", "modelPath": "n:models/animals/wolf.glb",
                      "behavior": "aggressive at night", "modifiers": { "bodyColor": "dark" } }
                  ],
                  "rules": [
                    { "name": "night-wisps", "kind": "logic", "when": "time==NIGHT", "effects": ["spawn will-o-wisp"] }
                  ],
                  "worldTemplates": [
                    { "name": "Blackfen Hollow", "summary": "small starter moor", "danger": "low" }
                  ]
                }
                """;

        RealityPlanResult result = parser.parseJson(json);

        assertThat(result.isSuccessful()).isTrue();
        RealityPlan plan = result.getPlan();
        assertThat(plan.getMeta().getRegionId()).isEqualTo("duskmoor");
        assertThat(plan.getMeta().getControls().getMaxItems()).isEqualTo(45);
        assertThat(plan.getMeta().getControls().getBlocksFromShared()).containsExactly("@shared:mc", "@shared:n");
        assertThat(plan.getStyle().getTransparentBackground()).isTrue();
        assertThat(plan.getStyle().getIconSize()).isEqualTo(64);

        assertThat(plan.getItems()).hasSize(2);
        RealityPlan.ItemSpec spade = plan.getItems().get(0);
        assertThat(spade.getName()).isEqualTo("Peat Spade");
        assertThat(spade.getType()).isEqualTo("tool");
        assertThat(spade.getPriceHint()).isEqualTo(20);
        assertThat(plan.getItems().get(1).getLoreBound()).isTrue();

        assertThat(plan.getCreatures()).hasSize(1);
        assertThat(plan.getCreatures().get(0).getModelPath()).isEqualTo("n:models/animals/wolf.glb");
        assertThat(plan.getCreatures().get(0).getModifiers()).containsEntry("bodyColor", "dark");

        assertThat(plan.getRules().get(0).getEffects()).containsExactly("spawn will-o-wisp");
        assertThat(plan.getWorldTemplates().get(0).getName()).isEqualTo("Blackfen Hollow");
    }

    @Test
    void ignoresUnknownFieldsAndTrailingCommas() {
        String json = """
                {
                  "meta": { "regionId": "x" },
                  "unknownField": "should be ignored",
                  "items": [
                    { "name": "Rock", "type": "material", "extraUnknown": 1 },
                  ]
                }
                """;

        RealityPlanResult result = parser.parseJson(json);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getPlan().getMeta().getRegionId()).isEqualTo("x");
        assertThat(result.getPlan().getItems()).hasSize(1);
        assertThat(result.getPlan().getItems().get(0).getName()).isEqualTo("Rock");
    }

    @Test
    void reportsFailureOnInvalidJson() {
        RealityPlanResult result = parser.parseJson("{ this is not valid json ");

        assertThat(result.hasFailed()).isTrue();
        assertThat(result.getErrors()).isNotEmpty();
        // The offending JSON is preserved for debugging.
        assertThat(result.getJson()).isNotNull();
    }

    @Test
    void reportsFailureOnBlankJson() {
        assertThat(parser.parseJson("   ").hasFailed()).isTrue();
    }

    /**
     * Regression fixture: a real AI output for {@code example-duskmoor.md} (captured from
     * gemini-2.5-flash via the parse-instruction prompt) must deserialize into {@link RealityPlan}.
     * Guards the DTO against drift from the actual model output shape.
     */
    @Test
    void parsesCapturedRealAiOutput() throws Exception {
        ClassPathResource resource = new ClassPathResource("reality/sample-plan-duskmoor.json");
        String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        RealityPlanResult result = parser.parseJson(json);

        assertThat(result.isSuccessful()).as("real AI sample must parse").isTrue();
        RealityPlan plan = result.getPlan();
        assertThat(plan.getMeta().getRegionId()).isEqualTo("duskmoor");
        assertThat(plan.getStyle().getTransparentBackground()).isTrue();
        assertThat(plan.getItems()).isNotEmpty();
        assertThat(plan.getCreatures()).isNotEmpty();
        assertThat(plan.getWorldTemplates()).isNotEmpty();
        assertThat(plan.getEconomy().getPriceBands()).isNotEmpty();
    }

    /**
     * {@code WDocumentService.save} de-duplicates by name: on a re-run it updates the document that
     * already carries the name "reality-plan" and returns THAT one, whose documentId differs from
     * the freshly generated one. savePlan must report the id of the persisted document, otherwise
     * the caller (and the reality manifest) keep a reference that resolves to nothing.
     */
    @Test
    void savePlanReturnsTheIdOfThePersistedDocument() {
        WDocumentService documentService = mock(WDocumentService.class);
        WDocument existing = mock(WDocument.class);
        when(existing.getDocumentId()).thenReturn("existing-plan-doc");
        when(documentService.save(any(), eq(RealityPlanParser.PLAN_COLLECTION), anyString(), any()))
                .thenReturn(existing);
        RealityPlanParser saving = new RealityPlanParser(null, documentService);

        String documentId = saving.savePlan(WorldId.of("@region:duskmoor").orElseThrow(), "{}");

        assertThat(documentId).isEqualTo("existing-plan-doc");
    }
}
