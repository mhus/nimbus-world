package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a road connection between two hex grids
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadConnection {
    /**
     * Start grid coordinate
     */
    private HexVector2 fromGrid;

    /**
     * End grid coordinate
     */
    private HexVector2 toGrid;

    /**
     * Side at from-grid
     */
    private WHexGrid.SIDE fromSide;

    /**
     * Side at to-grid (opposite of fromSide)
     */
    private WHexGrid.SIDE toSide;

    /**
     * Road width
     */
    private int width;

    /**
     * Road level
     */
    private int level;

    /**
     * Road type (street, trail)
     */
    private String type;

    /**
     * Optional group ID for the entire road
     */
    private String groupId;
}
