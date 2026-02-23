package de.mhus.nimbus.world.generator.composer.biome;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Desert biome with sandy, arid terrain and sparse vegetation.
 *
 * Supports different desert terrain types:
 * - FLAT: Flat desert plains (g_offset=5), minimal variation
 * - DUNES: Rolling sand dunes (g_offset=15), typical desert [Default]
 * - ROCKY: Rocky desert with outcrops (g_offset=18), more stone (50%)
 * - BADLANDS: Highly eroded terrain (g_offset=20), very rocky (70% stone)
 *
 * Default configuration:
 * - Uses DesertBuilder (g_builder="desert")
 * - Elevated terrain (g_asl=30) for dry conditions
 * - Moderate dunes (g_offset=15)
 * - Desert sand surface with occasional dirt/stone
 * - Sparse vegetation (cactus_density=0.3)
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "DESERT",
 *   "name": "badlands",
 *   "size": "LARGE",
 *   "terrain": "BADLANDS",
 *   "parameters": {
 *     "gf_density": "0.1"  // Even less vegetation
 *   }
 * }
 * </pre>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesertBiome extends Biome {

    /**
     * Desert terrain type configuration.
     * Determines terrain variation and material distribution.
     */
    private DesertTerrain terrain;

    /**
     * Desert terrain presets with elevation and material parameters.
     */
    public enum DesertTerrain {
        FLAT(5, 30, 0.5, 0.05, 0.1, 0.2),      // Flat desert plains
        DUNES(15, 30, 0.7, 0.05, 0.3, 0.3),     // Rolling sand dunes [Default]
        ROCKY(18, 35, 0.8, 0.1, 0.5, 0.2),      // Rocky desert
        BADLANDS(20, 35, 0.9, 0.15, 0.7, 0.1);  // Eroded badlands

        private final int landOffset;
        private final int aboveSeaLevel;
        private final double frequency;
        private final double dirtRatio;
        private final double stoneRatio;
        private final double cactusDensity;

        DesertTerrain(int landOffset, int aboveSeaLevel, double frequency, double dirtRatio, double stoneRatio, double cactusDensity) {
            this.landOffset = landOffset;
            this.aboveSeaLevel = aboveSeaLevel;
            this.frequency = frequency;
            this.dirtRatio = dirtRatio;
            this.stoneRatio = stoneRatio;
            this.cactusDensity = cactusDensity;
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

        public double getStoneRatio() {
            return stoneRatio;
        }

        public double getCactusDensity() {
            return cactusDensity;
        }
    }

    /**
     * Applies desert-specific default configuration.
     * Sets terrain variation and material distribution based on terrain type.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Default to DUNES if not specified
        if (terrain == null) {
            terrain = DesertTerrain.DUNES;
        }

        // Apply terrain-specific parameters
        if (getParameters() == null) {
            setParameters(new HashMap<>());
        }

        // Set parameters based on terrain type
        getParameters().put("g_offset", String.valueOf(terrain.getLandOffset()));
        getParameters().put("g_asl", String.valueOf(terrain.getAboveSeaLevel()));
        getParameters().put("g_frequency", String.valueOf(terrain.getFrequency()));
        getParameters().put("dirtRatio", String.valueOf(terrain.getDirtRatio()));
        getParameters().put("stoneRatio", String.valueOf(terrain.getStoneRatio()));
        getParameters().put("gf_density", String.valueOf(terrain.getCactusDensity()));

        log.debug("Applied DesertBiome defaults for '{}': terrain={}, landOffset={}, landLevel={}, stoneRatio={}, cactusDensity={}",
            getName(), terrain, terrain.getLandOffset(), terrain.getAboveSeaLevel(), terrain.getStoneRatio(), terrain.getCactusDensity());
    }
}
