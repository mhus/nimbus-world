package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Coast biome for transition zones between land and ocean.
 *
 * Default configuration:
 * - Uses CoastBuilder for natural shore transitions
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "COAST",
 *   "name": "shoreline",
 *   "size": "MEDIUM"
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoastBiome extends Biome {

    /**
     * Applies coast-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Coast-specific adjustments can be added here
        // For now, all defaults come from BiomeType.COAST
    }
}
