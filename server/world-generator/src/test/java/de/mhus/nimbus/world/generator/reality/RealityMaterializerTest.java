package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the Stage-D materializers (lore/rules/creatures). Backing services are mocked. */
class RealityMaterializerTest {

    private final WorldId region = WorldId.of(WorldId.COLLECTION_REGION, "reality_it").orElseThrow();

    // ---- D1 Lore ----

    @Test
    void loreMaterializerWritesLoreFactionsAndNpcsToDocuments() {
        WDocumentService docs = mock(WDocumentService.class);
        RealityLoreMaterializer m = new RealityLoreMaterializer(docs);

        RealityPlan plan = new RealityPlan();
        RealityPlan.LoreEntry history = new RealityPlan.LoreEntry();
        history.setTitle("The Great Rain");
        history.setKind("history");
        history.setContent("...");
        RealityPlan.LoreEntry faction = new RealityPlan.LoreEntry();
        faction.setTitle("Hollow Court");
        faction.setKind("faction");
        faction.setContent("...");
        plan.setLore(List.of(history, faction));
        RealityPlan.NpcSpec npc = new RealityPlan.NpcSpec();
        npc.setRole("Iron Warden");
        npc.setFaction("Wardens");
        npc.setTone("serious");
        plan.setNpcs(List.of(npc));

        MaterializeResult r = m.materialize(region, plan);

        assertThat(r.getCreated()).isEqualTo(3);
        ArgumentCaptor<String> collectionCap = ArgumentCaptor.forClass(String.class);
        verify(docs, times(3)).save(eq(region), collectionCap.capture(), anyString(), any());
        assertThat(collectionCap.getAllValues()).containsExactly("lore", "factions", "npcs");
    }

    // ---- D6 Rules ----

    @Test
    void ruleMaterializerCreatesLogicRulesWithMappedEffects() {
        WLogicRuleService rules = mock(WLogicRuleService.class);
        when(rules.findByWorldIdAndName(anyString(), anyString())).thenReturn(Optional.empty());
        RealityRuleMaterializer m = new RealityRuleMaterializer(rules);

        RealityPlan plan = new RealityPlan();
        RealityPlan.RuleSpec rule = new RealityPlan.RuleSpec();
        rule.setName("Night Wisps");
        rule.setKind("logic");
        rule.setWhen("time==NIGHT");
        rule.setEffects(List.of("spawn will-o-wisp", "bog hounds aggressive"));
        rule.setDescription("night behavior");
        plan.setRules(List.of(rule));

        MaterializeResult r = m.materialize(region, plan);

        assertThat(r.getCreated()).isEqualTo(1);
        ArgumentCaptor<WLogicRule> cap = ArgumentCaptor.forClass(WLogicRule.class);
        verify(rules).save(cap.capture());
        WLogicRule saved = cap.getValue();
        assertThat(saved.getWorldId()).isEqualTo("@region:reality_it");
        assertThat(saved.getName()).isEqualTo("night_wisps");
        assertThat(saved.getSpelCondition()).isEqualTo("time==NIGHT");
        assertThat(saved.getEffects()).hasSize(2);
        assertThat(saved.getEffects().get(0).getType()).isEqualTo("reality");
        assertThat(saved.getEffects().get(0).getParameters()).containsEntry("effect", "spawn will-o-wisp");
        assertThat(saved.getEpoches()).containsExactly(0);
    }

    // ---- D5 Creatures ----

    @Test
    void creatureMaterializerCreatesEntityModelsFromSharedModelPaths() {
        WEntityModelService models = mock(WEntityModelService.class);
        RealityCreatureMaterializer m = new RealityCreatureMaterializer(models);

        RealityPlan plan = new RealityPlan();
        RealityPlan.CreatureSpec c = new RealityPlan.CreatureSpec();
        c.setName("Bog Hound");
        c.setType("animal");
        c.setModelPath("n:models/animals/wolf.glb");
        c.setBehavior("aggressive at night");
        c.setModifiers(Map.of("bodyColor", "dark"));
        plan.setCreatures(List.of(c));

        MaterializeResult r = m.materialize(region, plan);

        assertThat(r.getCreated()).isEqualTo(1);
        ArgumentCaptor<EntityModel> cap = ArgumentCaptor.forClass(EntityModel.class);
        verify(models).save(eq(region), eq("bog_hound"), cap.capture());
        EntityModel dto = cap.getValue();
        assertThat(dto.getType()).isEqualTo("animal");
        assertThat(dto.getModelPath()).isEqualTo("n:models/animals/wolf.glb");
        assertThat(dto.getModelModifierMapping()).containsEntry("bodyColor", "dark");
    }
}
