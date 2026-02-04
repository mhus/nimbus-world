package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class Village extends Structure implements BuildFeature {
    private List<VillageBuildingDefinition> buildings;
    private List<VillageStreetDefinition> streets;
    private Map<String, String> parameters;

    /**
     * Building style that determines the type of buildings in the village.
     * Examples: "medieval", "modern", "fantasy", "ancient", "industrial"
     * This style affects the visual appearance and building types.
     */
    private String style;

    /**
     * List of districts that make up this village.
     * Each district represents one grid in the village with specific slot configuration.
     * Districts are positioned using axial hex coordinates relative to village origin.
     */
    private List<District> districts;

    /**
     * List of external connection points for this village.
     * These synthetic points are placed in neighboring grids outside the village
     * to provide entry/exit points for external roads.
     * Generated automatically by VillageExternalConnectionGenerator.
     */
    private List<VillageConnectionPoint> externalConnectionPoints;

    /**
     * Base level for village terrain (typically 95)
     */
    @Builder.Default
    private int baseLevel = 95;

    /**
     * Whether to automatically fill empty slots with additional places.
     * If true, empty slots will be filled with buildings (houses) or free places (parks, plazas).
     * If false, empty slots remain empty.
     */
    @Builder.Default
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
    @Builder.Default
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
    @Builder.Default
    private double fillRate = 0.75;

    /**
     * Enable debug mode to draw level-250 markers at the center of each place.
     * Useful for visualizing and verifying placement positions.
     * Default: false
     */
    @Builder.Default
    private boolean debug = false;

    // Note: Village uses Structure.Composed (no Village-specific calculated fields yet)

    /**
     * Applies village-specific default configuration from StructureType
     */
    @Override
    protected void applyStructureDefaults(Map<String, String> defaults) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        // Apply defaults from StructureType enum
        if (defaults != null) {
            defaults.forEach(parameters::putIfAbsent);
        }

        // Set default builder
        if (getType() != null) {
            parameters.putIfAbsent("g_builder", getType().getDefaultBuilder());
        }
    }

    /**
     * Builds this village internally.
     * Creates ALL HexGrids with complete parameters, with RELATIVE coordinates to village origin (0,0).
     * The parent Composer will later transform these relative coordinates to absolute world coordinates.
     *
     * Implements BuildFeature interface for cascaded composites.
     *
     * TODO: Implement new village building logic based on style instead of templateName.
     *
     * @param context Build context
     * @return CompositionResult with village design information
     */
    @Override
    public CompositionResult build(BuildContext context) {
        // TODO: Implement new village building logic
        // For now: Return empty successful result

        if (style == null || style.isBlank()) {
            // Use default style if not set
            style = "medieval";
        }

        try {
            // TODO: New village building logic will be implemented here
            // Will use 'style' instead of 'templateName'

            // For now: Return successful result without building anything
            // This allows the system to continue working while new logic is being developed

            return CompositionResult.builder()
                .success(true)
                .totalStructures(1)
                .totalGrids(0)
                .build();

        } catch (Exception e) {
            return CompositionResult.failed("Village build failed: " + e.getMessage());
        }
    }

    /**
     * Configures HexGrids for this village with buildings and streets.
     * Creates FeatureHexGrid configurations for each village grid.
     * Uses VillageDesigner to generate the village layout and attaches configuration as g_village parameter.
     *
     * @param coordinates Grid coordinates (not used for villages, districts define positions)
     * @param hexGridSize Size of each hex grid from world configuration
     */
    public void configureHexGrids(List<HexVector2> coordinates, int hexGridSize) {
        log.info("Configuring HexGrids for village '{}' with {} districts (hexGridSize: {})",
            getName(), districts != null ? districts.size() : 0, hexGridSize);

        // Clear existing configurations
        if (getHexGrids() != null) {
            getHexGrids().clear();
        }

        // Check if districts are defined
        if (districts == null || districts.isEmpty()) {
            log.warn("Village '{}' has no districts defined, creating fallback grids", getName());
            createFallbackGrids(coordinates);
            return;
        }

        // Create BuildingIndex (TODO: Load from external source)
        BuildingIndex buildingIndex = new BuildingIndex();
        // TODO: Load buildings from file/database
        log.debug("Created BuildingIndex for village '{}' (currently empty)", getName());

        // Design the village using VillageDesigner
        VillageDesigner designer = new VillageDesigner(buildingIndex);
        VillageDesignResult designResult;

        try {
            log.debug("Starting village design for '{}' with hexGridSize: {}", getName(), hexGridSize);
            designResult = designer.design(this, hexGridSize);

            if (!designResult.isSuccess()) {
                log.error("Village design failed for '{}': {}", getName(), designResult.getErrors());
                // Still create grids with basic parameters as fallback
                log.warn("Creating fallback grids for village '{}'", getName());
                createFallbackGridsFromDistricts();
                return;
            }

            log.info("Village design successful: {} districts", designResult.getDistrictCount());

        } catch (Exception e) {
            log.error("Exception during village design for '{}'", getName(), e);
            log.warn("Creating fallback grids for village '{}'", getName());
            createFallbackGridsFromDistricts();
            return;
        }

        log.info("Village design successful: {} districts, {} places",
            designResult.getDistrictCount(), designResult.getTotalPlaceCount());

        // Create FeatureHexGrid for each DistrictGrid with configuration
        for (DistrictGrid districtGrid : designResult.getDistrictGrids()) {
            HexVector2 relativePos = districtGrid.getGridPosition();

            // Find the corresponding absolute coordinate (coordinates are relative to village center)
            // For now, we assume coordinates list matches districtGrids order
            // TODO: Proper coordinate mapping

            // Create grid configuration
            VillageGridConfig gridConfig = createGridConfig(districtGrid);

            // Serialize to JSON
            String configJson = serializeToJson(gridConfig);

            // Create FeatureHexGrid
            FeatureHexGrid featureHexGrid = FeatureHexGrid.builder()
                .coordinate(relativePos) // Will be translated to absolute by StructureComposer
                .name(getName() + " - " + districtGrid.getName())
                .description("District '" + districtGrid.getTitle() + "' of " + getName())
                .build();

            // Add g_village parameter with configuration
            featureHexGrid.addParameter("g_village", configJson);

            // Add basic structure parameters
            featureHexGrid.addParameter("structure", "village");
            featureHexGrid.addParameter("structureName", getName());
            featureHexGrid.addParameter("districtName", districtGrid.getName());

            // Add debug flag if enabled
            if (debug) {
                featureHexGrid.addParameter("g_village_debug", "true");
                log.debug("Debug mode enabled for district '{}'", districtGrid.getName());
            }

            // Copy village parameters to grid
            if (parameters != null) {
                featureHexGrid.getParameters().putAll(parameters);
            }

            // Add to this feature
            addHexGrid(featureHexGrid);

            log.debug("Configured grid for district '{}' at [{},{}] with {} places, {} streets",
                districtGrid.getName(), relativePos.getQ(), relativePos.getR(),
                districtGrid.getPlacedPlaces().size(), districtGrid.getStreets().size());
        }

        log.info("Village '{}' configured: {} grids created", getName(), getHexGrids().size());
    }

    /**
     * Creates fallback grids when design fails
     */
    private void createFallbackGrids(List<HexVector2> coordinates) {
        log.warn("Creating {} fallback grids for village '{}'", coordinates.size(), getName());
        for (HexVector2 coord : coordinates) {
            FeatureHexGrid featureHexGrid = FeatureHexGrid.builder()
                .coordinate(coord)
                .name(getName() + " [" + coord.getQ() + "," + coord.getR() + "]")
                .description("Fallback grid for " + getName())
                .build();

            featureHexGrid.addParameter("structure", "village");
            featureHexGrid.addParameter("structureName", getName());

            addHexGrid(featureHexGrid);
        }
    }

    /**
     * Creates fallback grids from district positions when design fails
     */
    private void createFallbackGridsFromDistricts() {
        if (districts == null || districts.isEmpty()) {
            log.warn("No districts to create fallback grids from");
            return;
        }

        log.warn("Creating {} fallback grids from districts for village '{}'",
            districts.size(), getName());

        // Resolve district positions using VillageDesigner
        Map<String, HexVector2> districtPositions = VillageDesigner.resolveDistrictPositions(districts);

        for (District district : districts) {
            HexVector2 position = districtPositions.get(district.getName());
            if (position == null) {
                log.warn("District '{}' could not be positioned, skipping fallback", district.getName());
                continue;
            }

            FeatureHexGrid featureHexGrid = FeatureHexGrid.builder()
                .coordinate(position)
                .name(getName() + " - " + district.getName())
                .description("Fallback grid for district " + district.getName())
                .build();

            featureHexGrid.addParameter("structure", "village");
            featureHexGrid.addParameter("structureName", getName());
            featureHexGrid.addParameter("districtName", district.getName());

            // Add minimal g_village parameter
            String minimalConfig = String.format(
                "{\"villageName\":\"%s\",\"districtName\":\"%s\",\"baseLevel\":%d,\"places\":[],\"streets\":[]}",
                getName(), district.getName(), baseLevel);
            featureHexGrid.addParameter("g_village", minimalConfig);

            addHexGrid(featureHexGrid);
        }
    }

    /**
     * Creates VillageGridConfig from DistrictGrid
     */
    private VillageGridConfig createGridConfig(DistrictGrid districtGrid) {
        // Convert PlacedPlaces to config
        List<VillageGridConfig.PlacedPlaceConfig> placesConfig = districtGrid.getPlacedPlaces().stream()
            .map(this::convertPlacedPlace)
            .toList();

        // Convert Streets to config
        List<VillageGridConfig.StreetSegmentConfig> streetsConfig = districtGrid.getStreets().stream()
            .map(this::convertStreetSegment)
            .toList();

        return VillageGridConfig.builder()
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
    private VillageGridConfig.PlacedPlaceConfig convertPlacedPlace(PlacedPlace placedPlace) {
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

        return VillageGridConfig.PlacedPlaceConfig.builder()
            .name(place.getName())
            .type(type)
            .hexQ(placedPlace.getHexQ())
            .hexR(placedPlace.getHexR())
            .localX(placedPlace.getLocalX())
            .localZ(placedPlace.getLocalZ())
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
    private VillageGridConfig.StreetSegmentConfig convertStreetSegment(StreetSegment segment) {
        return VillageGridConfig.StreetSegmentConfig.builder()
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
    private String serializeToJson(VillageGridConfig config) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize VillageGridConfig to JSON", e);
            return "{}";
        }
    }
}
