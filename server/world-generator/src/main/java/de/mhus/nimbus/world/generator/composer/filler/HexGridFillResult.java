package de.mhus.nimbus.world.generator.composer.filler;

import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of hex grid filling process.
 * Note: Individual grids are now managed in the central FeatureHexGrid registry.
 * This class only contains statistics about the filling process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HexGridFillResult {
    /**
     * Original placement result (contains WHexGrids)
     */
    private BiomePlacementResult placementResult;

    /**
     * Count of ocean filler grids
     */
    private int oceanFillCount;

    /**
     * Count of land filler grids
     */
    private int landFillCount;

    /**
     * Count of coast filler grids
     */
    private int coastFillCount;

    /**
     * Count of mountain filler grids
     */
    private int mountainFillCount;

    /**
     * Count of lowland filler grids
     */
    private int lowlandFillCount;

    /**
     * Count of continent filler grids
     */
    private int continentFillCount;

    /**
     * Total number of grids
     */
    private int totalGridCount;

    /**
     * Success flag
     */
    private boolean success;

    /**
     * Error message if failed
     */
    private String errorMessage;

    private Map<String, WFlat> flats;

    /**
     * NOTE: getAllGrids() was removed.
     * WHexGrids are now in CompositionResult, use result.getAllGrids() instead.
     */
}
