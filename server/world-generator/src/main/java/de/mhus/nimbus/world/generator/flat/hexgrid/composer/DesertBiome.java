package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Desert biome with sandy, arid terrain and sparse vegetation.
 *
 * Default configuration:
 * - Uses MountainBuilder with low offset (g_offset=5) for dunes
 * - Will later use FloraBuilder for cacti (g_flora=desert)
 * - Low vegetation density (cactus_density=0.3)
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "DESERT",
 *   "name": "sahara",
 *   "size": "LARGE",
 *   "parameters": {
 *     "g_offset": "8",  // Higher dunes
 *     "cactus_density": "0.5"  // More cacti
 *   }
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesertBiome extends Biome {

    /**
     * Applies desert-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Desert-specific adjustments can be added here
        // For now, all defaults come from BiomeType.DESERT
    }
}
