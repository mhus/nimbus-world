package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.assets.AssetImageGeneratorExecutor;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.ItemTier;
import de.mhus.nimbus.world.shared.world.RarityCategory;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for Phase 4 item generation. The AI image executor is mocked (no network). */
class RealityItemGeneratorTest {

    private final WItemService itemService = mock(WItemService.class);
    private final AssetImageGeneratorExecutor imageGenerator = mock(AssetImageGeneratorExecutor.class);
    private final RealityItemGenerator generator = new RealityItemGenerator(itemService, imageGenerator);

    private final WorldId worldId = WorldId.of("earth616:westview").orElseThrow();

    private RealityPlan.ItemSpec item(String name, String type, String tier, String rarity, String desc, Integer price) {
        RealityPlan.ItemSpec s = new RealityPlan.ItemSpec();
        s.setName(name);
        s.setType(type);
        s.setTier(tier);
        s.setRarity(rarity);
        s.setDescription(desc);
        s.setPriceHint(price);
        return s;
    }

    private RealityPlan planWith(List<RealityPlan.ItemSpec> items, Integer maxItems) {
        RealityPlan plan = new RealityPlan();
        RealityPlan.StyleGuide style = new RealityPlan.StyleGuide();
        style.setIconSize(64);
        style.setTransparentBackground(true);
        style.setPromptPrefix("hand-painted fantasy icon");
        style.setPromptNegative("no text, no frame");
        plan.setStyle(style);
        if (maxItems != null) {
            RealityPlan.Meta meta = new RealityPlan.Meta();
            RealityPlan.GenerationControls c = new RealityPlan.GenerationControls();
            c.setMaxItems(maxItems);
            meta.setControls(c);
            plan.setMeta(meta);
        }
        plan.setItems(items);
        return plan;
    }

    private void stubHappyPath() throws Exception {
        when(itemService.save(any(), anyString(), any())).thenAnswer(inv ->
                WItem.builder().name(inv.getArgument(1)).publicData(inv.getArgument(2)).build());
        when(imageGenerator.execute(any())).thenReturn(JobExecutor.JobResult.success());
    }

    @Test
    void createsItemsWithTexturePathAndTransparentIcon() throws Exception {
        stubHappyPath();
        RealityPlan plan = planWith(List.of(
                item("Peat Spade", "tool", "IRON", "COMMON", "a wide peat spade", 20),
                item("Mistglass Shard", "material", null, "RARE", "a shimmering fae-glass shard", null)
        ), null);

        RealityItemResult result = generator.generateItems(worldId, plan);

        assertThat(result.getItemsCreated()).isEqualTo(2);
        assertThat(result.getIconsGenerated()).isEqualTo(2);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getCreatedItemIds()).containsExactly("peat_spade", "mistglass_shard");

        // Item DTOs saved with slug name, type and deterministic texture path.
        ArgumentCaptor<Item> itemCap = ArgumentCaptor.forClass(Item.class);
        verify(itemService, times(2)).save(any(), anyString(), itemCap.capture());
        Item first = itemCap.getAllValues().get(0);
        assertThat(first.getName()).isEqualTo("peat_spade");
        assertThat(first.getType()).isEqualTo("tool");
        assertThat(first.getItemType()).isEqualTo("tool");
        assertThat(first.getTitle()).isEqualTo("Peat Spade");
        assertThat(first.getTexture()).isEqualTo("textures/items/peat_spade.png");

        // Trading fields mapped and persisted.
        ArgumentCaptor<WItem> entityCap = ArgumentCaptor.forClass(WItem.class);
        verify(itemService, times(2)).saveEntity(entityCap.capture());
        WItem spade = entityCap.getAllValues().get(0);
        assertThat(spade.getItemTier()).isEqualTo(ItemTier.IRON);
        assertThat(spade.getRarityCategory()).isEqualTo(RarityCategory.COMMON);
        assertThat(spade.getBasePrice()).isEqualTo(20.0);
        WItem shard = entityCap.getAllValues().get(1);
        assertThat(shard.getItemTier()).isEqualTo(ItemTier.NONE);            // null tier -> NONE
        assertThat(shard.getRarityCategory()).isEqualTo(RarityCategory.RARE);
        assertThat(shard.getBasePrice()).isNull();                            // no priceHint

        // Image job asks for a transparent icon at the item's texture path.
        ArgumentCaptor<WJob> jobCap = ArgumentCaptor.forClass(WJob.class);
        verify(imageGenerator, times(2)).execute(jobCap.capture());
        WJob job = jobCap.getAllValues().get(0);
        assertThat(job.getWorldId()).isEqualTo("earth616:westview");
        assertThat(job.getParameters())
                .containsEntry("path", "textures/items/peat_spade.png")
                .containsEntry("transparent", "true")
                .containsEntry("size", "64x64")
                .containsEntry("overwrite", "true");
        assertThat(job.getParameters().get("prompt")).contains("hand-painted fantasy icon");
        assertThat(job.getParameters().get("prompt")).contains("a wide peat spade");
    }

    @Test
    void respectsMaxItemsLimit() throws Exception {
        stubHappyPath();
        RealityPlan plan = planWith(List.of(
                item("A", "material", null, null, "a", null),
                item("B", "material", null, null, "b", null),
                item("C", "material", null, null, "c", null)
        ), 1);

        RealityItemResult result = generator.generateItems(worldId, plan);

        assertThat(result.getItemsCreated()).isEqualTo(1);
        verify(itemService, times(1)).save(any(), anyString(), any());
    }

    @Test
    void recordsIconFailureButKeepsItem() throws Exception {
        when(itemService.save(any(), anyString(), any())).thenAnswer(inv ->
                WItem.builder().name(inv.getArgument(1)).publicData(inv.getArgument(2)).build());
        when(imageGenerator.execute(any())).thenReturn(JobExecutor.JobResult.failure("boom"));

        RealityPlan plan = planWith(List.of(item("Peat Brick", "material", null, "COMMON", "fuel", null)), null);
        RealityItemResult result = generator.generateItems(worldId, plan);

        assertThat(result.getCreatedItemIds()).containsExactly("peat_brick"); // item still created
        assertThat(result.getIconsGenerated()).isZero();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("peat_brick");
    }

    @Test
    void resolvesTierFromItemClassAndMapsSuperItem() throws Exception {
        stubHappyPath();

        RealityPlan plan = new RealityPlan();
        RealityPlan.ItemClass iron = new RealityPlan.ItemClass();
        iron.setName("iron");
        iron.setTier("IRON");
        plan.setItemClasses(List.of(iron));

        RealityPlan.ItemSpec sword = item("Iron Sword", "weapon", null, "UNCOMMON", "a sturdy iron sword", 30);
        sword.setItemClass("iron"); // no explicit tier -> resolved from class

        RealityPlan.ItemSpec oneUpForever = item("One-Up Forever", "super", null, "MYTHIC", "a glowing eternal heart", null);
        oneUpForever.setConsumable(false);
        oneUpForever.setPersistent(true);
        oneUpForever.setEffect("extra_life");

        plan.setItems(List.of(sword, oneUpForever));

        RealityItemResult result = generator.generateItems(worldId, plan);
        assertThat(result.getItemsCreated()).isEqualTo(2);

        // Tier resolved from the item class (no explicit tier on the item).
        ArgumentCaptor<WItem> entityCap = ArgumentCaptor.forClass(WItem.class);
        verify(itemService, times(2)).saveEntity(entityCap.capture());
        assertThat(entityCap.getAllValues().get(0).getItemTier()).isEqualTo(ItemTier.IRON);

        // The persistent super-item becomes exclusive and carries its mechanics in parameters.
        ArgumentCaptor<Item> itemCap = ArgumentCaptor.forClass(Item.class);
        verify(itemService, times(2)).save(any(), anyString(), itemCap.capture());
        Item superDto = itemCap.getAllValues().get(1);
        assertThat(superDto.getExclusive()).isTrue();
        assertThat(superDto.getParameters())
                .containsEntry("consumable", "false")
                .containsEntry("persistent", "true")
                .containsEntry("effect", "extra_life");
    }

    @Test
    void resolvesItemClassByTitleNotOnlyName() {
        RealityPlan.ItemClass bogIron = new RealityPlan.ItemClass();
        bogIron.setName("bog_iron");
        bogIron.setTitle("Bog Iron");
        bogIron.setTier("IRON");
        var classes = RealityItemGenerator.indexClasses(List.of(bogIron));

        RealityPlan.ItemSpec byTitle = item("Bog-Iron Spear", "weapon", null, "COMMON", "a spear", null);
        byTitle.setItemClass("Bog Iron"); // references the display title
        RealityPlan.ItemSpec byName = item("Bog-Iron Helm", "armor", null, "COMMON", "a helm", null);
        byName.setItemClass("bog_iron"); // references the canonical name

        assertThat(generator.resolveTier(byTitle, classes)).isEqualTo(ItemTier.IRON);
        assertThat(generator.resolveTier(byName, classes)).isEqualTo(ItemTier.IRON);
    }

    @Test
    void handlesEmptyPlan() {
        RealityItemResult result = generator.generateItems(worldId, new RealityPlan());
        assertThat(result.getItemsCreated()).isZero();
    }

    @Test
    void slugAndEnumMapping() {
        assertThat(RealityItemGenerator.slug("Peat Spade")).isEqualTo("peat_spade");
        assertThat(RealityItemGenerator.slug("Warden's Oil-coat!")).isEqualTo("warden_s_oil_coat");
        assertThat(RealityItemGenerator.slug(null)).isEmpty();

        assertThat(RealityItemGenerator.mapTier("iron")).isEqualTo(ItemTier.IRON);
        assertThat(RealityItemGenerator.mapTier("fairy-forged")).isEqualTo(ItemTier.NONE); // unknown -> NONE
        assertThat(RealityItemGenerator.mapRarity("LEGENDARY")).isEqualTo(RarityCategory.LEGENDARY);
        assertThat(RealityItemGenerator.mapRarity(null)).isEqualTo(RarityCategory.COMMON);
    }
}
