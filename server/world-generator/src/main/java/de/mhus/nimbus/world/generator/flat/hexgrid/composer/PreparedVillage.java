package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Prepared village with concrete positions and internal structure.
 */
@Data
public class PreparedVillage {
    private VillageDefinition original;
    private String name;
    private BiomeType type;
    private BiomeShape shape;

    // Prepared positions
    private List<PreparedPosition> positions;

    // Internal structure
    private List<VillageBuildingDefinition> buildings;
    private List<VillageStreetDefinition> streets;

    // Parameters for HexGrid builder
    private Map<String, String> parameters;
}
