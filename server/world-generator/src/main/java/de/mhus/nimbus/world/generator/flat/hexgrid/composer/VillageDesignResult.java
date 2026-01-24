package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Result of village design process
 * Contains HexGrid configurations that can be used by the Composer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class VillageDesignResult {
    private VillageTemplate template;
    private VillageGridLayout layout;
    private int baseLevel;
    private Map<HexVector2, HexGridConfig> gridConfigs;

    /**
     * Converts to Village feature for Composer
     */
    public Village toVillage(String name) {
        // Collect all buildings
        List<VillageBuildingDefinition> buildings = new ArrayList<>();
        for (Map.Entry<HexVector2, HexGridConfig> entry : gridConfigs.entrySet()) {
            HexVector2 gridPos = entry.getKey();
            HexGridConfig config = entry.getValue();

            for (VillagePlotDefinition plot : config.getPlots()) {
                HexVector2 coordinate = HexVector2.builder()
                    .q(gridPos.getQ())
                    .r(gridPos.getR())
                    .build();

                VillagePosition position = VillagePosition.builder()
                    .coordinate(coordinate)
                    .rotation(0)
                    .build();

                VillageBuildingDefinition building = VillageBuildingDefinition.builder()
                    .id(plot.getId())
                    .position(position)
                    .buildingType("building")
                    .size("medium")
                    .build();
                buildings.add(building);
            }
        }

        // Collect all streets
        List<VillageStreetDefinition> streets = new ArrayList<>();
        for (Map.Entry<HexVector2, HexGridConfig> entry : gridConfigs.entrySet()) {
            HexVector2 gridPos = entry.getKey();
            HexGridConfig config = entry.getValue();

            for (VillageRoadDefinition road : config.getInternalRoads()) {
                List<HexVector2> path = new ArrayList<>();

                HexVector2 fromCoord = HexVector2.builder()
                    .q(gridPos.getQ())
                    .r(gridPos.getR())
                    .build();
                path.add(fromCoord);

                HexVector2 toCoord = HexVector2.builder()
                    .q(gridPos.getQ())
                    .r(gridPos.getR())
                    .build();
                path.add(toCoord);

                VillageStreetDefinition street = VillageStreetDefinition.builder()
                    .id("street-" + streets.size())
                    .path(path)
                    .width(road.getWidth())
                    .streetType(road.getType())
                    .build();
                streets.add(street);
            }
        }

        // Calculate size
        int width = layout.getGridPositions().stream()
            .mapToInt(v -> v.getQ()).max().orElse(0) + 1;
        int height = layout.getGridPositions().stream()
            .mapToInt(v -> v.getR()).max().orElse(0) + 1;

        // Build Village feature
        Village village = Village.builder()
            .buildings(buildings)
            .streets(streets)
            .build();
        village.setName(name);
        village.setCalculatedHexGridWidth(width);
        village.setCalculatedHexGridHeight(height);
        village.initialize();

        return village;
    }

    /**
     * Exports as JSON
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize VillageDesignResult to JSON", e);
            throw new RuntimeException("Failed to serialize VillageDesignResult", e);
        }
    }

    /**
     * Loads from JSON
     */
    public static VillageDesignResult fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, VillageDesignResult.class);
        } catch (Exception e) {
            log.error("Failed to deserialize VillageDesignResult from JSON", e);
            throw new RuntimeException("Failed to deserialize VillageDesignResult", e);
        }
    }
}
