package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

@GenerateTypeScript("enums")
public enum BiomeSize implements TsEnum {
    SMALL(1, 3),
    MEDIUM(3, 7),
    LARGE(7, 15),
    WIDE(15, 30);

    private final int from;
    private final int to;

    BiomeSize(int from, int to) {
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

    public static BiomeSize fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BiomeSize.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid BiomeSize value: " + value);
        }
    }
}
