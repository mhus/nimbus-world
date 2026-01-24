package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Island biome for standalone land masses.
 *
 * Default configuration:
 * - Uses IslandBuilder for natural island formation
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "ISLAND",
 *   "name": "tropical-island",
 *   "size": "SMALL"
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IslandBiome extends Biome {

    /**
     * Applies island-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Island-specific adjustments can be added here
        // For now, all defaults come from BiomeType.ISLAND
    }
}
