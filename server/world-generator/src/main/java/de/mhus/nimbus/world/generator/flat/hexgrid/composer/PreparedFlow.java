package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for prepared flow features with resolved values.
 * Extends PreparedFeature to inherit common feature properties.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class PreparedFlow extends PreparedFeature {

    /**
     * Type of flow
     */
    private FlowType flowType;

    /**
     * Original start point ID reference
     */
    private String startPointId;

    /**
     * Start point coordinate (resolved from startPointId)
     */
    private HexVector2 startPoint;

    /**
     * End point coordinate (resolved from endPointId)
     */
    private HexVector2 endPoint;

    /**
     * Waypoint coordinates (resolved from waypointIds)
     */
    private List<HexVector2> waypoints;

    /**
     * Calculated route through hex grids
     */
    private List<HexVector2> route;

    /**
     * Width in blocks (resolved from width enum)
     */
    private Integer widthBlocks;

    /**
     * Level/height
     */
    private Integer level;

    /**
     * Returns all waypoints including start and end
     */
    public List<HexVector2> getAllWaypoints() {
        List<HexVector2> all = new ArrayList<>();
        if (startPoint != null) {
            all.add(startPoint);
        }
        if (waypoints != null) {
            all.addAll(waypoints);
        }
        if (endPoint != null) {
            all.add(endPoint);
        }
        return all;
    }

    /**
     * Returns true if this flow has a complete route
     */
    public boolean hasRoute() {
        return route != null && !route.isEmpty();
    }
}
