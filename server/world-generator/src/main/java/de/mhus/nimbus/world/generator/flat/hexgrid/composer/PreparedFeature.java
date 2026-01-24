package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all prepared features with resolved values.
 */
@Data
public abstract class PreparedFeature {
    private Feature original;
    private String featureId;
    private String name;
    private String title;

    /**
     * List of HexGrid configurations prepared for this feature.
     * Populated during BiomeComposer/HexGridFiller stage.
     * Will be copied back to original Feature after composition.
     */
    private List<FeatureHexGrid> hexGrids;

    /**
     * Adds a HexGrid configuration
     */
    public void addHexGrid(FeatureHexGrid hexGrid) {
        if (hexGrids == null) {
            hexGrids = new ArrayList<>();
        }
        hexGrids.add(hexGrid);
    }

    /**
     * Returns all HexGrid configurations
     */
    public List<FeatureHexGrid> getHexGrids() {
        return hexGrids != null ? hexGrids : new ArrayList<>();
    }

    /**
     * Copies HexGrid configurations back to original Feature
     */
    public void copyHexGridsToOriginal() {
        if (original != null && hexGrids != null) {
            for (FeatureHexGrid hexGrid : hexGrids) {
                original.addHexGrid(hexGrid);
            }
        }
    }
}
