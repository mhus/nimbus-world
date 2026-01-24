package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

public enum BuildingType implements TsEnum {
    // Residential
    HOUSE("house", BuildingSize.SMALL, 1),
    COTTAGE("cottage", BuildingSize.SMALL, 1),
    MANSION("mansion", BuildingSize.LARGE, 1),

    // Religious
    CHURCH("church", BuildingSize.LARGE, 1),
    CHAPEL("chapel", BuildingSize.MEDIUM, 1),
    SHRINE("shrine", BuildingSize.SMALL, 1),

    // Commercial
    TAVERN("tavern", BuildingSize.MEDIUM, 1),
    INN("inn", BuildingSize.LARGE, 1),
    MARKET("market", BuildingSize.MEDIUM, 1),
    SHOP("shop", BuildingSize.SMALL, 1),
    BLACKSMITH("blacksmith", BuildingSize.MEDIUM, 1),

    // Agricultural
    FARM("farm", BuildingSize.LARGE, 1),
    BARN("barn", BuildingSize.LARGE, 1),
    STABLE("stable", BuildingSize.MEDIUM, 1),
    MILL("mill", BuildingSize.MEDIUM, 1),

    // Defensive
    WALL("wall", BuildingSize.SMALL, 2),
    TOWER("tower", BuildingSize.MEDIUM, 2),
    GATE("gate", BuildingSize.MEDIUM, 2),
    GUARDHOUSE("guardhouse", BuildingSize.MEDIUM, 2),

    // Infrastructure
    WELL("well", BuildingSize.SMALL, 1),
    FOUNTAIN("fountain", BuildingSize.SMALL, 1),
    WAREHOUSE("warehouse", BuildingSize.LARGE, 1),
    TOWN_HALL("town_hall", BuildingSize.LARGE, 1);

    private final String builderName;
    private final BuildingSize defaultSize;
    private final int defaultMaterial;

    BuildingType(String builderName, BuildingSize defaultSize, int defaultMaterial) {
        this.builderName = builderName;
        this.defaultSize = defaultSize;
        this.defaultMaterial = defaultMaterial;
    }

    public String getBuilderName() {
        return builderName;
    }

    public BuildingSize getDefaultSize() {
        return defaultSize;
    }

    public int getDefaultMaterial() {
        return defaultMaterial;
    }

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

    public static BuildingType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BuildingType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid BuildingType value: " + value);
        }
    }

    public enum BuildingSize {
        SMALL(10, 10),
        MEDIUM(20, 20),
        LARGE(30, 30);

        private final int defaultSizeX;
        private final int defaultSizeZ;

        BuildingSize(int defaultSizeX, int defaultSizeZ) {
            this.defaultSizeX = defaultSizeX;
            this.defaultSizeZ = defaultSizeZ;
        }

        public int getDefaultSizeX() {
            return defaultSizeX;
        }

        public int getDefaultSizeZ() {
            return defaultSizeZ;
        }
    }
}
