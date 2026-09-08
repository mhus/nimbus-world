package de.mhus.nimbus.world.generator.composer.point;

import java.util.Locale;

public enum Direction {
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W,
    NW;

    public static Direction fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Direction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Direction value: " + value, e);
        }
    }
}
