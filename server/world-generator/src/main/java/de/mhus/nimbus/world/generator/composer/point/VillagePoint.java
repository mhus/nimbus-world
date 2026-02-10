package de.mhus.nimbus.world.generator.composer.point;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.build.ComposeContext;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.town.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VillagePoint represents a village positioned at a specific point.
 * Unlike Town (which was area-based with multiple grids), VillagePoint is point-based
 * and positioned on a single HexGrid.
 *
 * The village structure is defined through districts and places, which are composed
 * onto the single grid where the point is located.
 *
 * During composition:
 * 1. PointComposer positions the VillagePoint on a HexGrid
 * 2. VillagePoint creates g_village configuration for that grid
 * 3. VillageBuilder reads g_village and builds the village on the grid
 *
 * Example: A small medieval village positioned at the center of a plains biome
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class VillagePoint extends Point {

    /**
     * Building style that determines the type of buildings in the village.
     * Examples: "medieval", "modern", "fantasy", "ancient", "industrial"
     * This style affects the visual appearance and building types.
     */
    private String style;

    /**
     * The single district that defines this village.
     * The district is positioned exactly on the hex where this point is placed.
     */
    private District district;

    /**
     * Base level for village terrain (typically 95)
     */
    @lombok.Builder.Default
    private int baseLevel = 95;

    /**
     * Whether to automatically fill empty slots with additional places.
     * If true, empty slots will be filled with buildings (houses) or free places (parks, plazas).
     * If false, empty slots remain empty.
     */
    @lombok.Builder.Default
    private boolean fillEmptySlots = true;

    /**
     * Tendency towards buildings vs free places when filling empty slots.
     * Value between 0.0 and 1.0:
     * - 0.0 = all free places (parks, gardens, plazas)
     * - 0.5 = 50% buildings, 50% free places
     * - 1.0 = all buildings (houses)
     *
     * Only applies to slots near streets. Slots far from streets always become free places.
     * Default: 0.7 (70% buildings, 30% free places)
     */
    @lombok.Builder.Default
    private double buildingTendency = 0.7;

    /**
     * Target occupancy rate for the district (total fill rate).
     * Value between 0.0 and 1.0:
     * - 0.0 = no slots filled (only explicit places, no auto-filling)
     * - 0.75 = district should be 75% occupied (if already 75%+ filled, no additional filling)
     * - 1.0 = district should be 100% filled
     *
     * If explicit places already exceed fillRate, no additional slots are filled.
     * Additional slots are filled starting with those nearest to streets.
     * Default: 0.75 (75% total occupancy, 25% remain empty)
     */
    @lombok.Builder.Default
    private double fillRate = 0.75;

    /**
     * Enable debug mode to draw level-250 markers at the center of each place.
     * Useful for visualizing and verifying placement positions.
     * Default: false
     */
    @lombok.Builder.Default
    private boolean debug = false;

    /**
     * Custom parameters for the village.
     */
    private Map<String, String> parameters;

    /**
     * Configures the HexGrid at the given coordinate with village configuration.
     * Called by PointComposer after the point has been positioned.
     *
     * Creates a g_village parameter with the village design (districts, places, streets)
     * that will be used by VillageBuilder to build the actual village on the grid.
     *
     * @param gridCoordinate The coordinate of the grid where this point is placed
     * @param hexGridSize Size of the hex grid
     */
    public void configureHexGrid(HexVector2 gridCoordinate, int hexGridSize) {
        log.debug("Configuring HexGrid for VillagePoint '{}' at [{},{}] with hexGridSize: {}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), hexGridSize);

        // Use default style if not set
        if (style == null || style.isBlank()) {
            style = "medieval";
        }

        // Check if district is defined
        if (district == null) {
            log.warn("VillagePoint '{}' has no district defined, skipping configuration", getName());
            return;
        }

        // Create BuildingIndex (TODO: Load from external source)
        BuildingIndex buildingIndex = new BuildingIndex();
        log.debug("Created BuildingIndex for VillagePoint '{}' (currently empty)", getName());

        // Design the village using TownDesigner
        TownDesigner designer = new TownDesigner(buildingIndex);
        TownDesignResult designResult;

        try {
            log.debug("Starting village design for '{}' with hexGridSize: {}", getName(), hexGridSize);

            // Create a temporary Town object with single district for the designer
            // The district is positioned at (0,0) since it's on the same grid as the point
            District districtWithPosition = District.builder()
                .name(district.getName())
                .title(district.getTitle())
                .slots(district.getSlots())
                .places(district.getPlaces())
                .build();

            Town tempTown = Town.builder()
                .style(style)
                .districts(List.of(districtWithPosition))
                .baseLevel(baseLevel)
                .fillEmptySlots(fillEmptySlots)
                .buildingTendency(buildingTendency)
                .fillRate(fillRate)
                .debug(debug)
                .parameters(parameters)
                .build();

            tempTown.setName(getName());
            tempTown.setTitle(getTitle());

            designResult = designer.design(tempTown, hexGridSize);

            if (!designResult.isSuccess()) {
                log.error("Village design failed for '{}': {}", getName(), designResult.getErrors());
                return;
            }

            log.debug("Village design successful: {} places",
                designResult.getTotalPlaceCount());

        } catch (Exception e) {
            log.error("Exception during village design for '{}'", getName(), e);
            return;
        }

        // Get the single district grid from design result
        if (designResult.getDistrictGrids().isEmpty()) {
            log.warn("No district grid created for VillagePoint '{}'", getName());
            return;
        }

        DistrictGrid districtGrid = designResult.getDistrictGrids().get(0);
        TownGridConfig gridConfig = createGridConfigFromDistrict(districtGrid);

        // Serialize to JSON
        String configJson = serializeToJson(gridConfig);

        // Find or create the FeatureHexGrid for this point's grid
        FeatureHexGrid featureHexGrid = findOrCreateFeatureHexGrid(gridCoordinate);

        // Add g_village parameter with configuration
        featureHexGrid.addParameter("g_village", configJson);

        // Add basic structure parameters
        featureHexGrid.addParameter("structure", "village");
        featureHexGrid.addParameter("structureName", getName());
        featureHexGrid.addParameter("villagePointId", getFeatureId());

        // Copy village parameters to grid
        if (parameters != null) {
            featureHexGrid.getParameters().putAll(parameters);
        }

        log.debug("VillagePoint '{}' configured on grid [{},{}] with {} places, {} streets",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(),
            gridConfig.getPlaces().size(), gridConfig.getStreets().size());
    }

    /**
     * Creates grid configuration from the single district.
     *
     * @param districtGrid The district grid from TownDesigner
     * @return Grid configuration
     */
    private TownGridConfig createGridConfigFromDistrict(DistrictGrid districtGrid) {
        // Convert PlacedPlaces to config
        List<TownGridConfig.PlacedPlaceConfig> placesConfig = districtGrid.getPlacedPlaces().stream()
            .map(this::convertPlacedPlace)
            .toList();

        // Convert Streets to config
        List<TownGridConfig.StreetSegmentConfig> streetsConfig = districtGrid.getStreets().stream()
            .map(this::convertStreetSegment)
            .toList();

        return TownGridConfig.builder()
            .villageName(getName())
            .style(style)
            .districtName(districtGrid.getName())
            .districtTitle(districtGrid.getTitle())
            .baseLevel(baseLevel)
            .places(placesConfig)
            .streets(streetsConfig)
            .build();
    }

    /**
     * Converts PlacedPlace to config
     */
    private TownGridConfig.PlacedPlaceConfig convertPlacedPlace(PlacedPlace placedPlace) {
        Place place = placedPlace.getPlace();

        String type;
        String kind = null;

        if (place instanceof BuildingPlace) {
            type = "building";
            kind = ((BuildingPlace) place).getKind();
        } else if (place instanceof FreePlace) {
            type = "free";
            kind = ((FreePlace) place).getKind() != null ?
                ((FreePlace) place).getKind().name() : null;
        } else if (place instanceof RoadPlace) {
            type = "road";
            kind = ((RoadPlace) place).getKind() != null ?
                ((RoadPlace) place).getKind().name() : null;
        } else if (place instanceof RiverPlace) {
            type = "river";
            kind = ((RiverPlace) place).getKind() != null ?
                ((RiverPlace) place).getKind().name() : "STREAM";
        } else if (place instanceof WallPlace) {
            type = "wall";
            kind = ((WallPlace) place).getKind() != null ?
                ((WallPlace) place).getKind().name() : null;
        } else {
            type = "unknown";
        }

        return TownGridConfig.PlacedPlaceConfig.builder()
            .name(place.getName())
            .type(type)
            .hexQ(placedPlace.getHexQ())
            .hexR(placedPlace.getHexR())
            .localX(placedPlace.getLocalX())
            .localZ(placedPlace.getLocalZ())
            .relativePos(placedPlace.getRelativePos())
            .rotation(placedPlace.getRotation())
            .divider(placedPlace.getDivider())
            .buildingId(placedPlace.getBuildingId())
            .kind(kind)
            .oversized(placedPlace.isOversized())
            .connectionPoint(place.isConnectionPoint())
            .build();
    }

    /**
     * Converts StreetSegment to config
     */
    private TownGridConfig.StreetSegmentConfig convertStreetSegment(
            de.mhus.nimbus.world.generator.composer.flow.StreetSegment segment) {
        return TownGridConfig.StreetSegmentConfig.builder()
            .fromX(segment.getFromX())
            .fromZ(segment.getFromZ())
            .toX(segment.getToX())
            .toZ(segment.getToZ())
            .width(segment.getWidth())
            .type(segment.getType())
            .level(segment.getLevel())
            .build();
    }

    /**
     * Serializes config to JSON string
     */
    private String serializeToJson(TownGridConfig config) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize TownGridConfig to JSON", e);
            return "{}";
        }
    }

    /**
     * Finds or creates the FeatureHexGrid for this point's coordinate.
     * Since points are positioned on a grid, we need to access that grid's configuration.
     *
     * @param gridCoordinate The grid coordinate
     * @return The FeatureHexGrid for this coordinate
     */
    private FeatureHexGrid findOrCreateFeatureHexGrid(HexVector2 gridCoordinate) {
        // Find existing grid in parent feature (biome)
        FeatureHexGrid existing = findHexGrid(gridCoordinate);
        if (existing != null) {
            return existing;
        }

        // Create new grid configuration
        FeatureHexGrid newGrid = FeatureHexGrid.builder()
            .coordinate(gridCoordinate)
            .name(getName() + " at [" + gridCoordinate.getQ() + "," + gridCoordinate.getR() + "]")
            .description("VillagePoint " + getName())
            .build();

        addHexGrid(newGrid);
        return newGrid;
    }

    @Override
    public void applyDefaults() {
        super.applyDefaults();

        if (style == null || style.isBlank()) {
            style = "medieval";
        }

        if (parameters == null) {
            parameters = new HashMap<>();
        }
    }
}
