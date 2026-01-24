package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Forest biome with trees and slightly hilly terrain.
 *
 * Default configuration:
 * - Uses MountainBuilder with low offset (g_offset=2) for gentle hills
 * - Will later use FloraBuilder for tree placement (g_flora=forest)
 * - Tree density parameter (flora_density=0.8)
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "FOREST",
 *   "name": "dark-forest",
 *   "size": "LARGE",
 *   "parameters": {
 *     "flora_density": "0.9"  // Override default
 *   }
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForestBiome extends Biome {

    /**
     * Applies forest-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Forest-specific adjustments can be added here
        // For now, all defaults come from BiomeType.FOREST
    }

    /**
     * Configures HexGrids for forests with trees and gentle hills.
     * Example of how subclasses can customize grid configuration for flora.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Call base implementation to create standard FeatureHexGrids
        super.configureHexGrids(coordinates);

        // Forest-specific customization can be added here
        // For example: vary tree density based on position, add clearings, etc.
        // Could add additional flora parameters per grid
        // Current implementation uses defaults from BiomeType.FOREST
    }
}
