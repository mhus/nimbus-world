package de.mhus.nimbus.world.generator.composer.biome;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

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
     * Ground material type for this mountain biome.
     * Determines which materials are used for different elevations.
     */
    private GroundType groundType;

    /**
     * Mountain height presets with land level and offset values.
     * Formula: maxLevel = landLevel + oceanLevel + landOffset (terrain)
     *          ridgeLevel = landLevel + oceanLevel + landOffset + ridgeOffset (peaks)
     * (with oceanLevel typically = 50)
     */
    public enum MountainHeight {
        HIGH_PEAKS(120, 40, 20, 0.8), // max level: 150+50+40 = 240, ridge: 260
        MEDIUM_PEAKS(100, 30, 15, 0.8), // max level: 120+50+30 = 200, ridge: 215
        LOW_PEAKS(80, 20, 10, 0.7), // max level: 100+50+20 = 170, ridge: 180
        MEADOW(60, 10, 5, 0.6); // max level: 80+50+10 = 140, ridge: 145

        private final int landLevel;
        private final int landOffset;
        private final int ridgeOffset;
        private final double frequency;

        MountainHeight(int landLevel, int landOffset, int ridgeOffset, double frequency) {
            this.landLevel = landLevel;
            this.landOffset = landOffset;
            this.ridgeOffset = ridgeOffset;
            this.frequency = frequency;
        }

        public int getAboveSeaLevel() {
            return landLevel;
        }

        public int getLandOffset() {
            return landOffset;
        }

        public int getRidgeOffset() {
            return ridgeOffset;
        }

        public double getFrequency() {
            return frequency;
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
        getParameters().put("g_asl", String.valueOf(height.getAboveSeaLevel()));
        getParameters().put("g_offset", String.valueOf(height.getLandOffset()));
        getParameters().put("g_frequency", String.valueOf(height.getFrequency()));

        // Apply ground type materials if specified
        if (groundType == null) {
            groundType = GroundType.DEFAULT;
        }
        groundType.applyToParameters(getParameters());

        log.debug(
                "Applied MountainBiome defaults for '{}': height={}, landLevel={}, landOffset={}, groundType={}",
                getName(),
                height,
                height.getAboveSeaLevel(),
                height.getLandOffset(),
                groundType);
    }

    /**
     * Configures HexGrids for mountains with ridge configurations.
     * Creates ridge=[{"side":"NE","level":200},...] parameters for connected grids.
     *
     * @deprecated This method is deprecated and does nothing.
     *             Ridge configuration should be done in BiomeComposer.configureHexGridsForPlacedBiomes()
     *             where FeatureHexGrids are created and registered in central registry.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // No-op: FeatureHexGrids are now managed centrally by BiomeComposer
        // Ridge configuration needs to be implemented in BiomeComposer
        // TODO: Implement ridge configuration in BiomeComposer.configureHexGridsForPlacedBiomes()
    }

    // getHexNeighbor, getHexDirectionDelta, getDirectionSide replaced by
    // HexMathUtil.getNeighborPosition() and direct EDGE iteration above
}
