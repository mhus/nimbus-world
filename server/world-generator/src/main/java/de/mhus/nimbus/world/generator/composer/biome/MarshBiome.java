package de.mhus.nimbus.world.generator.composer.biome;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Marsh biome with flat wetland terrain and extensive water coverage.
 * Marshes are flatter and lower than swamps, often closer to sea level,
 * with more open water areas.
 *
 * Supports different marsh water levels:
 * - TIDAL: Very close to sea level (g_asl=1), minimal variation, shallow water
 * - COASTAL: Close to sea level (g_asl=2), low variation, moderate water depth [Default]
 * - INLAND: Slightly elevated (g_asl=4), moderate variation, deeper pools
 * - WETLAND: Higher elevation (g_asl=6), more variation, deep water areas
 *
 * Default configuration:
 * - Uses SwampBuilder (g_builder="swamp")
 * - Very low elevation (g_asl=2) for marsh terrain
 * - Minimal terrain variation (g_offset=5)
 * - Moderate water depth in pools (swampDepth=3)
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "MARSH",
 *   "name": "tidal-marsh",
 *   "size": "LARGE",
 *   "waterLevel": "TIDAL",
 *   "parameters": {
 *     "grassMaterial": "DIRT"  // Custom material for marsh ground
 *   }
 * }
 * </pre>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarshBiome extends Biome {

    /**
     * Marsh water level configuration.
     * Determines elevation and water coverage.
     */
    private MarshWaterLevel waterLevel;

    /**
     * Marsh water level presets with elevation and terrain parameters.
     * Marshes are characterized by low elevation and flat terrain.
     */
    public enum MarshWaterLevel {
        TIDAL(2, 1, 4, 0.4),      // Almost at sea level, very flat
        COASTAL(3, 2, 5, 0.5),    // Close to sea level, flat [Default]
        INLAND(4, 4, 7, 0.6),     // Slightly elevated, more variation
        WETLAND(5, 6, 9, 0.7);    // Higher, more varied terrain

        private final int swampDepth;
        private final int aboveSeaLevel;
        private final int landOffset;
        private final double frequency;

        MarshWaterLevel(int swampDepth, int aboveSeaLevel, int landOffset, double frequency) {
            this.swampDepth = swampDepth;
            this.aboveSeaLevel = aboveSeaLevel;
            this.landOffset = landOffset;
            this.frequency = frequency;
        }

        public int getSwampDepth() {
            return swampDepth;
        }

        public int getAboveSeaLevel() {
            return aboveSeaLevel;
        }

        public int getLandOffset() {
            return landOffset;
        }

        public double getFrequency() {
            return frequency;
        }
    }

    /**
     * Applies marsh-specific default configuration.
     * Sets parameters optimized for flat, low-lying wetlands.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Default to COASTAL if not specified
        if (waterLevel == null) {
            waterLevel = MarshWaterLevel.COASTAL;
        }

        // Apply water level-specific parameters
        if (getParameters() == null) {
            setParameters(new HashMap<>());
        }

        // Set swampDepth, landLevel, and landOffset based on water level
        getParameters().put("swampDepth", String.valueOf(waterLevel.getSwampDepth()));
        getParameters().put("g_asl", String.valueOf(waterLevel.getAboveSeaLevel()));
        getParameters().put("g_offset", String.valueOf(waterLevel.getLandOffset()));
        getParameters().put("g_frequency", String.valueOf(waterLevel.getFrequency()));

        // Marsh-specific material defaults (can be overridden)
        getParameters().putIfAbsent("grassMaterial", "DIRT");  // Muddy ground

        log.debug("Applied MarshBiome defaults for '{}': waterLevel={}, swampDepth={}, landLevel={}, landOffset={}",
            getName(), waterLevel, waterLevel.getSwampDepth(), waterLevel.getAboveSeaLevel(), waterLevel.getLandOffset());
    }
}
