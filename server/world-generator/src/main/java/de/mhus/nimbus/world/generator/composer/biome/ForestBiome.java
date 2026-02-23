package de.mhus.nimbus.world.generator.composer.biome;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

/**
 * Forest biome with trees and gently rolling terrain.
 *
 * Supports different forest densities:
 * - SPARSE: Low tree density (0.4), minimal variation (g_offset=3), 10% dirt
 * - LIGHT: Moderate tree density (0.6), gentle hills (g_offset=5), 20% dirt
 * - DENSE: High tree density (0.8), rolling hills (g_offset=5), 30% dirt [Default]
 * - OLD_GROWTH: Very high tree density (0.9), varied terrain (g_offset=7), 40% dirt
 *
 * Default configuration:
 * - Uses ForestBuilder (g_builder="forest")
 * - Moderate elevation (g_asl=20) for typical forest
 * - Gentle rolling terrain (g_offset=5)
 * - Mixed grass/dirt ground (dirtRatio=0.3)
 * - Tree density (flora_density=0.8)
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "FOREST",
 *   "name": "ancient-woods",
 *   "size": "LARGE",
 *   "density": "OLD_GROWTH",
 *   "parameters": {
 *     "g_frequency": "0.7"  // More terrain variation
 *   }
 * }
 * </pre>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForestBiome extends Biome {

    /**
     * Forest density configuration.
     * Determines tree coverage and terrain variation.
     */
    private ForestDensity density;

    /**
     * Ground material type for this forest biome.
     * Determines which materials are used for the forest floor.
     */
    private GroundType groundType;

    /**
     * Forest density presets with tree coverage and terrain parameters.
     */
    public enum ForestDensity {
        SPARSE(0.4, 20, 3, 0.5, 0.1),      // Open forest with clearings
        LIGHT(0.6, 20, 5, 0.6, 0.2),       // Light forest
        DENSE(0.8, 20, 5, 0.6, 0.3),       // Dense forest [Default]
        OLD_GROWTH(0.9, 25, 7, 0.7, 0.4);  // Ancient forest with varied terrain

        private final double floraDensity;
        private final int aboveSeaLevel;
        private final int landOffset;
        private final double frequency;
        private final double dirtRatio;

        ForestDensity(double floraDensity, int aboveSeaLevel, int landOffset, double frequency, double dirtRatio) {
            this.floraDensity = floraDensity;
            this.aboveSeaLevel = aboveSeaLevel;
            this.landOffset = landOffset;
            this.frequency = frequency;
            this.dirtRatio = dirtRatio;
        }

        public double getFloraDensity() {
            return floraDensity;
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

        public double getDirtRatio() {
            return dirtRatio;
        }
    }

    /**
     * Applies forest-specific default configuration.
     * Sets tree density and terrain parameters based on density preset.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Default to DENSE if not specified
        if (density == null) {
            density = ForestDensity.DENSE;
        }

        // Apply density-specific parameters
        if (getParameters() == null) {
            setParameters(new HashMap<>());
        }

        // Set parameters based on density
        getParameters().put("gf_density", String.valueOf(density.getFloraDensity()));
        getParameters().put("g_asl", String.valueOf(density.getAboveSeaLevel()));
        getParameters().put("g_offset", String.valueOf(density.getLandOffset()));
        getParameters().put("g_frequency", String.valueOf(density.getFrequency()));
        getParameters().put("dirtRatio", String.valueOf(density.getDirtRatio()));

        // Apply ground type materials if specified
        if (groundType == null) {
            groundType = GroundType.DEFAULT;
        }
        groundType.applyToParameters(getParameters());

        log.debug("Applied ForestBiome defaults for '{}': density={}, floraDensity={}, landLevel={}, landOffset={}, dirtRatio={}, groundType={}",
            getName(), density, density.getFloraDensity(), density.getAboveSeaLevel(), density.getLandOffset(), density.getDirtRatio(), groundType);
    }

    /**
     * Configures HexGrids for forests with trees and gentle hills.
     * Example of how subclasses can customize grid configuration for flora.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Call base implementation to create standard FeatureHexGrids
        super.configureHexGrids(coordinates);

        // Forest-specific customization can be added here
        // For example: vary tree density based on position, add clearings, etc.
        // Could add additional flora parameters per grid
        // Current implementation uses defaults from ForestDensity
    }
}
