package de.mhus.nimbus.world.generator.fauna;

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
 * Index for fauna options loaded from WAnything entities.
 * Indexes fauna definitions by biome prefix (everything before the first '_').
 * Used to provide the AI translator with available fauna options per biome.
 */
@Slf4j
public class FaunaIndex {

    private final Map<String, List<String>> faunaByBiomePrefix = new HashMap<>();
    private final List<String> allFaunaNames = new ArrayList<>();

    public FaunaIndex(List<WAnything> faunaEntities) {
        for (WAnything entity : faunaEntities) {
            String name = entity.getName();
            if (name == null || name.isBlank()) continue;

            allFaunaNames.add(name);

            String prefix = extractBiomePrefix(name);
            faunaByBiomePrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
        }
        log.info("FaunaIndex loaded: {} entries, {} biome prefixes", allFaunaNames.size(), faunaByBiomePrefix.size());
    }

    /**
     * Get all fauna option names for a given biome prefix.
     */
    public List<String> getFaunaOptionsForBiome(String biomePrefix) {
        if (biomePrefix == null) return new ArrayList<>();
        return new ArrayList<>(faunaByBiomePrefix.getOrDefault(biomePrefix.toLowerCase(), List.of()));
    }

    /**
     * Get all known biome prefixes.
     */
    public Set<String> getAllBiomePrefixes() {
        return new TreeSet<>(faunaByBiomePrefix.keySet());
    }

    /**
     * Get all fauna names.
     */
    public List<String> getAllFaunaNames() {
        return new ArrayList<>(allFaunaNames);
    }

    /**
     * Format the index as a description for AI prompts.
     */
    public String toPromptDescription() {
        if (allFaunaNames.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        sb.append("### Available Fauna Options\n\n");
        sb.append("| Biome Prefix | Available Fauna Names |\n");
        sb.append("|-------------|----------------------|\n");

        for (String prefix : getAllBiomePrefixes()) {
            List<String> names = faunaByBiomePrefix.get(prefix);
            String joined = names.stream().map(n -> "`" + n + "`").collect(Collectors.joining(", "));
            sb.append("| `").append(prefix).append("` | ").append(joined).append(" |\n");
        }

        return sb.toString();
    }

    /**
     * Extract the biome prefix from a fauna name.
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
