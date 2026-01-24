package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

public enum DistanceRange implements TsEnum {
    DIRECT_BEHIND(1, 1),
    NEAR(1, 10),
    FAR(10, 20);

    private final int from;
    private final int to;

    DistanceRange(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

    public static DistanceRange fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return DistanceRange.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid DistanceRange value: " + value);
        }
    }
}
