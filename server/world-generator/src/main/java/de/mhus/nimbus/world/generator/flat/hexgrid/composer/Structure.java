package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Structure extends Area {
    private StructureType type;
    private Integer calculatedHexGridWidth;
    private Integer calculatedHexGridHeight;

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
}
