package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * Stage D1 — materialize lore, factions and NPC archetypes as {@code WDocument}s in the region.
 * Lore entries go to collections {@code lore}/{@code factions}/{@code quests} (by kind); NPC
 * archetypes to {@code npcs}. {@code WDocumentService.save} de-duplicates by name, so re-runs update
 * the existing document instead of piling up duplicates.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityLoreMaterializer {

    private final WDocumentService documentService;

    public MaterializeResult materialize(WorldId region, RealityPlan plan) {
        MaterializeResult result = MaterializeResult.builder().build();

        if (plan.getLore() != null) {
            for (RealityPlan.LoreEntry e : plan.getLore()) {
                if (e == null || (Strings.isBlank(e.getTitle()) && Strings.isBlank(e.getContent()))) {
                    continue;
                }
                String title = Strings.isBlank(e.getTitle()) ? "lore" : e.getTitle();
                String collection = loreCollection(e.getKind());
                try {
                    saveDoc(region, collection, slug(title), title, e.getContent(),
                            Strings.isBlank(e.getKind()) ? "lore" : e.getKind());
                    result.inc();
                } catch (Exception ex) {
                    log.warn("Failed to materialize lore '{}'", title, ex);
                    result.addError("lore '" + title + "': " + ex.getMessage());
                }
            }
        }

        if (plan.getNpcs() != null) {
            for (RealityPlan.NpcSpec n : plan.getNpcs()) {
                if (n == null || Strings.isBlank(n.getRole())) {
                    continue;
                }
                String content = "# " + n.getRole() + "\n\n"
                        + "- Faction: " + nz(n.getFaction()) + "\n"
                        + "- Tone: " + nz(n.getTone()) + "\n";
                try {
                    saveDoc(region, "npcs", slug(n.getRole()), n.getRole(), content, "npc");
                    result.inc();
                } catch (Exception ex) {
                    log.warn("Failed to materialize npc '{}'", n.getRole(), ex);
                    result.addError("npc '" + n.getRole() + "': " + ex.getMessage());
                }
            }
        }

        log.info("RealityLoreMaterializer: {} documents, {} errors", result.getCreated(), result.getErrors().size());
        return result;
    }

    private void saveDoc(WorldId region, String collection, String name, String title, String content, String type) {
        documentService.save(region, collection, UUID.randomUUID().toString(), doc -> {
            doc.setName(name);
            doc.setTitle(title);
            doc.setContent(content == null ? "" : content);
            doc.setFormat("markdown");
            doc.setType(type);
            doc.setCollection(collection);
        });
    }

    private static String loreCollection(String kind) {
        if (kind == null) {
            return "lore";
        }
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "faction" -> "factions";
            case "quest" -> "quests";
            default -> "lore";
        };
    }

    private static String slug(String s) {
        return RealityItemGenerator.slug(s);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
