package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.types.TsEnum;

import java.util.HashMap;
import java.util.Map;

public enum VillageStyle implements TsEnum {
    MEDIEVAL("medieval"),     // Klassisches mittelalterliches Dorf
    FARMING("farming"),       // Landwirtschaftlich geprägt
    TRADING("trading"),       // Handelszentrum
    MINING("mining"),         // Bergbau-Siedlung
    FISHING("fishing"),       // Fischerdorf
    RELIGIOUS("religious");   // Kloster/Kirchen-Fokus

    private final String styleName;

    VillageStyle(String styleName) {
        this.styleName = styleName;
    }

    public String getStyleName() {
        return styleName;
    }

    @Override
    public String tsString() {
        return name().toLowerCase();
    }

    /**
     * Returns typical building distribution for this style and size
     */
    public Map<BuildingType, Integer> getTypicalBuildings(VillageSize size) {
        Map<BuildingType, Integer> buildings = new HashMap<>();

        switch (this) {
            case MEDIEVAL:
                buildings.put(BuildingType.CHURCH, 1);
                buildings.put(BuildingType.TAVERN, 1);
                buildings.put(BuildingType.HOUSE, size.getMinBuildings() - 2);
                if (size.ordinal() >= VillageSize.TOWN.ordinal()) {
                    buildings.put(BuildingType.TOWN_HALL, 1);
                    buildings.put(BuildingType.MARKET, 1);
                }
                break;

            case FARMING:
                buildings.put(BuildingType.FARM, size.getMinBuildings() / 2);
                buildings.put(BuildingType.BARN, size.getMinBuildings() / 3);
                buildings.put(BuildingType.HOUSE, size.getMinBuildings() / 4);
                buildings.put(BuildingType.MILL, 1);
                break;

            case TRADING:
                buildings.put(BuildingType.MARKET, 2);
                buildings.put(BuildingType.SHOP, size.getMinBuildings() / 3);
                buildings.put(BuildingType.WAREHOUSE, 2);
                buildings.put(BuildingType.INN, 1);
                buildings.put(BuildingType.HOUSE, size.getMinBuildings() / 4);
                break;

            case MINING:
                buildings.put(BuildingType.WAREHOUSE, 3);
                buildings.put(BuildingType.BLACKSMITH, 2);
                buildings.put(BuildingType.HOUSE, size.getMinBuildings() - 5);
                buildings.put(BuildingType.TAVERN, 1);
                break;

            case FISHING:
                buildings.put(BuildingType.WAREHOUSE, 2);
                buildings.put(BuildingType.MARKET, 1);
                buildings.put(BuildingType.HOUSE, size.getMinBuildings() - 4);
                buildings.put(BuildingType.TAVERN, 1);
                break;

            case RELIGIOUS:
                buildings.put(BuildingType.CHURCH, 1);
                buildings.put(BuildingType.CHAPEL, 2);
                buildings.put(BuildingType.SHRINE, 3);
                buildings.put(BuildingType.HOUSE, size.getMinBuildings() - 6);
                break;
        }

        return buildings;
    }

    public static VillageStyle fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VillageStyle.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid VillageStyle value: " + value);
        }
    }
}
