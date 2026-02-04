package de.mhus.nimbus.world.generator.composer.pathfinding;

/**
 * Represents the six sides of a flat-top hexagon.
 * Used for edge node placement at district boundaries.
 */
public enum EdgeSide {
    /**
     * North edge (top)
     */
    N,

    /**
     * Northeast edge (upper right)
     */
    NE,

    /**
     * Southeast edge (lower right)
     */
    SE,

    /**
     * South edge (bottom)
     */
    S,

    /**
     * Southwest edge (lower left)
     */
    SW,

    /**
     * Northwest edge (upper left)
     */
    NW;

    /**
     * Get the opposite edge side.
     *
     * @return The opposite edge side
     */
    public EdgeSide opposite() {
        return switch (this) {
            case N -> S;
            case NE -> SW;
            case SE -> NW;
            case S -> N;
            case SW -> NE;
            case NW -> SE;
        };
    }

    /**
     * Get the axial direction vector for this edge (dq, dr).
     * For flat-top hexagons.
     *
     * @return Array [dq, dr]
     */
    public int[] getAxialDirection() {
        return switch (this) {
            case N -> new int[]{0, -1};   // North: r decreases
            case NE -> new int[]{1, -1};  // Northeast: q increases, r decreases
            case SE -> new int[]{1, 0};   // Southeast: q increases
            case S -> new int[]{0, 1};    // South: r increases
            case SW -> new int[]{-1, 1};  // Southwest: q decreases, r increases
            case NW -> new int[]{-1, 0};  // Northwest: q decreases
        };
    }
}
