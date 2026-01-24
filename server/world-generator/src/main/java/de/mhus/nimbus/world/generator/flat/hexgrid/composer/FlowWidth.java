package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Width categories for flow features.
 * This enum is server-side only and not exposed to TypeScript.
 */
public enum FlowWidth {
    SMALL(2, 4),
    MEDIUM(4, 6),
    LARGE(6, 10);

    private final int from;
    private final int to;

    FlowWidth(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public static FlowWidth fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return FlowWidth.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid FlowWidth value: " + value);
        }
    }
}
