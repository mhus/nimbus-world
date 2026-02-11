package de.mhus.nimbus.world.generator.composer.structure;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.area.Area;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Structure extends Area {
    private StructureType type;

    /**
     * Composed data - calculated during composition phase at Structure level.
     * Separates input configuration from runtime computed values.
     */
    private StructureComposed structureComposed;

    /**
     * Inner class for structureComposed (calculated) data at Structure level.
     * Stores values computed during composition, separate from user input.
     *
     * Note: hexGrids is temporary storage during composition phase.
     * Structures configure their FeatureHexGrids here, then they are copied to
     * the central HexComposition.featureHexGridRegistry by StructureComposer.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructureComposed {
        private Integer calculatedHexGridWidth;
        private Integer calculatedHexGridHeight;

        /**
         * Temporary storage for FeatureHexGrids during composition phase.
         * After composition, these are registered in central HexComposition.featureHexGridRegistry.
         */
        private List<FeatureHexGrid> hexGrids;
    }

    /**
     * Applies default configuration for this structure type.
     * Override in subclasses for type-specific defaults.
     */
    @Override
    public void applyDefaults() {
        if (type == null) {
            return;
        }

        // Apply defaults from StructureType enum
        // Subclasses can use these defaults or override them
        Map<String, String> defaults = type.getDefaultParameters();
        if (defaults != null) {
            applyStructureDefaults(defaults);
        }
    }

    /**
     * Hook for subclasses to apply structure-specific defaults.
     * Base implementation does nothing - override in subclasses.
     */
    protected void applyStructureDefaults(Map<String, String> defaults) {
        // Base implementation - subclasses override
    }

    /**
     * Configures HexGrids for this structure at the given coordinates.
     * Structures typically configure village/town grids with buildings and streets.
     * Override in subclasses for type-specific configuration.
     *
     * @param coordinates List of coordinates assigned to this structure
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Default implementation - override in subclasses
        // Structures have complex configuration with buildings/streets
    }

    // Helper methods for backward compatibility

    public Integer getCalculatedHexGridWidth() {
        return structureComposed != null ? structureComposed.getCalculatedHexGridWidth() : null;
    }

    public void setCalculatedHexGridWidth(Integer calculatedHexGridWidth) {
        if (structureComposed == null) {
            structureComposed = new StructureComposed();
        }
        structureComposed.setCalculatedHexGridWidth(calculatedHexGridWidth);
    }

    public Integer getCalculatedHexGridHeight() {
        return structureComposed != null ? structureComposed.getCalculatedHexGridHeight() : null;
    }

    public void setCalculatedHexGridHeight(Integer calculatedHexGridHeight) {
        if (structureComposed == null) {
            structureComposed = new StructureComposed();
        }
        structureComposed.setCalculatedHexGridHeight(calculatedHexGridHeight);
    }

    // HexGrid management methods

    public List<FeatureHexGrid> getHexGrids() {
        return structureComposed != null ? structureComposed.getHexGrids() : null;
    }

    public void setHexGrids(List<FeatureHexGrid> hexGrids) {
        if (structureComposed == null) {
            structureComposed = new StructureComposed();
        }
        structureComposed.setHexGrids(hexGrids);
    }

    public void addHexGrid(FeatureHexGrid hexGrid) {
        if (structureComposed == null) {
            structureComposed = new StructureComposed();
        }
        if (structureComposed.getHexGrids() == null) {
            structureComposed.setHexGrids(new ArrayList<>());
        }
        structureComposed.getHexGrids().add(hexGrid);
    }

    public FeatureHexGrid findHexGrid(int q, int r) {
        if (structureComposed == null || structureComposed.getHexGrids() == null) {
            return null;
        }
        return structureComposed.getHexGrids().stream()
            .filter(grid -> grid.getCoordinate() != null &&
                          grid.getCoordinate().getQ() == q &&
                          grid.getCoordinate().getR() == r)
            .findFirst()
            .orElse(null);
    }
}
