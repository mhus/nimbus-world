package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of hex grid filling process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HexGridFillResult {
    /**
     * Original placement result
     */
    private BiomePlacementResult placementResult;

    /**
     * All hex grids (biomes + fillers)
     */
    private List<FilledHexGrid> allGrids;

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

    /**
     * Gets all WHexGrid instances
     */
    public List<WHexGrid> getAllHexGrids() {
        return allGrids.stream()
            .map(FilledHexGrid::getHexGrid)
            .toList();
    }
}
