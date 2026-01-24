package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Plains biome with flat, open terrain.
 *
 * Default configuration:
 * - Uses IslandBuilder with minimal offset (g_offset=1) for nearly flat terrain
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "PLAINS",
 *   "name": "grasslands",
 *   "size": "LARGE",
 *   "parameters": {
 *     "g_offset": "0"  // Completely flat
 *   }
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlainsBiome extends Biome {

    /**
     * Applies plains-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Plains-specific adjustments can be added here
        // For now, all defaults come from BiomeType.PLAINS
    }
}
