package de.mhus.nimbus.world.shared.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting between ModelSelector instances and their List<String> representation
 * used in WSession for Redis serialization.
 * <p>
 * Format:
 * - First entry is always a config: <def color>,<auto select name>
 * - Further entries are selected blocks in Vector3Color format: x,y,z,color
 */
public class ModelSelectorUtil {

    /**
     * Create a ModelSelector from a List<String> representation.
     * This is used when deserializing from Redis/WSession.
     *
     * @param data serialized data (first entry is config, rest are blocks)
     * @return ModelSelector instance, or null if data is null or empty
     */
    public static ModelSelector fromStringList(List<String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        return ModelSelector.fromStringList(data);
    }

    /**
     * Convert a ModelSelector to a List<String> representation.
     * This is used when serializing to Redis/WSession.
     *
     * @param modelSelector the ModelSelector to serialize
     * @return serialized data (first entry is config, rest are blocks), or null if modelSelector is null
     */
    public static List<String> toStringList(ModelSelector modelSelector) {
        if (modelSelector == null) {
            return null;
        }

        return modelSelector.toStringList();
    }

    /**
     * Create an empty ModelSelector with default config.
     *
     * @param defaultColor default color for selected blocks
     * @param autoSelectName auto select name/identifier
     * @return empty ModelSelector with config
     */
    public static ModelSelector createEmpty(String defaultColor, String autoSelectName) {
        return ModelSelector.builder()
                .defaultColor(defaultColor)
                .autoSelectName(autoSelectName)
                .blocks(new ArrayList<>())
                .build();
    }

    /**
     * Create an empty ModelSelector with default values.
     *
     * @return empty ModelSelector with default config
     */
    public static ModelSelector createEmpty() {
        return createEmpty("", "");
    }

    /**
     * Check if a model selector is empty (no blocks selected).
     *
     * @param modelSelector the ModelSelector to check
     * @return true if empty or null
     */
    public static boolean isEmpty(ModelSelector modelSelector) {
        return modelSelector == null || modelSelector.getBlockCount() == 0;
    }

    /**
     * Check if a model selector string list is empty (no blocks selected).
     *
     * @param data serialized model selector data
     * @return true if empty, null, or contains only config line
     */
    public static boolean isEmpty(List<String> data) {
        return data == null || data.isEmpty() || data.size() <= 1;
    }
}
