package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Snap mode defining how a Point should be positioned relative to a target feature.
 */
public enum SnapMode {
    /**
     * Place the point inside the target feature's boundaries.
     * The point will be positioned within one of the hexagons assigned to the target.
     * Example: Place a city inside a plains biome
     */
    INSIDE,

    /**
     * Place the point at the edge/border of the target feature.
     * The point will be positioned in a hexagon that is at the boundary of the target.
     * Example: Place a coastal lighthouse at the edge of a coast biome
     */
    EDGE,

    /**
     * Place the point outside but near the target feature.
     * The point will be positioned in a hexagon adjacent to the target feature.
     * Example: Place a watchtower outside a city walls
     */
    OUTSIDE_NEAR;

    public static SnapMode fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SnapMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid SnapMode value: " + value);
        }
    }
}
