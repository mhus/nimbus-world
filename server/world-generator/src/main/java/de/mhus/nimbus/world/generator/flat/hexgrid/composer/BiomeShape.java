package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

@GenerateTypeScript("enums")
public enum BiomeShape implements TsEnum {
    CIRCLE,
    LINE;

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

    public static BiomeShape fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BiomeShape.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid BiomeShape value: " + value);
        }
    }
}
