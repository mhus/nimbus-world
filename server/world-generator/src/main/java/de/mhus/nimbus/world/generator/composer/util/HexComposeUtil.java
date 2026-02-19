package de.mhus.nimbus.world.generator.composer.util;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Z-flip-correct hex neighbor utility for the Composition pipeline.
 *
 * In the 3D world, North = Z- (negative Z axis). hexToCartesian maps r to z with a positive factor,
 * so r+ = z+ = South in 3D. Therefore NORTH directions must use r- and SOUTH directions must use r+.
 *
 * HexMathUtil has NORTH = r+ which is incorrect for 3D world orientation.
 * This class flips the N/S labels so that:
 *   NORTH_EAST/NORTH_WEST → r- (= z- = North in 3D)
 *   SOUTH_EAST/SOUTH_WEST → r+ (= z+ = South in 3D)
 *   EAST/WEST → unchanged
 *
 * Uses odd-r offset coordinates throughout.
 */
@UtilityClass
public class HexComposeUtil {

    /**
     * Returns the neighbor position for the given EDGE direction with Z-flip-correct labels.
     *
     * Even row (r%2==0):
     *   EAST:       (q+1, r)
     *   WEST:       (q-1, r)
     *   NORTH_EAST: (q,   r-1)   ← r- = North in 3D
     *   NORTH_WEST: (q-1, r-1)
     *   SOUTH_EAST: (q,   r+1)   ← r+ = South in 3D
     *   SOUTH_WEST: (q-1, r+1)
     *
     * Odd row (r%2!=0):
     *   EAST:       (q+1, r)
     *   WEST:       (q-1, r)
     *   NORTH_EAST: (q+1, r-1)
     *   NORTH_WEST: (q,   r-1)
     *   SOUTH_EAST: (q+1, r+1)
     *   SOUTH_WEST: (q,   r+1)
     */
    public static HexVector2 getNeighborPosition(HexVector2 position, WHexGrid.EDGE edge) {
        int q = position.getQ();
        int r = position.getR();
        boolean evenRow = (r % 2 == 0);

        return switch (edge) {
            case EAST -> HexVector2.builder().q(q + 1).r(r).build();
            case WEST -> HexVector2.builder().q(q - 1).r(r).build();
            case NORTH_EAST -> evenRow
                    ? HexVector2.builder().q(q).r(r - 1).build()
                    : HexVector2.builder().q(q + 1).r(r - 1).build();
            case NORTH_WEST -> evenRow
                    ? HexVector2.builder().q(q - 1).r(r - 1).build()
                    : HexVector2.builder().q(q).r(r - 1).build();
            case SOUTH_EAST -> evenRow
                    ? HexVector2.builder().q(q).r(r + 1).build()
                    : HexVector2.builder().q(q + 1).r(r + 1).build();
            case SOUTH_WEST -> evenRow
                    ? HexVector2.builder().q(q - 1).r(r + 1).build()
                    : HexVector2.builder().q(q).r(r + 1).build();
        };
    }

    /**
     * Returns all 6 neighbors for the given hex coordinate.
     */
    public static List<HexVector2> getNeighbors(HexVector2 coord) {
        List<HexVector2> neighbors = new ArrayList<>(6);
        for (WHexGrid.EDGE edge : WHexGrid.EDGE.values()) {
            neighbors.add(getNeighborPosition(coord, edge));
        }
        return neighbors;
    }

    /**
     * Determines which EDGE side connects 'from' to 'to' using Z-flip-correct labels.
     */
    public static WHexGrid.EDGE determineSide(HexVector2 from, HexVector2 to) {
        int dq = to.getQ() - from.getQ();
        int dr = to.getR() - from.getR();
        boolean evenRow = (from.getR() % 2 == 0);

        if (dq == 1 && dr == 0) return WHexGrid.EDGE.EAST;
        if (dq == -1 && dr == 0) return WHexGrid.EDGE.WEST;

        if (evenRow) {
            if (dq == 0 && dr == -1) return WHexGrid.EDGE.NORTH_EAST;
            if (dq == -1 && dr == -1) return WHexGrid.EDGE.NORTH_WEST;
            if (dq == 0 && dr == 1) return WHexGrid.EDGE.SOUTH_EAST;
            if (dq == -1 && dr == 1) return WHexGrid.EDGE.SOUTH_WEST;
        } else {
            if (dq == 1 && dr == -1) return WHexGrid.EDGE.NORTH_EAST;
            if (dq == 0 && dr == -1) return WHexGrid.EDGE.NORTH_WEST;
            if (dq == 1 && dr == 1) return WHexGrid.EDGE.SOUTH_EAST;
            if (dq == 0 && dr == 1) return WHexGrid.EDGE.SOUTH_WEST;
        }

        throw new IllegalArgumentException("Invalid hex direction from (" + from.getQ() + "," + from.getR()
                + ") to (" + to.getQ() + "," + to.getR() + "): dq=" + dq + ", dr=" + dr);
    }

    /**
     * Hex distance using cube coordinates. Direction-independent, same as Flow.hexDistance.
     */
    public static int hexDistance(HexVector2 a, HexVector2 b) {
        int aCubeQ = a.getQ() - (a.getR() - (a.getR() & 1)) / 2;
        int aCubeR = a.getR();
        int aCubeS = -aCubeQ - aCubeR;

        int bCubeQ = b.getQ() - (b.getR() - (b.getR() & 1)) / 2;
        int bCubeR = b.getR();
        int bCubeS = -bCubeQ - bCubeR;

        return Math.max(Math.max(
                Math.abs(aCubeQ - bCubeQ),
                Math.abs(aCubeR - bCubeR)),
                Math.abs(aCubeS - bCubeS));
    }
}
