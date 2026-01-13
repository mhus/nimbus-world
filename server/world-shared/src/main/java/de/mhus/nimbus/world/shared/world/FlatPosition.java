package de.mhus.nimbus.world.shared.world;

/**
 * Represents a 2D position on the XZ-plane (flat world position).
 * Immutable record for block coordinates in the horizontal plane.
 *
 * @param x The X coordinate
 * @param z The Z coordinate
 */
public record FlatPosition(int x, int z) implements Comparable<FlatPosition> {

    /**
     * Compares positions first by x coordinate, then by z coordinate.
     *
     * @param other The other position to compare to
     * @return negative if this < other, 0 if equal, positive if this > other
     */
    @Override
    public int compareTo(FlatPosition other) {
        int cmp = Integer.compare(this.x, other.x);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.z, other.z);
    }

    /**
     * Creates a string representation in "x:z" format.
     *
     * @return String in format "x:z"
     */
    public String toKey() {
        return x + ":" + z;
    }
}
