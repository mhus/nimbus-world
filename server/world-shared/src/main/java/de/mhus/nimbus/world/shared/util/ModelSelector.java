package de.mhus.nimbus.world.shared.util;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Model selector for storing selected blocks in a build operation.
 * Format:
 * - First entry is always a config: <def color>,<auto select name>
 * - Further entries are selected blocks in Vector3Color format: x,y,z,color
 */
@Data
@Builder
public class ModelSelector {

    /**
     * Default color for selected blocks.
     */
    private String defaultColor;

    /**
     * Auto select name/identifier.
     */
    private String autoSelectName;

    /**
     * List of selected block positions with colors.
     * Format: x,y,z,color
     */
    @Builder.Default
    private List<String> blocks = new ArrayList<>();

    /**
     * Create a ModelSelector from serialized string list.
     *
     * @param data serialized data (first entry is config, rest are blocks)
     * @return ModelSelector instance
     */
    public static ModelSelector fromStringList(List<String> data) {
        if (data == null || data.isEmpty()) {
            return ModelSelector.builder().build();
        }

        // Parse config line (first entry)
        String configLine = data.get(0);
        String[] configParts = configLine.split(",", 2);
        String defaultColor = configParts.length > 0 ? configParts[0] : "";
        String autoSelectName = configParts.length > 1 ? configParts[1] : "";

        // Parse block entries
        List<String> blocks = new ArrayList<>();
        Set<String> coordinates = new HashSet<>();
        for (int i = 1; i < data.size(); i++) {
            var coordinate = data.get(i).substring(0, data.get(i).lastIndexOf(','));
            if (!coordinates.contains(coordinate)) {
                coordinates.add(coordinate);
                blocks.add(data.get(i));
            }
        }

        return ModelSelector.builder()
                .defaultColor(defaultColor)
                .autoSelectName(autoSelectName)
                .blocks(blocks)
                .build();
    }

    public static List<String> cleanup(List<String> modelSelector) {
        var selector = ModelSelector.fromStringList(modelSelector);
        return selector.toStringList();
    }

    /**
     * Convert this ModelSelector to serialized string list.
     *
     * @return serialized data (first entry is config, rest are blocks)
     */
    public List<String> toStringList() {
        List<String> result = new ArrayList<>();

        // Add config line
        String configLine = (defaultColor != null ? defaultColor : "") + "," +
                (autoSelectName != null ? autoSelectName : "");
        result.add(configLine);

        // Add block entries
        if (blocks != null) {
            result.addAll(blocks);
        }

        return result;
    }

    /**
     * Add a block to the selection.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param color block color
     */
    public void addBlock(int x, int y, int z, String color) {
        if (blocks == null) {
            blocks = new ArrayList<>();
        }
        blocks.add(String.format("%d,%d,%d,%s", x, y, z, color));
    }

    /**
     * Get the number of selected blocks.
     *
     * @return number of blocks
     */
    public int getBlockCount() {
        return blocks != null ? blocks.size() : 0;
    }

    /**
     * Clear all selected blocks.
     */
    public void clearBlocks() {
        if (blocks != null) {
            blocks.clear();
        }
    }
}
