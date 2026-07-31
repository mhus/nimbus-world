package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.shared.world.ItemTier;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * C1 — mechanical validator of a {@link RealityPlan}. Deterministic and side-effect free (no DB, no
 * network): a pure function {@code RealityPlan -> ValidationReport} that enforces the world-logic
 * coherence invariants (see {@code preset-catalog.md} §3/§4) before anything is written to the DB.
 * <p>
 * ERRORs block materialization (structural breakage). WARNINGs are tolerated (the tolerance model)
 * and only reported — the AI judge (C2) handles the finer balance assessment.
 */
@Service
@Slf4j
public class RealityValidator {

    /** Categories that are expected to have a tier (via explicit tier or item class). */
    private static final Set<String> TIERED_CATEGORIES = Set.of("weapon", "armor", "tool");

    /**
     * A lower-rarity item in the same category costing more than a higher-rarity one by more than
     * this factor is flagged as a price/rarity inversion (deterministic balance smell, tolerated).
     */
    private static final double PRICE_INVERSION_TOLERANCE = 0.5;

    public ValidationReport validate(RealityPlan plan) {
        ValidationReport report = new ValidationReport();
        if (plan == null) {
            report.error("null_plan", "RealityPlan is null", null);
            return report;
        }

        Map<String, RealityPlan.ItemClass> classes = validateClasses(plan, report);

        List<RealityPlan.ItemSpec> items = allItems(plan);
        Set<String> slugs = collectSlugs(items, report);

        for (RealityPlan.ItemSpec it : items) {
            if (it == null || Strings.isBlank(it.getName())) {
                continue;
            }
            validateItem(it, classes, slugs, report);
        }

        validatePriceMonotonicity(items, report);

        log.debug("RealityValidator: {}", report.summary());
        return report;
    }

    // ---- item classes ----

    private Map<String, RealityPlan.ItemClass> validateClasses(RealityPlan plan, ValidationReport report) {
        // Lookup map keyed by BOTH name and title (AI may reference either).
        Map<String, RealityPlan.ItemClass> classes = new HashMap<>();
        Set<String> identities = new HashSet<>();
        Map<Integer, String> ranks = new HashMap<>();
        if (plan.getItemClasses() == null) {
            return classes;
        }
        for (RealityPlan.ItemClass c : plan.getItemClasses()) {
            if (c == null) {
                continue;
            }
            String name = Strings.isBlank(c.getName()) ? null : c.getName().trim().toLowerCase(Locale.ROOT);
            String title = Strings.isBlank(c.getTitle()) ? null : c.getTitle().trim().toLowerCase(Locale.ROOT);
            String identity = name != null ? name : title;
            if (identity == null) {
                report.error("class_no_name", "Item class without a name or title", null);
                continue;
            }
            if (!identities.add(identity)) {
                report.error("duplicate_item_class", "Duplicate item class '" + identity + "'", identity);
            }
            if (name != null) {
                classes.put(name, c);
            }
            if (title != null) {
                classes.put(title, c);
            }

            if (!Strings.isBlank(c.getTier()) && !isValidTier(c.getTier())) {
                report.warning("bad_class_tier",
                        "Item class '" + identity + "' has unknown tier '" + c.getTier() + "'", identity);
            }
            if (c.getRank() != null) {
                String prev = ranks.put(c.getRank(), identity);
                if (prev != null) {
                    report.warning("duplicate_class_rank",
                            "Item classes '" + prev + "' and '" + identity + "' share rank " + c.getRank(), identity);
                }
            }
        }
        return classes;
    }

    // ---- items ----

    private void validateItem(RealityPlan.ItemSpec it, Map<String, RealityPlan.ItemClass> classes,
                              Set<String> slugs, ValidationReport report) {
        String slug = RealityItemGenerator.slug(it.getName());
        String type = it.getType() == null ? "" : it.getType().trim().toLowerCase(Locale.ROOT);

        // item class reference must resolve
        boolean classResolves = false;
        if (!Strings.isBlank(it.getItemClass())) {
            String cn = it.getItemClass().trim().toLowerCase(Locale.ROOT);
            RealityPlan.ItemClass c = classes.get(cn);
            if (c == null) {
                report.error("unknown_item_class",
                        "Item '" + slug + "' references unknown item class '" + it.getItemClass() + "'", slug);
            } else {
                classResolves = !Strings.isBlank(c.getTier());
            }
        }

        // tiered gear must resolve a tier
        if (TIERED_CATEGORIES.contains(type)) {
            boolean hasTier = !Strings.isBlank(it.getTier()) || classResolves;
            if (!hasTier) {
                report.warning("untiered_gear",
                        "Tiered item '" + slug + "' (" + type + ") has neither tier nor a tier-bearing item class", slug);
            }
        }
        if (!Strings.isBlank(it.getTier()) && !isValidTier(it.getTier())) {
            report.warning("bad_item_tier",
                    "Item '" + slug + "' has unknown tier '" + it.getTier() + "'", slug);
        }

        // every material needs a source
        if ("material".equals(type) && Strings.isBlank(it.getSource())) {
            report.warning("material_no_source", "Material '" + slug + "' has no source", slug);
        }

        // recipe ingredients must reference existing items
        if (it.getRecipe() != null) {
            for (String ingredient : it.getRecipe()) {
                String ingSlug = RealityItemGenerator.slug(ingredient);
                if (!ingSlug.isEmpty() && !slugs.contains(ingSlug)) {
                    report.warning("unknown_recipe_ref",
                            "Item '" + slug + "' recipe references unknown item '" + ingredient + "'", slug);
                }
            }
        }

        // super-item mechanics
        boolean persistent = Boolean.TRUE.equals(it.getPersistent());
        boolean consumable = Boolean.TRUE.equals(it.getConsumable());
        if (persistent && consumable) {
            report.error("super_contradiction",
                    "Item '" + slug + "' is both persistent and consumable", slug);
        }
        if ("super".equals(type)) {
            if (Strings.isBlank(it.getEffect())) {
                report.warning("super_no_effect", "Super item '" + slug + "' has no effect", slug);
            }
            if (it.getPersistent() == null && it.getConsumable() == null) {
                report.warning("super_no_mechanic",
                        "Super item '" + slug + "' sets neither consumable nor persistent", slug);
            }
        }

        // price sanity
        if (it.getPriceHint() != null && it.getPriceHint() <= 0) {
            report.warning("nonpositive_price",
                    "Item '" + slug + "' has non-positive priceHint " + it.getPriceHint(), slug);
        }
    }

    /** Flag lower-rarity items that cost drastically more than a higher-rarity item of the same type. */
    private void validatePriceMonotonicity(List<RealityPlan.ItemSpec> items, ValidationReport report) {
        Map<String, List<RealityPlan.ItemSpec>> byType = new HashMap<>();
        for (RealityPlan.ItemSpec it : items) {
            if (it == null || it.getPriceHint() == null || Strings.isBlank(it.getType())
                    || rarityOrdinal(it.getRarity()) < 0) {
                continue;
            }
            byType.computeIfAbsent(it.getType().trim().toLowerCase(Locale.ROOT), k -> new java.util.ArrayList<>()).add(it);
        }
        for (List<RealityPlan.ItemSpec> group : byType.values()) {
            for (RealityPlan.ItemSpec low : group) {
                for (RealityPlan.ItemSpec high : group) {
                    if (rarityOrdinal(low.getRarity()) < rarityOrdinal(high.getRarity())
                            && low.getPriceHint() > high.getPriceHint() * (1 + PRICE_INVERSION_TOLERANCE)) {
                        report.warning("price_rarity_inversion",
                                "Lower-rarity '" + RealityItemGenerator.slug(low.getName()) + "' (" + low.getRarity()
                                        + ", " + low.getPriceHint() + ") costs far more than higher-rarity '"
                                        + RealityItemGenerator.slug(high.getName()) + "' (" + high.getRarity()
                                        + ", " + high.getPriceHint() + ")",
                                RealityItemGenerator.slug(low.getName()));
                    }
                }
            }
        }
    }

    // ---- helpers ----

    private List<RealityPlan.ItemSpec> allItems(RealityPlan plan) {
        List<RealityPlan.ItemSpec> items = new java.util.ArrayList<>();
        if (plan.getItems() != null) {
            items.addAll(plan.getItems());
        }
        if (plan.getSpecialItems() != null) {
            items.addAll(plan.getSpecialItems());
        }
        return items;
    }

    private Set<String> collectSlugs(List<RealityPlan.ItemSpec> items, ValidationReport report) {
        Set<String> slugs = new HashSet<>();
        for (RealityPlan.ItemSpec it : items) {
            if (it == null) {
                continue;
            }
            String slug = RealityItemGenerator.slug(it.getName());
            if (slug.isEmpty()) {
                report.error("item_no_name", "Item without a usable name", null);
                continue;
            }
            if (!slugs.add(slug)) {
                report.error("duplicate_item", "Duplicate item id '" + slug + "' (" + it.getName() + ")", slug);
            }
        }
        return slugs;
    }

    private static boolean isValidTier(String tier) {
        try {
            ItemTier.valueOf(tier.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** COMMON=0 … MYTHIC=5; -1 for null/unknown. */
    private static int rarityOrdinal(String rarity) {
        if (Strings.isBlank(rarity)) {
            return -1;
        }
        return switch (rarity.trim().toUpperCase(Locale.ROOT)) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> -1;
        };
    }
}
