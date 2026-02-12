package de.mhus.nimbus.world.generator.composer.biome;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Swamp biome with wetland terrain and water-filled valleys.
 *
 * Supports different swamp depths:
 * - SHALLOW: swampDepth=2, low terrain variation (g_offset=8)
 * - MEDIUM: swampDepth=3, moderate terrain variation (g_offset=10) [Default]
 * - DEEP: swampDepth=5, higher terrain variation (g_offset=12)
 * - BOG: swampDepth=4, very low elevation (g_asl=3), minimal variation (g_offset=6)
 *
 * Default configuration:
 * - Uses SwampBuilder (g_builder="swamp")
 * - Low elevation (g_asl=5) for marshy terrain
 * - Moderate terrain variation (g_offset=10)
 * - Water depth in valleys (swampDepth=3)
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "SWAMP",
 *   "name": "marshlands",
 *   "size": "MEDIUM",
 *   "depth": "DEEP",
 *   "parameters": {
 *     "g_frequency": "0.8"  // More variation in terrain
 *   }
 * }
 * </pre>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwampBiome extends Biome {

    /**
     * Swamp depth configuration for this swamp biome.
     * Determines how deep the water pools are in valleys.
     */
    private SwampDepth depth;

    /**
     * Ground material type for this swamp biome.
     * Determines which materials are used for the swamp floor.
     */
    private GroundType groundType;

    /**
     * Swamp depth presets with water depth and terrain variation values.
     * Formula: water fills from valley bottom (minLevel) to minLevel + swampDepth
     */
    public enum SwampDepth {
        SHALLOW(2, 5, 8, 0.6),    // Shallow puddles, low terrain
        MEDIUM(3, 5, 10, 0.7),    // Medium pools, moderate terrain [Default]
        DEEP(5, 5, 12, 0.8),      // Deep pools, varied terrain
        BOG(4, 3, 6, 0.5);        // Bog - very low and flat

        private final int swampDepth;
        private final int aboveSeaLevel;
        private final int landOffset;
        private final double frequency;

        SwampDepth(int swampDepth, int aboveSeaLevel, int landOffset, double frequency) {
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
     * Applies swamp-specific default configuration.
     * Sets swampDepth, landLevel, and landOffset based on depth preset.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Default to MEDIUM if not specified
        if (depth == null) {
            depth = SwampDepth.MEDIUM;
        }

        // Apply depth-specific parameters
        if (getParameters() == null) {
            setParameters(new HashMap<>());
        }

        // Set swampDepth, landLevel, and landOffset based on depth
        getParameters().put("swampDepth", String.valueOf(depth.getSwampDepth()));
        getParameters().put("g_asl", String.valueOf(depth.getAboveSeaLevel()));
        getParameters().put("g_offset", String.valueOf(depth.getLandOffset()));
        getParameters().put("g_frequency", String.valueOf(depth.getFrequency()));

        // Apply ground type materials if specified
        if (groundType == null) {
            groundType = GroundType.SWAMPY;  // Swamps default to SWAMPY ground type
        }
        groundType.applyToParameters(getParameters());

        log.debug("Applied SwampBiome defaults for '{}': depth={}, swampDepth={}, landLevel={}, landOffset={}, groundType={}",
            getName(), depth, depth.getSwampDepth(), depth.getAboveSeaLevel(), depth.getLandOffset(), groundType);
    }
}
