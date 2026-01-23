package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

@GenerateTypeScript("enums")
public enum Direction implements TsEnum {
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W,
    NW;

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

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
