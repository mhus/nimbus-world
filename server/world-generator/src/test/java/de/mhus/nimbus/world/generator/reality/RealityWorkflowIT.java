package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.WorldGeneratorApplication;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that runs the real {@link RealityWorkflow} end-to-end against the local
 * infrastructure (Mongo + Redis + live AI keys stored in s_settings). Boots the full
 * {@link WorldGeneratorApplication} context.
 * <p>
 * Excluded from normal builds ({@code @Tag("manual")}) — it needs a running local Mongo/Redis and a
 * configured AI provider. Run explicitly:
 * <pre>{@code
 *   mvn -pl world-generator test -Dtest=RealityWorkflowIT -DexcludedGroups=
 * }</pre>
 * The chat provider is fixed to {@code gemini:gemini-2.5-flash} here; switch to
 * {@code cortecs:deepseek-v4-pro} to compare. Data is written under region {@code @region:reality_it}.
 */
@SpringBootTest(classes = WorldGeneratorApplication.class)
@Tag("manual")
class RealityWorkflowIT {

    private static final String MODEL = "gemini:gemini-2.5-flash";

    @Autowired
    private RealityWorkflow workflow;
    @Autowired
    private WDocumentService documentService;
    @Autowired
    private WItemService itemService;
    @Autowired
    private RealityLoreMaterializer loreMaterializer;
    @Autowired
    private RealityRuleMaterializer ruleMaterializer;
    @Autowired
    private RealityCreatureMaterializer creatureMaterializer;
    @Autowired
    private WLogicRuleService ruleService;
    @Autowired
    private WEntityModelService entityModelService;

    private final WorldId region = WorldId.of(WorldId.COLLECTION_REGION, "reality_it").orElseThrow();

    private String seedInstruction() throws Exception {
        String content = new String(new ClassPathResource("reality/it-instruction.md")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String docId = UUID.randomUUID().toString();
        String name = "it-instructions";
        documentService.save(region, RealityPlanParser.INSTRUCTIONS_COLLECTION, docId, doc -> {
            doc.setName(name);
            doc.setTitle("Duskmoor IT");
            doc.setContent(content);
            doc.setFormat("markdown");
        });
        // save() de-duplicates by name: if a doc with this name already exists (from a previous run)
        // it updates that one and keeps ITS documentId, so resolve the actually persisted id.
        return documentService.findByName(region, RealityPlanParser.INSTRUCTIONS_COLLECTION, name)
                .map(d -> d.getDocumentId())
                .orElse(docId);
    }

    @Test
    void planOnlyEndToEnd() throws Exception {
        String docId = seedInstruction();

        RealityWorkflowResult result = workflow.generate(region, docId, RealityWorkflowOptions.builder()
                .modelName(MODEL)
                .expand(true)
                .useJudge(true)
                .refineIterations(1)
                .materialize(false) // plan only — no DB writes, no icon generation
                .build());

        System.out.println("=== plan-only run ===\n" + String.join("\n", result.getLog()));
        if (result.getVerdict() != null) {
            System.out.println("verdict: acceptable=" + result.getVerdict().isAcceptable()
                    + " score=" + result.getVerdict().getScore());
        }
        RealityPlan p = result.getPlan();
        if (p.getDirection() != null) {
            System.out.println("direction: " + p.getDirection().getPremise());
        }
        if (p.getBackgroundPowers() != null && !p.getBackgroundPowers().isEmpty()) {
            var bp = p.getBackgroundPowers().get(0);
            System.out.println("power[0]: " + bp.getName() + " (" + bp.getInfluence() + "/" + bp.getStatus() + ")");
        }
        System.out.println("powers=" + (p.getBackgroundPowers() == null ? 0 : p.getBackgroundPowers().size())
                + " loreChapters=" + (p.getLore() == null ? 0 : p.getLore().size())
                + " items=" + (p.getItems() == null ? 0 : p.getItems().size())
                + " classes=" + (p.getItemClasses() == null ? 0 : p.getItemClasses().size())
                + " report-errors=" + (result.getReport() == null ? "?" : result.getReport().errors().size()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMaterialized()).isFalse();
        assertThat(p).isNotNull();
        assertThat(p.getDirection()).as("seed produced a core direction").isNotNull();
        assertThat(p.getBackgroundPowers()).as("seed produced background power(s)").isNotEmpty();
        assertThat(p.getLore()).as("phase-2 elaborated deep lore").isNotEmpty();
        assertThat(p.getItems()).as("mechanical catalog derived").isNotEmpty();
        assertThat(result.getReport()).isNotNull();
    }

    @Test
    void materializesLoreRulesCreaturesToDb() {
        WorldId reg = WorldId.of(WorldId.COLLECTION_REGION, "reality_it").orElseThrow();

        RealityPlan plan = new RealityPlan();
        RealityPlan.LoreEntry lore = new RealityPlan.LoreEntry();
        lore.setTitle("IT Lore");
        lore.setKind("history");
        lore.setContent("Once upon an integration test.");
        plan.setLore(List.of(lore));

        RealityPlan.RuleSpec rule = new RealityPlan.RuleSpec();
        rule.setName("IT Rule");
        rule.setKind("logic");
        rule.setWhen("flags.itFlag==1");
        rule.setEffects(List.of("do something"));
        plan.setRules(List.of(rule));

        RealityPlan.CreatureSpec creature = new RealityPlan.CreatureSpec();
        creature.setName("IT Beast");
        creature.setType("animal");
        creature.setModelPath("n:models/animals/wolf.glb");
        creature.setModifiers(Map.of("bodyColor", "dark"));
        plan.setCreatures(List.of(creature));

        try {
            assertThat(loreMaterializer.materialize(reg, plan).getCreated()).isEqualTo(1);
            assertThat(ruleMaterializer.materialize(reg, plan).getCreated()).isEqualTo(1);
            assertThat(creatureMaterializer.materialize(reg, plan).getCreated()).isEqualTo(1);

            // Verify the rows really landed in the DB.
            assertThat(documentService.findByName(reg, "lore", "it_lore")).isPresent();
            assertThat(ruleService.findByWorldIdAndName(reg.getId(), "it_rule")).isPresent();
            assertThat(entityModelService.findByModelId(reg, "it_beast")).isPresent();
            System.out.println("Stage-D materializers verified: lore + rule + creature written to DB");
        } finally {
            entityModelService.delete(reg, "it_beast"); // best-effort cleanup
        }
    }

    @Test
    void materializeSmallCatalog() throws Exception {
        String docId = seedInstruction();
        try {
            RealityWorkflowResult result = workflow.generate(region, docId, RealityWorkflowOptions.builder()
                    .modelName(MODEL)
                    .expand(false)          // keep it small: only the ~9 named items
                    .useJudge(false)        // skip judge to keep the run cheap/fast
                    .refineIterations(1)
                    .materialize(true)
                    .generateItems(true)
                    .build());

            System.out.println("=== materialize run ===\n" + String.join("\n", result.getLog()));

            assertThat(result.isSuccess()).isTrue();
            if (result.isMaterialized()) {
                assertThat(result.getPlanDocId()).isNotNull();
                assertThat(result.getManifestDocId()).isNotNull();
                assertThat(result.getItemResult().getItemsCreated()).isGreaterThan(0);
                // the items really exist in the DB
                assertThat(itemService.findByWorldId(region)).isNotEmpty();
            } else {
                System.out.println("not materialized: " + result.getErrors());
            }
        } finally {
            // best-effort cleanup of items created under the test region
            itemService.deleteAllByWorldId(region.getId());
        }
    }
}
