package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Mountain biome with high, rugged terrain.
 *
 * Default configuration:
 * - Uses MountainBuilder with high offset (g_offset=30) for tall mountains
 * - High roughness (g_roughness=0.8) for jagged peaks
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "MOUNTAINS",
 *   "name": "alpine-peaks",
 *   "size": "LARGE",
 *   "parameters": {
 *     "g_offset": "40"  // Even taller mountains
 *   }
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MountainBiome extends Biome {

    /**
     * Applies mountain-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Mountain-specific adjustments can be added here
        // For now, all defaults come from BiomeType.MOUNTAINS
    }

    /**
     * Configures HexGrids for mountains with high elevation and rugged terrain.
     * Example of how subclasses can customize grid configuration.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Call base implementation to create standard FeatureHexGrids
        super.configureHexGrids(coordinates);

        // Mountain-specific customization can be added here
        // For example: adjust roughness based on position, add peaks, etc.
        // Current implementation uses defaults from BiomeType.MOUNTAINS
    }
}
