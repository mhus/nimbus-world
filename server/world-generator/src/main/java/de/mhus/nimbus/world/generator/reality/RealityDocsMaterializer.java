package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Stage D — deterministic (no-AI) documents that capture the design intent:
 * <ul>
 *   <li><b>Design rationale</b> ({@code reality_design}) — "what we were thinking": the item catalog
 *       with classes/relations (source/recipe/purpose), super-items, creatures and rules.</li>
 *   <li><b>World directives</b> ({@code reality_world_directives}) — the direction + background
 *       power(s) + rules for how worlds extend the canon + world-template hooks. This is what the
 *       World Generator (Genesis) reads (stored as a WDocument, like Genesis' generator_instructions).</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityDocsMaterializer {

    public static final String DESIGN_COLLECTION = "reality_design";
    public static final String DIRECTIVES_COLLECTION = "reality_world_directives";

    private final WDocumentService documentService;

    public MaterializeResult materialize(WorldId region, RealityPlan plan) {
        MaterializeResult result = MaterializeResult.builder().build();
        try {
            save(region, DESIGN_COLLECTION, "reality-design", "Reality Design", renderDesign(plan), "reality_design");
            result.inc();
        } catch (Exception e) {
            log.warn("Failed to write reality_design", e);
            result.addError("design: " + e.getMessage());
        }
        try {
            save(region, DIRECTIVES_COLLECTION, "world-directives", "World Directives",
                    renderDirectives(region, plan), "reality_world_directives");
            result.inc();
        } catch (Exception e) {
            log.warn("Failed to write reality_world_directives", e);
            result.addError("directives: " + e.getMessage());
        }
        return result;
    }

    // ---- rendering ----

    String renderDesign(RealityPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Reality Design — ").append(regionTitle(plan)).append("\n\n");
        sb.append("> Auto-generated design rationale: the item web, classes and relations behind this region.\n\n");

        if (plan.getVision() != null) {
            sb.append("## Vision\n").append(plan.getVision()).append("\n\n");
        }
        appendDirection(sb, plan);

        if (notEmpty(plan.getItemClasses())) {
            sb.append("## Item classes (progression ladder)\n\n");
            sb.append("| rank | name | title | tier | material |\n|---|---|---|---|---|\n");
            for (RealityPlan.ItemClass c : plan.getItemClasses()) {
                if (c == null) continue;
                sb.append("| ").append(nz(c.getRank())).append(" | ").append(nz(c.getName()))
                        .append(" | ").append(nz(c.getTitle())).append(" | ").append(nz(c.getTier()))
                        .append(" | ").append(nz(c.getMaterial())).append(" |\n");
            }
            sb.append('\n');
        }

        appendItemTable(sb, "Item catalog", plan.getItems());
        appendItemTable(sb, "Special / super items", plan.getSpecialItems());

        if (notEmpty(plan.getCreatures())) {
            sb.append("## Creatures\n\n");
            for (RealityPlan.CreatureSpec c : plan.getCreatures()) {
                if (c == null) continue;
                sb.append("- **").append(nz(c.getName())).append("** (").append(nz(c.getType()))
                        .append(") — model `").append(nz(c.getModelPath())).append("`; ")
                        .append(nz(c.getBehavior())).append('\n');
            }
            sb.append('\n');
        }

        if (notEmpty(plan.getRules())) {
            sb.append("## Rules\n\n");
            for (RealityPlan.RuleSpec r : plan.getRules()) {
                if (r == null) continue;
                sb.append("- **").append(nz(r.getName())).append("** [").append(nz(r.getKind())).append("] — when `")
                        .append(nz(r.getWhen())).append("` → ").append(String.join(", ", nzList(r.getEffects())))
                        .append("\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    String renderDirectives(WorldId region, RealityPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# World Directives — ").append(regionTitle(plan)).append("\n\n");
        sb.append("> Canon for the World Generator (Genesis). Worlds build on this base and extend it by ")
                .append("**one aspect at a time** — advance a background power's status/manifestation, add ")
                .append("reacting factions or places — but must NOT contradict the base lore. The system is ")
                .append("open: never define a fixed, finite set of worlds.\n\n");
        sb.append("Region: `").append(region.getId()).append("`. Base lore lives in the `lore` collection.\n\n");

        appendDirection(sb, plan);

        if (notEmpty(plan.getBackgroundPowers())) {
            sb.append("## Background powers (drive the tension)\n\n");
            for (RealityPlan.BackgroundPower p : plan.getBackgroundPowers()) {
                if (p == null) continue;
                sb.append("### ").append(nz(p.getName())).append('\n');
                sb.append("- influence: ").append(nz(p.getInfluence()))
                        .append(" · visibility: ").append(nz(p.getVisibility()))
                        .append(" · status: ").append(nz(p.getStatus())).append('\n');
                if (!Strings.isBlank(p.getNature())) sb.append("- nature: ").append(p.getNature()).append('\n');
                if (!Strings.isBlank(p.getGoal())) sb.append("- goal: ").append(p.getGoal()).append('\n');
                if (notEmpty(p.getManifestations())) {
                    sb.append("- manifestations: ").append(String.join("; ", p.getManifestations())).append('\n');
                }
                if (notEmpty(p.getOpposedBy())) {
                    sb.append("- opposed by: ").append(String.join(", ", p.getOpposedBy())).append('\n');
                }
                sb.append('\n');
            }
        }

        if (notEmpty(plan.getWorldTemplates())) {
            sb.append("## World templates (starting hooks)\n\n");
            for (RealityPlan.WorldTemplate w : plan.getWorldTemplates()) {
                if (w == null) continue;
                sb.append("- **").append(nz(w.getName())).append("** — ").append(nz(w.getSummary()))
                        .append(" (biome: ").append(nz(w.getBiomeFocus())).append(", danger: ")
                        .append(nz(w.getDanger())).append(")\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void appendDirection(StringBuilder sb, RealityPlan plan) {
        if (plan.getDirection() != null && !Strings.isBlank(plan.getDirection().getPremise())) {
            sb.append("## Direction\n").append(plan.getDirection().getPremise());
            if (!Strings.isBlank(plan.getDirection().getTone())) {
                sb.append("\n\n_Tone: ").append(plan.getDirection().getTone()).append("_");
            }
            sb.append("\n\n");
        }
    }

    private void appendItemTable(StringBuilder sb, String heading, List<RealityPlan.ItemSpec> items) {
        if (!notEmpty(items)) {
            return;
        }
        sb.append("## ").append(heading).append("\n\n");
        sb.append("| name | type | class/tier | rarity | source | recipe | purpose |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (RealityPlan.ItemSpec it : items) {
            if (it == null) continue;
            String classTier = !Strings.isBlank(it.getItemClass()) ? it.getItemClass() : nz(it.getTier());
            sb.append("| ").append(nz(it.getName()))
                    .append(" | ").append(nz(it.getType()))
                    .append(" | ").append(classTier)
                    .append(" | ").append(nz(it.getRarity()))
                    .append(" | ").append(nz(it.getSource()))
                    .append(" | ").append(String.join(", ", nzList(it.getRecipe())))
                    .append(" | ").append(nz(it.getDescription()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private void save(WorldId region, String collection, String name, String title, String content, String type) {
        documentService.save(region, collection, UUID.randomUUID().toString(), doc -> {
            doc.setName(name);
            doc.setTitle(title);
            doc.setContent(content);
            doc.setFormat("markdown");
            doc.setType(type);
            doc.setCollection(collection);
        });
    }

    private static String regionTitle(RealityPlan plan) {
        if (plan.getMeta() != null && !Strings.isBlank(plan.getMeta().getTitle())) {
            return plan.getMeta().getTitle();
        }
        if (plan.getMeta() != null && !Strings.isBlank(plan.getMeta().getRegionId())) {
            return plan.getMeta().getRegionId();
        }
        return "Region";
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private static List<String> nzList(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static String nz(Object o) {
        return o == null ? "" : o.toString();
    }
}
