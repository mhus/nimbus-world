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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 4 of the Reality Workflow: create the item entities of a region from a parsed
 * {@link RealityPlan} and generate a transparent icon for each one.
 * <p>
 * For every {@link RealityPlan.ItemSpec} this:
 * <ol>
 *     <li>creates/updates a {@link WItem} (region-scoped) with a deterministic {@code texture} path;</li>
 *     <li>fills the server-side trading fields (tier / rarity / base price) from the spec;</li>
 *     <li>generates the icon PNG at that path via the {@code asset-image-generator} executor with
 *         {@code transparent=true} (real alpha — see the transparent-image implementation).</li>
 * </ol>
 * The icon is generated synchronously through the executor (this phase itself runs as a background
 * job/workflow step). A failing icon is recorded but does not abort the run, and the item still
 * references its expected texture path.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityItemGenerator {

    private static final String ITEM_TEXTURE_DIR = "textures/items/";
    private static final int DEFAULT_ICON_SIZE = 64;

    private final WItemService itemService;
    private final AssetImageGeneratorExecutor imageGenerator;

    /**
     * Generate all regular items of the plan (not {@code specialItems}, which are on-demand).
     *
     * @param worldId a world or region id; items are stored region-scoped by {@link WItemService}
     * @param plan    the parsed reality plan
     * @return a summary of created items, generated icons and errors
     */
    public RealityItemResult generateItems(WorldId worldId, RealityPlan plan) {
        RealityItemResult result = RealityItemResult.builder().build();

        List<RealityPlan.ItemSpec> specs = plan.getItems();
        if (specs == null || specs.isEmpty()) {
            log.info("RealityItemGenerator: plan has no items");
            return result;
        }

        int limit = maxItems(plan);
        RealityPlan.StyleGuide style = plan.getStyle();
        String imageModel = imageModel(plan);
        Map<String, RealityPlan.ItemClass> classes = indexClasses(plan.getItemClasses());

        int count = 0;
        for (RealityPlan.ItemSpec spec : specs) {
            if (count >= limit) {
                log.info("RealityItemGenerator: reached maxItems limit {} - skipping remaining {} items",
                        limit, specs.size() - count);
                break;
            }
            count++;

            String itemId = slug(spec.getName());
            if (itemId.isEmpty()) {
                result.addError("Skipped item with blank name");
                continue;
            }
            String texturePath = ITEM_TEXTURE_DIR + itemId + ".png";

            try {
                createItem(worldId, itemId, texturePath, spec, classes);
                result.getCreatedItemIds().add(itemId);
            } catch (Exception e) {
                log.warn("Failed to create item '{}'", itemId, e);
                result.addError("Item '" + itemId + "': " + e.getMessage());
                continue;
            }

            try {
                if (generateIcon(worldId, style, imageModel, spec, texturePath)) {
                    result.setIconsGenerated(result.getIconsGenerated() + 1);
                }
            } catch (Exception e) {
                log.warn("Failed to generate icon for item '{}'", itemId, e);
                result.addError("Icon '" + itemId + "': " + e.getMessage());
            }
        }

        log.info("RealityItemGenerator: created {} items, generated {} icons, {} errors",
                result.getItemsCreated(), result.getIconsGenerated(), result.getErrors().size());
        return result;
    }

    /** Create/update the WItem and its trading fields. */
    private void createItem(WorldId worldId, String itemId, String texturePath, RealityPlan.ItemSpec spec,
                            Map<String, RealityPlan.ItemClass> classes) {
        // Super-item / class metadata carried on the DTO parameters map.
        Map<String, String> params = new HashMap<>();
        putIfSet(params, "itemClass", spec.getItemClass());
        putIfSet(params, "effect", spec.getEffect());
        if (spec.getConsumable() != null) {
            params.put("consumable", String.valueOf(spec.getConsumable()));
        }
        if (spec.getPersistent() != null) {
            params.put("persistent", String.valueOf(spec.getPersistent()));
        }

        Item dto = Item.builder()
                .name(itemId)
                .itemType(spec.getType())
                .type(spec.getType())
                .title(Strings.isBlank(spec.getName()) ? itemId : spec.getName())
                .description(spec.getDescription())
                .texture(texturePath)
                // A persistent super-item (e.g. "one-up forever") is never consumed -> exclusive.
                .exclusive(Boolean.TRUE.equals(spec.getPersistent()) ? Boolean.TRUE : null)
                .parameters(params.isEmpty() ? null : params)
                .build();

        // Trading fields live on the WItem entity (not the public Item DTO), so set them via the
        // customizer overload -> a single persist instead of save + saveEntity.
        itemService.save(worldId, itemId, dto, saved -> {
            saved.setItemTier(resolveTier(spec, classes));
            saved.setRarityCategory(mapRarity(spec.getRarity()));
            if (spec.getPriceHint() != null) {
                saved.setBasePrice(spec.getPriceHint().doubleValue());
            }
        });
    }

    private static void putIfSet(Map<String, String> map, String key, String value) {
        if (!Strings.isBlank(value)) {
            map.put(key, value);
        }
    }

    /**
     * Index item classes by BOTH canonical name and (themed) title, lowercased. AI output sometimes
     * references a class by its display title instead of its name, so we accept either.
     */
    static Map<String, RealityPlan.ItemClass> indexClasses(List<RealityPlan.ItemClass> classes) {
        Map<String, RealityPlan.ItemClass> map = new HashMap<>();
        if (classes != null) {
            for (RealityPlan.ItemClass c : classes) {
                if (c == null) {
                    continue;
                }
                if (!Strings.isBlank(c.getName())) {
                    map.putIfAbsent(c.getName().trim().toLowerCase(Locale.ROOT), c);
                }
                if (!Strings.isBlank(c.getTitle())) {
                    map.putIfAbsent(c.getTitle().trim().toLowerCase(Locale.ROOT), c);
                }
            }
        }
        return map;
    }

    /** Resolve the tier: explicit spec tier wins, else the item class' tier, else NONE. */
    ItemTier resolveTier(RealityPlan.ItemSpec spec, Map<String, RealityPlan.ItemClass> classes) {
        if (!Strings.isBlank(spec.getTier())) {
            return mapTier(spec.getTier());
        }
        if (!Strings.isBlank(spec.getItemClass()) && classes != null) {
            RealityPlan.ItemClass c = classes.get(spec.getItemClass().trim().toLowerCase(Locale.ROOT));
            if (c != null && !Strings.isBlank(c.getTier())) {
                return mapTier(c.getTier());
            }
        }
        return ItemTier.NONE;
    }

    /** Generate the icon PNG at {@code texturePath} using the asset-image-generator executor. */
    private boolean generateIcon(WorldId worldId, RealityPlan.StyleGuide style, String imageModel,
                                 RealityPlan.ItemSpec spec, String texturePath) throws Exception {
        int size = DEFAULT_ICON_SIZE;
        boolean transparent = true;
        if (style != null) {
            if (style.getIconSize() != null && style.getIconSize() > 0) {
                size = style.getIconSize();
            }
            if (style.getTransparentBackground() != null) {
                transparent = style.getTransparentBackground();
            }
        }

        Map<String, String> params = new HashMap<>();
        params.put("prompt", buildPrompt(style, spec));
        params.put("path", texturePath);
        params.put("size", size + "x" + size);
        params.put("transparent", String.valueOf(transparent));
        params.put("overwrite", "true"); // deterministic path -> item.texture stays valid
        if (!Strings.isBlank(imageModel)) {
            params.put("model", imageModel);
        }

        WJob job = WJob.builder()
                .worldId(worldId.getId())
                .executor(imageGenerator.getExecutorName())
                .title("Reality icon: " + texturePath)
                .parameters(params)
                .build();

        JobExecutor.JobResult r = imageGenerator.execute(job);
        if (r == null || !r.successful()) {
            String msg = r != null ? r.errorMessage() : "no result";
            throw new IllegalStateException("image generation failed: " + msg);
        }
        return true;
    }

    /** Compose the image prompt from the region style guide and the item description. */
    String buildPrompt(RealityPlan.StyleGuide style, RealityPlan.ItemSpec spec) {
        String subject = Strings.isBlank(spec.getDescription()) ? spec.getName() : spec.getDescription();
        StringBuilder sb = new StringBuilder();
        if (style != null && !Strings.isBlank(style.getPromptPrefix())) {
            sb.append(style.getPromptPrefix().trim());
            if (!sb.toString().endsWith(".")) {
                sb.append('.');
            }
            sb.append(' ');
        }
        sb.append(subject == null ? "" : subject.trim());
        if (style != null && !Strings.isBlank(style.getPromptNegative())) {
            sb.append(". Avoid: ").append(style.getPromptNegative().trim());
        }
        return sb.toString();
    }

    private int maxItems(RealityPlan plan) {
        if (plan.getMeta() != null && plan.getMeta().getControls() != null) {
            Integer max = plan.getMeta().getControls().getMaxItems();
            if (max != null && max > 0) {
                return max;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String imageModel(RealityPlan plan) {
        if (plan.getMeta() != null && plan.getMeta().getControls() != null) {
            return plan.getMeta().getControls().getImageModel();
        }
        return null;
    }

    /** Slugify a display name into a technical itemId: lowercase, non-alphanumeric -> underscore. */
    static String slug(String name) {
        if (name == null) {
            return "";
        }
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return s.replaceAll("^_+|_+$", "");
    }

    static ItemTier mapTier(String tier) {
        if (Strings.isBlank(tier)) {
            return ItemTier.NONE;
        }
        try {
            return ItemTier.valueOf(tier.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ItemTier.NONE;
        }
    }

    static RarityCategory mapRarity(String rarity) {
        if (Strings.isBlank(rarity)) {
            return RarityCategory.COMMON;
        }
        try {
            return RarityCategory.valueOf(rarity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RarityCategory.COMMON;
        }
    }
}
