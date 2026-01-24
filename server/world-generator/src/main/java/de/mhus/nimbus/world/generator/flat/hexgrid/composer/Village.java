package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Village extends Structure {
    private List<VillageBuildingDefinition> buildings;
    private List<VillageStreetDefinition> streets;
    private Map<String, String> parameters;

    public static VillageBuilder builder() {
        return new VillageBuilder();
    }

    /**
     * Applies village-specific default configuration from StructureType
     */
    @Override
    protected void applyStructureDefaults(Map<String, String> defaults) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        // Apply defaults from StructureType enum
        if (defaults != null) {
            defaults.forEach(parameters::putIfAbsent);
        }

        // Set default builder
        if (getType() != null) {
            parameters.putIfAbsent("g_builder", getType().getDefaultBuilder());
        }
    }

    /**
     * Configures HexGrids for this village with buildings and streets.
     * Creates FeatureHexGrid configurations for each village grid.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return;
        }

        // Clear existing configurations
        if (getHexGrids() != null) {
            getHexGrids().clear();
        }

        // Create FeatureHexGrid for each coordinate
        for (HexVector2 coord : coordinates) {
            FeatureHexGrid featureHexGrid = FeatureHexGrid.builder()
                .coordinate(coord)
                .name(getName() + " [" + coord.getQ() + "," + coord.getR() + "]")
                .description("Part of " + (getType() != null ? getType().name() : "VILLAGE") + " village")
                .build();

            // Copy village parameters to grid
            if (parameters != null) {
                featureHexGrid.getParameters().putAll(parameters);
            }

            // Add structure type parameter
            if (getType() != null) {
                featureHexGrid.addParameter("structure", getType().name().toLowerCase());
                featureHexGrid.addParameter("structureName", getName());
            } else {
                featureHexGrid.addParameter("structure", "village");
                featureHexGrid.addParameter("structureName", getName());
            }

            // Add to this feature
            addHexGrid(featureHexGrid);
        }
    }
}
