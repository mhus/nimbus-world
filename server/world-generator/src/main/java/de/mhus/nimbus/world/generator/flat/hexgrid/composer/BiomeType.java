package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

@GenerateTypeScript("enums")
public enum BiomeType implements TsEnum {
    MOUNTAINS("mountain"),
    DESERT("desert"),
    FOREST("forest"),
    PLAINS("plains"),
    SWAMP("swamp"),
    VILLAGE("village"),
    TOWN("town"),
    COAST("coast"),
    ISLAND("island"),
    OCEAN("ocean");

    private final String builderName;

    BiomeType(String builderName) {
        this.builderName = builderName;
    }

    public String getBuilderName() {
        return builderName;
    }

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

    public static BiomeType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BiomeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid BiomeType value: " + value);
        }
    }
}
