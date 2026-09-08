package de.mhus.nimbus.world.generator.composer.area;

import java.util.Locale;

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
            return AreaShape.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid AreaShape value: " + value, e);
        }
    }
}
