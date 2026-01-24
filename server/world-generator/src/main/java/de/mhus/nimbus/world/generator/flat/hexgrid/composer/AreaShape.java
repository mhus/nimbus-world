package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Shape of an area feature.
 * This enum is server-side only and not exposed to TypeScript.
 */
public enum AreaShape {
    CIRCLE,
    LINE,
    RECTANGLE;

    public static AreaShape fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AreaShape.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid AreaShape value: " + value);
        }
    }
}
