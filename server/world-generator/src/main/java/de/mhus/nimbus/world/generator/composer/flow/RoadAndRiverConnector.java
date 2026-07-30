package de.mhus.nimbus.world.generator.composer.flow;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.build.ConnectionResult;
import de.mhus.nimbus.world.generator.composer.filler.HexGridFillResult;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid.EDGE;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Connects roads and rivers across hex grid boundaries
 * Ensures that roads/rivers at one grid's edge connect to the neighboring grid's opposite edge
 */
@Slf4j
public class RoadAndRiverConnector {

    /**
     * Connects roads and rivers across all hex grids
     *
     * @deprecated This connector is no longer used. Flow connections are now handled
     *             directly during composition via FlowComposer writing to the Central Registry.
     *             This method exists only for backwards compatibility and always returns empty results.
     *
     * @param fillResult Result from HexGridFiller with all grids (unused)
     * @param roadConnections List of road connections to apply (unused)
     * @param riverConnections List of river connections to apply (unused)
     * @return Empty result with success=true for backwards compatibility
     */
    @Deprecated
    public ConnectionResult connect(HexGridFillResult fillResult,
                                    List<RoadConnection> roadConnections,
                                    List<RiverConnection> riverConnections) {
        log.warn("RoadAndRiverConnector.connect() is deprecated and does nothing - Flow connections are now handled during composition");
        return ConnectionResult.builder()
                .roadsApplied(0)
                .riversApplied(0)
                .success(true)
                .build();
    }

    /**
     * Gets opposite side for hex grid connection
     */
    public static EDGE getOppositeSide(EDGE side) {
        return switch (side) {
            case NORTH_EAST -> EDGE.SOUTH_WEST;
            case EAST -> EDGE.WEST;
            case SOUTH_EAST -> EDGE.NORTH_WEST;
            case SOUTH_WEST -> EDGE.NORTH_EAST;
            case WEST -> EDGE.EAST;
            case NORTH_WEST -> EDGE.SOUTH_EAST;
        };
    }

    /**
     * Calculates neighbor grid coordinate based on direction.
     * Uses odd-r offset coordinates via HexMathUtil.
     */
    public static HexVector2 getNeighborCoordinate(HexVector2 coord, EDGE side) {
        return HexMathUtil.getNeighborPosition(coord, side);
    }

    /**
     * Determines which EDGE side connects 'from' to 'to' in odd-r offset coordinates.
     * The neighbor delta depends on the row parity of 'from'.
     */
    public static EDGE determineSide(HexVector2 from, HexVector2 to) {
        int dq = to.getQ() - from.getQ();
        int dr = to.getR() - from.getR();
        boolean evenRow = (from.getR() % 2 == 0);

        if (dq == 1 && dr == 0) return EDGE.EAST;
        if (dq == -1 && dr == 0) return EDGE.WEST;

        if (evenRow) {
            if (dq == 0 && dr == 1) return EDGE.NORTH_EAST;
            if (dq == -1 && dr == 1) return EDGE.NORTH_WEST;
            if (dq == 0 && dr == -1) return EDGE.SOUTH_EAST;
            if (dq == -1 && dr == -1) return EDGE.SOUTH_WEST;
        } else {
            if (dq == 1 && dr == 1) return EDGE.NORTH_EAST;
            if (dq == 0 && dr == 1) return EDGE.NORTH_WEST;
            if (dq == 1 && dr == -1) return EDGE.SOUTH_EAST;
            if (dq == 0 && dr == -1) return EDGE.SOUTH_WEST;
        }

        throw new IllegalArgumentException("Invalid hex direction from (" + from.getQ() + "," + from.getR()
            + ") to (" + to.getQ() + "," + to.getR() + "): dq=" + dq + ", dr=" + dr);
    }

}
