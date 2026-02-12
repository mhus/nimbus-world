package de.mhus.nimbus.world.generator.composer.biome;

import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of biome composition process.
 * Note: WHexGrids are no longer included here - they are created later by HexGridGenerator
 * from FeatureHexGrids in the Central Registry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiomePlacementResult {
    /**
     * The original prepared composition (contains FeatureHexGrids in Central Registry)
     */
    private HexComposition composition;

    /**
     * Successfully placed biomes
     */
    private List<PlacedBiome> placedBiomes;

    /**
     * Number of retries needed
     */
    private int retries;

    /**
     * Success flag
     */
    private boolean success;

    /**
     * Error message if failed
     */
    private String errorMessage;
}
