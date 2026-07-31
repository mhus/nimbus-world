package de.mhus.nimbus.world.generator.reality;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline tests for the mechanical validator (C1). */
class RealityValidatorTest {

    private final RealityValidator validator = new RealityValidator();

    private RealityPlan.ItemClass clazz(String name, int rank, String tier) {
        RealityPlan.ItemClass c = new RealityPlan.ItemClass();
        c.setName(name);
        c.setRank(rank);
        c.setTier(tier);
        return c;
    }

    private RealityPlan.ItemSpec item(String name, String type) {
        RealityPlan.ItemSpec s = new RealityPlan.ItemSpec();
        s.setName(name);
        s.setType(type);
        return s;
    }

    private RealityPlan plan(List<RealityPlan.ItemClass> classes, List<RealityPlan.ItemSpec> items) {
        RealityPlan p = new RealityPlan();
        p.setItemClasses(classes);
        p.setItems(items);
        return p;
    }

    private boolean hasCode(ValidationReport r, String code) {
        return r.getIssues().stream().anyMatch(i -> i.getCode().equals(code));
    }

    @Test
    void acceptsACoherentPlan() {
        RealityPlan.ItemSpec ingot = item("Iron Ingot", "material");
        ingot.setSource("iron_ore block");
        RealityPlan.ItemSpec sword = item("Iron Sword", "weapon");
        sword.setItemClass("iron");
        sword.setRecipe(List.of("Iron Ingot"));

        ValidationReport r = validator.validate(plan(
                List.of(clazz("iron", 2, "IRON")),
                List.of(ingot, sword)));

        assertThat(r.isValid()).isTrue();
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void flagsUnknownItemClassAsError() {
        RealityPlan.ItemSpec sword = item("Steel Sword", "weapon");
        sword.setItemClass("steel"); // no such class defined
        ValidationReport r = validator.validate(plan(List.of(clazz("iron", 2, "IRON")), List.of(sword)));

        assertThat(r.hasErrors()).isTrue();
        assertThat(hasCode(r, "unknown_item_class")).isTrue();
    }

    @Test
    void flagsDuplicateItemIdAsError() {
        ValidationReport r = validator.validate(plan(null, List.of(
                item("Peat Brick", "material"),
                item("peat brick", "material")))); // same slug -> peat_brick

        assertThat(r.hasErrors()).isTrue();
        assertThat(hasCode(r, "duplicate_item")).isTrue();
    }

    @Test
    void flagsSuperItemContradictionAsError() {
        RealityPlan.ItemSpec s = item("Broken Charm", "super");
        s.setPersistent(true);
        s.setConsumable(true); // contradiction
        s.setEffect("extra_life");
        ValidationReport r = validator.validate(plan(null, List.of(s)));

        assertThat(hasCode(r, "super_contradiction")).isTrue();
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    void acceptsOneUpForeverAndWarnsOnMissingSourceAndRecipe() {
        RealityPlan.ItemSpec forever = item("One-Up Forever", "super");
        forever.setPersistent(true);
        forever.setConsumable(false);
        forever.setEffect("extra_life");

        RealityPlan.ItemSpec material = item("Bog Iron", "material"); // no source -> warning
        RealityPlan.ItemSpec crafted = item("Fen Sickle", "tool");
        crafted.setTier("IRON");
        crafted.setRecipe(List.of("Ghost Metal")); // unknown -> warning

        ValidationReport r = validator.validate(plan(null, List.of(forever, material, crafted)));

        assertThat(r.isValid()).isTrue();                       // only warnings, no errors
        assertThat(hasCode(r, "material_no_source")).isTrue();
        assertThat(hasCode(r, "unknown_recipe_ref")).isTrue();
        assertThat(hasCode(r, "super_contradiction")).isFalse();
    }

    @Test
    void warnsOnUntieredGear() {
        RealityPlan.ItemSpec sword = item("Plain Sword", "weapon"); // no tier, no class
        ValidationReport r = validator.validate(plan(null, List.of(sword)));

        assertThat(r.isValid()).isTrue();
        assertThat(hasCode(r, "untiered_gear")).isTrue();
    }

    @Test
    void warnsOnPriceRarityInversion() {
        RealityPlan.ItemSpec common = item("Common Blade", "weapon");
        common.setRarity("COMMON");
        common.setPriceHint(500);
        common.setTier("IRON");
        RealityPlan.ItemSpec rare = item("Rare Blade", "weapon");
        rare.setRarity("RARE");
        rare.setPriceHint(50); // far cheaper than the common one
        rare.setTier("IRON");

        ValidationReport r = validator.validate(plan(null, List.of(common, rare)));

        assertThat(hasCode(r, "price_rarity_inversion")).isTrue();
    }

    @Test
    void validatesTheCapturedRealPlanWithoutErrors() throws Exception {
        ClassPathResource resource = new ClassPathResource("reality/sample-plan-duskmoor.json");
        String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        RealityPlan plan = new RealityPlanParser(null, null).parseJson(json).getPlan();

        ValidationReport r = validator.validate(plan);

        // The real AI plan may carry warnings (e.g. missing sources), but must have no hard errors.
        assertThat(r.errors()).as(r.summary()).isEmpty();
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(validator.validate(null).hasErrors()).isTrue();
        assertThat(validator.validate(new RealityPlan()).isValid()).isTrue();
        assertThat(new ArrayList<>()).isEmpty(); // sanity
    }
}
