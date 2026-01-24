package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

public enum VillageSize implements TsEnum {
    HAMLET(1, 5, 10),           // 1 Grid, 5-10 buildings
    SMALL_VILLAGE(2, 10, 15),   // 2 Grids, 10-15 buildings
    VILLAGE(3, 15, 25),         // 3 Grids, 15-25 buildings
    TOWN(5, 25, 40),            // 5 Grids (cross), 25-40 buildings
    LARGE_TOWN(7, 40, 60);      // 7 Grids (hex), 40-60 buildings

    private final int gridCount;
    private final int minBuildings;
    private final int maxBuildings;

    VillageSize(int gridCount, int minBuildings, int maxBuildings) {
        this.gridCount = gridCount;
        this.minBuildings = minBuildings;
        this.maxBuildings = maxBuildings;
    }

    public int getGridCount() {
        return gridCount;
    }

    public int getMinBuildings() {
        return minBuildings;
    }

    public int getMaxBuildings() {
        return maxBuildings;
    }

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

    public static VillageSize fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VillageSize.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid VillageSize value: " + value);
        }
    }
}
