package de.mhus.nimbus.world.generator.composer.point;

public enum Direction  {
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
            return Direction.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Direction value: " + value);
        }
    }
}
