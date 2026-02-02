package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a road connection between two points.
 * Grid coordinates and sides are calculated during composition from point positions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadConnection {
    /**
     * Start point ID
     */
    private String fromPointId;

    /**
     * End point ID
     */
    private String toPointId;

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
