package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Ocean biome for deep water areas.
 *
 * Default configuration:
 * - Uses OceanBuilder for underwater terrain
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "OCEAN",
 *   "name": "deep-sea",
 *   "size": "XLARGE"
 * }
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OceanBiome extends Biome {

    /**
     * Applies ocean-specific default configuration.
     * Overrides base implementation to add custom parameters.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Ocean-specific adjustments can be added here
        // For now, all defaults come from BiomeType.OCEAN
    }
}
