package de.mhus.nimbus.world.generator.flora;

import de.mhus.nimbus.world.shared.world.WAnything;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Index for flora options loaded from WAnything entities.
 * Indexes flora definitions by biome prefix (everything before the first '_').
 * Used to provide the AI translator with available flora options per biome.
 */
@Slf4j
public class FloraIndex {

    private final Map<String, List<String>> floraByBiomePrefix = new HashMap<>();
    private final List<String> allFloraNames = new ArrayList<>();

    public FloraIndex(List<WAnything> floraEntities) {
        for (WAnything entity : floraEntities) {
            String name = entity.getName();
            if (name == null || name.isBlank()) continue;

            allFloraNames.add(name);

            String prefix = extractBiomePrefix(name);
            floraByBiomePrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
        }
        log.info("FloraIndex loaded: {} entries, {} biome prefixes", allFloraNames.size(), floraByBiomePrefix.size());
    }

    /**
     * Get all flora option names for a given biome prefix.
     */
    public List<String> getFloraOptionsForBiome(String biomePrefix) {
        if (biomePrefix == null) return new ArrayList<>();
        return new ArrayList<>(floraByBiomePrefix.getOrDefault(biomePrefix.toLowerCase(), List.of()));
    }

    /**
     * Get all known biome prefixes.
     */
    public Set<String> getAllBiomePrefixes() {
        return new TreeSet<>(floraByBiomePrefix.keySet());
    }

    /**
     * Get all flora names.
     */
    public List<String> getAllFloraNames() {
        return new ArrayList<>(allFloraNames);
    }

    /**
     * Format the index as a description for AI prompts.
     */
    public String toPromptDescription() {
        if (allFloraNames.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        sb.append("### Available Flora Options\n\n");
        sb.append("| Biome Prefix | Available Flora Names |\n");
        sb.append("|-------------|----------------------|\n");

        for (String prefix : getAllBiomePrefixes()) {
            List<String> names = floraByBiomePrefix.get(prefix);
            String joined = names.stream().map(n -> "`" + n + "`").collect(Collectors.joining(", "));
            sb.append("| `").append(prefix).append("` | ").append(joined).append(" |\n");
        }

        return sb.toString();
    }

    /**
     * Extract the biome prefix from a flora name.
     * The prefix is everything before the first '_', or the entire name if no '_' exists.
     */
    private static String extractBiomePrefix(String name) {
        int underscoreIndex = name.indexOf('_');
        if (underscoreIndex > 0) {
            return name.substring(0, underscoreIndex).toLowerCase();
        }
        return name.toLowerCase();
    }
}
