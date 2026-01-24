package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Swamp biome with wetland terrain and standing water.
 *
 * Default configuration:
 * - Uses CoastBuilder with minimal offset (g_offset=1) for wet terrain
 * - Water feature enabled (g_water=true) for puddles and ponds
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "SWAMP",
 *   "name": "marshlands",
 *   "size": "MEDIUM",
 *   "parameters": {
 *     "g_offset": "2",  // Slightly more variation
 *     "water_density": "0.7"  // More water pools
 *   }
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwampBiome extends Biome {

    /**
     * Applies swamp-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Swamp-specific adjustments can be added here
        // For now, all defaults come from BiomeType.SWAMP
    }
}
