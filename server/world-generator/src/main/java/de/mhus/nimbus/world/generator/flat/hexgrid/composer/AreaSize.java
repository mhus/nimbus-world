package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Size categories for area features.
 * This enum is server-side only and not exposed to TypeScript.
 */
public enum AreaSize {
    SMALL(1, 3),
    MEDIUM(3, 7),
    LARGE(7, 15),
    WIDE(15, 30);

    private final int from;
    private final int to;

    AreaSize(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public static AreaSize fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AreaSize.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid AreaSize value: " + value);
        }
    }
}
