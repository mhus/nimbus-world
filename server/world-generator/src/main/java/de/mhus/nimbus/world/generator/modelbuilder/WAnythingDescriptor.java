package de.mhus.nimbus.world.generator.modelbuilder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic descriptor for WAnything-based model building. Supports two formats:
 * <ul>
 *   <li>{@code block:m:champignon} - single block</li>
 *   <li>{@code block:m:sunflower_log,m:sunflower_top} - N blocks stacked vertically</li>
 *   <li>{@code model:tree,log=m:birch_log,leaves=m:birch_leaves} - model from WAnything with named parameters</li>
 * </ul>
 */
public sealed interface WAnythingDescriptor {

    record BlockStack(List<String> blockTypes) implements WAnythingDescriptor {}

    record ModelRef(String name, Map<String, String> parameters) implements WAnythingDescriptor {}

    /**
     * Parse a descriptor string into a WAnythingDescriptor.
     *
     * @param descriptor the descriptor string
     * @return parsed WAnythingDescriptor
     * @throws IllegalArgumentException if the descriptor is blank or has an unknown prefix
     */
    static WAnythingDescriptor parse(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("Descriptor must not be blank");
        }

        int colonIndex = descriptor.indexOf(':');
        if (colonIndex < 0) {
            throw new IllegalArgumentException("Invalid descriptor, missing prefix: " + descriptor);
        }

        String prefix = descriptor.substring(0, colonIndex);
        String body = descriptor.substring(colonIndex + 1);

        return switch (prefix) {
            case "block" -> parseBlock(body);
            case "model" -> parseModel(body);
            default -> throw new IllegalArgumentException("Unknown descriptor prefix: " + prefix);
        };
    }

    private static BlockStack parseBlock(String body) {
        List<String> blockTypes = Arrays.stream(body.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (blockTypes.isEmpty()) {
            throw new IllegalArgumentException("block descriptor has no block types");
        }
        return new BlockStack(blockTypes);
    }

    private static ModelRef parseModel(String body) {
        String[] tokens = body.split(",");
        String name = tokens[0].trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("model descriptor has no name");
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].trim();
            int eqIndex = token.indexOf('=');
            if (eqIndex < 0) {
                throw new IllegalArgumentException("model parameter missing '=': " + token);
            }
            String key = token.substring(0, eqIndex).trim();
            String value = token.substring(eqIndex + 1).trim();
            parameters.put(key, value);
        }
        return new ModelRef(name, parameters);
    }
}
