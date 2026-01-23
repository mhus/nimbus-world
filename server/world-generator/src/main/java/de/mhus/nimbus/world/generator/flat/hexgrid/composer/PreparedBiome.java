package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Prepared biome with concrete size ranges and positions.
 */
@Data
public class PreparedBiome {
    private BiomeDefinition original;
    private String name;
    private BiomeType type;
    private BiomeShape shape;

    // Concrete size values
    private int sizeFrom;
    private int sizeTo;

    // Prepared positions
    private List<PreparedPosition> positions;

    // Parameters for HexGrid builder
    private Map<String, String> parameters;
}
