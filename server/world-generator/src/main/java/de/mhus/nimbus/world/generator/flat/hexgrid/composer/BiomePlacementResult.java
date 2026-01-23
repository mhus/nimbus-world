package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of biome composition process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiomePlacementResult {
    /**
     * The original prepared composition
     */
    private PreparedHexComposition composition;

    /**
     * Successfully placed biomes
     */
    private List<PlacedBiome> placedBiomes;

    /**
     * Generated HexGrids with configuration
     */
    private List<WHexGrid> hexGrids;

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
