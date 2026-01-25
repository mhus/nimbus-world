package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

/**
 * Mountain biome with configurable height levels.
 *
 * Supports different mountain heights:
 * - HIGH_PEAKS: landLevel=150, landOffset=40 (max height ~240 blocks)
 * - MEDIUM_PEAKS: landLevel=120, landOffset=30 (max height ~200 blocks)
 * - LOW_PEAKS: landLevel=100, landOffset=20 (max height ~170 blocks)
 * - MEADOW: landLevel=80, landOffset=10 (max height ~140 blocks)
 *
 * Default configuration:
 * - Uses MountainBuilder (g_builder="mountain")
 * - High roughness (g_roughness=0.8) for jagged peaks
 * - Defaults to MEDIUM_PEAKS if height not specified
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "MOUNTAINS",
 *   "name": "alpine-peaks",
 *   "size": "LARGE",
 *   "height": "HIGH_PEAKS"
 * }
 * </pre>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MountainBiome extends Biome {

    /**
     * Peak height configuration for this mountain biome.
     * Determines landLevel and landOffset parameters.
     */
    private MountainHeight height;

    /**
     * Mountain height presets with land level and offset values.
     * Formula: maxLevel = landLevel + oceanLevel + landOffset
     * (with oceanLevel typically = 50)
     */
    public enum MountainHeight {
        HIGH_PEAKS(150, 40),    // max level: 150+50+40 = 240
        MEDIUM_PEAKS(120, 30),  // max level: 120+50+30 = 200
        LOW_PEAKS(100, 20),     // max level: 100+50+20 = 170
        MEADOW(80, 10);         // max level: 80+50+10 = 140

        private final int landLevel;
        private final int landOffset;

        MountainHeight(int landLevel, int landOffset) {
            this.landLevel = landLevel;
            this.landOffset = landOffset;
        }

        public int getLandLevel() {
            return landLevel;
        }

        public int getLandOffset() {
            return landOffset;
        }
    }

    /**
     * Applies mountain-specific default configuration.
     * Sets landLevel and landOffset based on mountain height.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Default to MEDIUM_PEAKS if not specified
        if (height == null) {
            height = MountainHeight.MEDIUM_PEAKS;
        }

        // Apply height-specific parameters
        if (getParameters() == null) {
            setParameters(new HashMap<>());
        }

        // Set landLevel and landOffset based on height
        getParameters().put("landLevel", String.valueOf(height.getLandLevel()));
        getParameters().put("landOffset", String.valueOf(height.getLandOffset()));

        log.info("Applied MountainBiome defaults for '{}': height={}, landLevel={}, landOffset={}",
            getName(), height, height.getLandLevel(), height.getLandOffset());
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
