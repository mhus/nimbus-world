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
public class Biome extends Area {
    private BiomeType type;
    private Map<String, String> parameters;

    public static BiomeBuilder builder() {
        return new BiomeBuilder();
    }

    /**
     * Applies default configuration for this biome type.
     * Override in subclasses for type-specific defaults.
     */
    public void applyDefaults() {
        if (type == null) {
            return;
        }

        if (parameters == null) {
            parameters = new HashMap<>();
        }

        // Apply defaults from BiomeType enum
        Map<String, String> defaults = type.getDefaultParameters();
        if (defaults != null) {
            defaults.forEach(parameters::putIfAbsent);
        }

        // Set default builder
        parameters.putIfAbsent("g_builder", type.getDefaultBuilder());
    }

    /**
     * Configures HexGrids for this biome at the given coordinates.
     * Creates FeatureHexGrid configurations with biome-specific parameters.
     * Override in subclasses for specialized grid configuration.
     *
     * @param coordinates List of coordinates assigned to this biome
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
                .description("Part of " + (type != null ? type.name() : "unknown") + " biome")
                .build();

            // Copy biome parameters to grid
            if (parameters != null) {
                featureHexGrid.getParameters().putAll(parameters);
            }

            // Add biome type parameter
            if (type != null) {
                featureHexGrid.addParameter("biome", type.name().toLowerCase());
                featureHexGrid.addParameter("biomeName", getName());
            }

            // Add to this feature
            addHexGrid(featureHexGrid);
        }
    }
}
