package de.mhus.nimbus.world.generator.composer.area;

import java.util.Locale;
import lombok.Getter;

@Getter
public enum DistanceRange {
    DIRECT_BEHIND(1, 1),
    NEAR(1, 5),
    NORMAL(5, 10),
    FAR(10, 20);

    private final int from;
    private final int to;

    DistanceRange(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public static DistanceRange fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return DistanceRange.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid DistanceRange value: " + value, e);
        }
    }
}
