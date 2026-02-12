package de.mhus.nimbus.world.generator.composer.biome;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Plains biome with flat, open grassland terrain.
 *
 * Supports different terrain variations:
 * - FLAT: Nearly flat plains (g_offset=2), no lakes
 * - ROLLING: Gently rolling hills (g_offset=5), occasional lakes [Default]
 * - MEADOW: Varied meadows (g_offset=7), more lakes
 * - STEPPE: Dry grassland (g_offset=4), 20% dirt, few lakes
 *
 * Default configuration:
 * - Uses PlainsBuilder (g_builder="plains")
 * - Low elevation (g_asl=15) for open terrain
 * - Gentle variation (g_offset=5)
 * - Grass/dirt surface (dirtRatio=0.1)
 * - Occasional lakes in valleys (enableLakes=true, lakeDepth=4)
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "PLAINS",
 *   "name": "green-meadows",
 *   "size": "LARGE",
 *   "variation": "MEADOW",
 *   "parameters": {
 *     "lakeDepth": "5"  // Deeper lakes
 *   }
 * }
 * </pre>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlainsBiome extends Biome {

    /**
     * Plains terrain variation configuration.
     * Determines terrain flatness and lake frequency.
     */
    private PlainsVariation variation;

    /**
     * Ground material type for this plains biome.
     * Determines which materials are used for the plains surface.
     */
    private GroundType groundType;

    /**
     * Plains variation presets with terrain and lake parameters.
     */
    public enum PlainsVariation {
        FLAT(2, 15, 0.4, 0.05, false, 3),          // Very flat, no lakes
        ROLLING(5, 15, 0.5, 0.1, true, 4),         // Gently rolling, occasional lakes [Default]
        MEADOW(7, 18, 0.6, 0.15, true, 5),         // Varied meadows, more lakes
        STEPPE(4, 20, 0.5, 0.2, false, 3);         // Dry grassland, higher, few lakes

        private final int landOffset;
        private final int aboveSeaLevel;
        private final double frequency;
        private final double dirtRatio;
        private final boolean enableLakes;
        private final int lakeDepth;

        PlainsVariation(int landOffset, int aboveSeaLevel, double frequency, double dirtRatio, boolean enableLakes, int lakeDepth) {
            this.landOffset = landOffset;
            this.aboveSeaLevel = aboveSeaLevel;
            this.frequency = frequency;
            this.dirtRatio = dirtRatio;
            this.enableLakes = enableLakes;
            this.lakeDepth = lakeDepth;
        }

        public int getLandOffset() {
            return landOffset;
        }

        public int getAboveSeaLevel() {
            return aboveSeaLevel;
        }

        public double getFrequency() {
            return frequency;
        }

        public double getDirtRatio() {
            return dirtRatio;
        }

        public boolean isEnableLakes() {
            return enableLakes;
        }

        public int getLakeDepth() {
            return lakeDepth;
        }
    }

    /**
     * Applies plains-specific default configuration.
     * Sets terrain variation and lake parameters based on variation type.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Default to ROLLING if not specified
        if (variation == null) {
            variation = PlainsVariation.ROLLING;
        }

        // Apply variation-specific parameters
        if (getParameters() == null) {
            setParameters(new HashMap<>());
        }

        // Set parameters based on variation type
        getParameters().put("g_offset", String.valueOf(variation.getLandOffset()));
        getParameters().put("g_asl", String.valueOf(variation.getAboveSeaLevel()));
        getParameters().put("g_frequency", String.valueOf(variation.getFrequency()));
        getParameters().put("dirtRatio", String.valueOf(variation.getDirtRatio()));
        getParameters().put("enableLakes", String.valueOf(variation.isEnableLakes()));
        getParameters().put("lakeDepth", String.valueOf(variation.getLakeDepth()));

        // Apply ground type materials if specified
        if (groundType == null) {
            groundType = GroundType.GRASSY;  // Plains default to GRASSY ground type
        }
        groundType.applyToParameters(getParameters());

        log.debug("Applied PlainsBiome defaults for '{}': variation={}, landOffset={}, landLevel={}, dirtRatio={}, lakes={}, groundType={}",
            getName(), variation, variation.getLandOffset(), variation.getAboveSeaLevel(), variation.getDirtRatio(), variation.isEnableLakes(), groundType);
    }
}

