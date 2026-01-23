package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VillageDesigner generates concrete village layouts from templates
 * Converts template definitions into HexGrid configurations with proper coordinate mapping
 */
@Slf4j
public class VillageDesigner {

    private static final int FLAT_SIZE = 512;  // Standard HexGrid Flat Size

    /**
     * Designs a concrete village from a template
     */
    public VillageDesignResult design(VillageTemplate template, int baseLevel) {
        log.info("Designing village: {} ({})", template.getName(), template.getSize());

        VillageDesignResult result = new VillageDesignResult();
        result.setTemplate(template);
        result.setBaseLevel(baseLevel);

        // 1. Layout initialisieren
        VillageGridLayout layout = template.getLayout();
        if (layout == null) {
            // Create default layout based on size
            layout = createDefaultLayout(template.getSize());
        }
        result.setLayout(layout);

        // 2. HexGrid-Konfigurationen erstellen
        Map<HexVector2, HexGridConfig> gridConfigs = new HashMap<>();
        for (HexVector2 gridPos : layout.getGridPositions()) {
            HexGridConfig config = createGridConfig(gridPos, baseLevel);
            gridConfigs.put(gridPos, config);
        }

        // 3. Plaza platzieren (in Center-Grid)
        GridLocalCoordinate plazaCoord = null;
        if (template.getPlaza() != null) {
            PlazaDefinition plaza = template.getPlaza();
            plazaCoord = layout.toAbsolute(
                plaza.getLocalX(), plaza.getLocalZ(), FLAT_SIZE);

            HexGridConfig centerConfig = gridConfigs.get(plazaCoord.getGridPosition());
            if (centerConfig != null) {
                centerConfig.setRoadConfig(createPlazaRoadConfig(plaza, plazaCoord));
            }
        }

        // 4. Gebäude platzieren und Road-Centers pro Grid sammeln
        Map<HexVector2, GridLocalCoordinate> gridRoadCenters = new HashMap<>();

        // Bestimme Road-Center für jedes Grid
        for (HexVector2 gridPos : layout.getGridPositions()) {
            GridLocalCoordinate roadCenter;

            // Wenn Plaza in diesem Grid ist, nutze Plaza-Position
            if (plazaCoord != null &&
                plazaCoord.getGridPosition().getQ() == gridPos.getQ() &&
                plazaCoord.getGridPosition().getR() == gridPos.getR()) {
                roadCenter = plazaCoord;
            } else {
                // Sonst nutze Grid-Center als Road-Center
                roadCenter = GridLocalCoordinate.builder()
                    .gridPosition(gridPos)
                    .localX(FLAT_SIZE / 2)
                    .localZ(FLAT_SIZE / 2)
                    .build();
            }

            gridRoadCenters.put(gridPos, roadCenter);
        }

        if (template.getBuildings() != null) {
            for (TemplateBuildingDefinition building : template.getBuildings()) {
                GridLocalCoordinate coord = layout.toAbsolute(
                    building.getLocalX(), building.getLocalZ(), FLAT_SIZE);

                HexGridConfig gridConfig = gridConfigs.get(coord.getGridPosition());
                if (gridConfig != null) {
                    VillagePlotDefinition plot = createPlot(building, coord, baseLevel);
                    gridConfig.addPlot(plot);

                    // Optional: Straße zum Road-Center des Grids
                    if (building.isConnectToPlaza()) {
                        GridLocalCoordinate roadCenter = gridRoadCenters.get(coord.getGridPosition());
                        if (roadCenter != null) {
                            addPlotToPlazaConnection(gridConfig, plot, coord, roadCenter, baseLevel);
                        }
                    }
                }
            }
        }

        // 5. Straßen platzieren (inkl. Grid-Boundary Roads)
        if (template.getStreets() != null) {
            for (TemplateStreetDefinition street : template.getStreets()) {
                placeStreet(street, layout, gridConfigs, baseLevel);
            }
        }

        // 6. Grid-Boundary Roads für zusammenhängende Straßen
        connectGridBoundaryRoads(gridConfigs, layout);

        result.setGridConfigs(gridConfigs);
        log.info("Village design completed: {} grids, {} buildings",
            gridConfigs.size(),
            template.getBuildings() != null ? template.getBuildings().size() : 0);

        return result;
    }

    /**
     * Creates default layout based on village size
     */
    private VillageGridLayout createDefaultLayout(VillageSize size) {
        switch (size) {
            case HAMLET:
                return VillageGridLayout.createHamlet();
            case SMALL_VILLAGE:
                return VillageGridLayout.create2x1();
            case VILLAGE:
                return VillageGridLayout.create3x1();
            case TOWN:
                return VillageGridLayout.create5Cross();
            case LARGE_TOWN:
                return VillageGridLayout.create7Hex();
            default:
                return VillageGridLayout.createHamlet();
        }
    }

    /**
     * Creates a basic grid configuration
     */
    private HexGridConfig createGridConfig(HexVector2 gridPos, int baseLevel) {
        return HexGridConfig.builder()
            .gridPosition(gridPos)
            .baseLevel(baseLevel)
            .plots(new ArrayList<>())
            .internalRoads(new ArrayList<>())
            .boundaryRoads(new ArrayList<>())
            .build();
    }

    /**
     * Creates plaza road configuration
     */
    private RoadConfig createPlazaRoadConfig(PlazaDefinition plaza, GridLocalCoordinate plazaCoord) {
        return RoadConfig.builder()
            .lx(plazaCoord.getLocalX())
            .lz(plazaCoord.getLocalZ())
            .level(plaza.getLevel())
            .plazaSize(plaza.getSize())
            .plazaMaterial(plaza.getMaterial())
            .routes(new ArrayList<>())
            .build();
    }

    /**
     * Creates a plot from building definition
     */
    private VillagePlotDefinition createPlot(TemplateBuildingDefinition building,
                                             GridLocalCoordinate coord,
                                             int baseLevel) {
        BuildingType.BuildingSize size = parseBuildingSize(building.getSize());

        return VillagePlotDefinition.builder()
            .id(building.getId())
            .lx(coord.getLocalX())
            .lz(coord.getLocalZ())
            .sizeX(size.getDefaultSizeX())
            .sizeZ(size.getDefaultSizeZ())
            .level(baseLevel)
            .material(building.getType().getDefaultMaterial())
            .build();
    }

    /**
     * Parses building size string
     */
    private BuildingType.BuildingSize parseBuildingSize(String sizeStr) {
        if (sizeStr == null) {
            return BuildingType.BuildingSize.MEDIUM;
        }
        switch (sizeStr.toLowerCase()) {
            case "small":
                return BuildingType.BuildingSize.SMALL;
            case "medium":
                return BuildingType.BuildingSize.MEDIUM;
            case "large":
                return BuildingType.BuildingSize.LARGE;
            default:
                return BuildingType.BuildingSize.MEDIUM;
        }
    }

    /**
     * Adds connection from plot to plaza by creating an actual road
     */
    private void addPlotToPlazaConnection(HexGridConfig gridConfig,
                                          VillagePlotDefinition plot,
                                          GridLocalCoordinate buildingCoord,
                                          GridLocalCoordinate plazaCoord,
                                          int baseLevel) {
        // Check if building and plaza are in the same grid
        if (buildingCoord.getGridPosition().getQ() != plazaCoord.getGridPosition().getQ() ||
            buildingCoord.getGridPosition().getR() != plazaCoord.getGridPosition().getR()) {
            // Different grids - would need boundary road, skip for now
            log.debug("Building and plaza in different grids, skipping direct connection");
            return;
        }

        // Create a road from plaza center to building
        VillageRoadDefinition road = VillageRoadDefinition.builder()
            .fromX(plazaCoord.getLocalX())
            .fromZ(plazaCoord.getLocalZ())
            .toX(buildingCoord.getLocalX())
            .toZ(buildingCoord.getLocalZ())
            .width(3)
            .type("street")
            .level(baseLevel)
            .build();

        // Add road to grid config
        int roadIndex = gridConfig.getInternalRoads().size();
        gridConfig.addInternalRoad(road);

        // Connect plot to this road
        plot.setRoad(roadIndex);

        log.debug("Created road from plaza [{},{}] to building [{},{}], road index: {}",
            plazaCoord.getLocalX(), plazaCoord.getLocalZ(),
            buildingCoord.getLocalX(), buildingCoord.getLocalZ(),
            roadIndex);
    }

    /**
     * Places a street that may span multiple grids
     */
    private void placeStreet(TemplateStreetDefinition street,
                             VillageGridLayout layout,
                             Map<HexVector2, HexGridConfig> gridConfigs,
                             int baseLevel) {

        List<LocalPosition> path = street.getPath();
        if (path == null || path.size() < 2) {
            return;
        }

        for (int i = 0; i < path.size() - 1; i++) {
            LocalPosition from = path.get(i);
            LocalPosition to = path.get(i + 1);

            GridLocalCoordinate fromCoord = layout.toAbsolute(from.getLocalX(), from.getLocalZ(), FLAT_SIZE);
            GridLocalCoordinate toCoord = layout.toAbsolute(to.getLocalX(), to.getLocalZ(), FLAT_SIZE);

            // Gleiche Grid: Normale Straße innerhalb des Grids
            if (fromCoord.getGridPosition().getQ() == toCoord.getGridPosition().getQ() &&
                fromCoord.getGridPosition().getR() == toCoord.getGridPosition().getR()) {

                HexGridConfig config = gridConfigs.get(fromCoord.getGridPosition());
                if (config != null) {
                    VillageRoadDefinition road = createRoad(fromCoord, toCoord, street, baseLevel);
                    config.addInternalRoad(road);
                }
            }
            // Verschiedene Grids: Boundary Road
            else {
                addBoundaryRoad(fromCoord, toCoord, street, gridConfigs, baseLevel);
            }
        }
    }

    /**
     * Creates a road definition
     */
    private VillageRoadDefinition createRoad(GridLocalCoordinate from,
                                             GridLocalCoordinate to,
                                             TemplateStreetDefinition street,
                                             int level) {
        return VillageRoadDefinition.builder()
            .fromX(from.getLocalX())
            .fromZ(from.getLocalZ())
            .toX(to.getLocalX())
            .toZ(to.getLocalZ())
            .width(street.getWidth())
            .type(street.getType())
            .level(level)
            .build();
    }

    /**
     * Adds boundary road between two grids
     */
    private void addBoundaryRoad(GridLocalCoordinate from,
                                  GridLocalCoordinate to,
                                  TemplateStreetDefinition street,
                                  Map<HexVector2, HexGridConfig> gridConfigs,
                                  int baseLevel) {

        // Determine direction between grids
        WHexGrid.SIDE sideFrom = determineSide(from.getGridPosition(), to.getGridPosition());
        if (sideFrom == null) {
            log.warn("Cannot determine side between grids {} and {}, skipping boundary road",
                from.getGridPosition(), to.getGridPosition());
            return;
        }

        WHexGrid.SIDE sideTo = getOppositeSide(sideFrom);

        // From-Grid: Road zur Seite
        HexGridConfig fromConfig = gridConfigs.get(from.getGridPosition());
        if (fromConfig != null && fromConfig.getRoadConfig() != null) {
            RouteDefinition route = RouteDefinition.builder()
                .side(sideFrom)
                .width(street.getWidth())
                .level(baseLevel)
                .type(street.getType())
                .build();
            fromConfig.getRoadConfig().getRoutes().add(route);
        }

        // To-Grid: Road von gegenüberliegender Seite
        HexGridConfig toConfig = gridConfigs.get(to.getGridPosition());
        if (toConfig != null && toConfig.getRoadConfig() != null) {
            RouteDefinition route = RouteDefinition.builder()
                .side(sideTo)
                .width(street.getWidth())
                .level(baseLevel)
                .type(street.getType())
                .build();
            toConfig.getRoadConfig().getRoutes().add(route);
        }
    }

    /**
     * Determines HexGrid side based on grid positions
     */
    private WHexGrid.SIDE determineSide(HexVector2 from, HexVector2 to) {
        int dq = to.getQ() - from.getQ();
        int dr = to.getR() - from.getR();

        // Hex Grid Directions (axial coordinates)
        if (dq == 1 && dr == -1) return WHexGrid.SIDE.NORTH_EAST;
        if (dq == 1 && dr == 0) return WHexGrid.SIDE.EAST;
        if (dq == 0 && dr == 1) return WHexGrid.SIDE.SOUTH_EAST;
        if (dq == -1 && dr == 1) return WHexGrid.SIDE.SOUTH_WEST;
        if (dq == -1 && dr == 0) return WHexGrid.SIDE.WEST;
        if (dq == 0 && dr == -1) return WHexGrid.SIDE.NORTH_WEST;

        log.warn("Invalid grid direction: dq={}, dr={}", dq, dr);
        return null;
    }

    /**
     * Gets opposite side
     */
    private WHexGrid.SIDE getOppositeSide(WHexGrid.SIDE side) {
        switch (side) {
            case NORTH_EAST: return WHexGrid.SIDE.SOUTH_WEST;
            case EAST: return WHexGrid.SIDE.WEST;
            case SOUTH_EAST: return WHexGrid.SIDE.NORTH_WEST;
            case SOUTH_WEST: return WHexGrid.SIDE.NORTH_EAST;
            case WEST: return WHexGrid.SIDE.EAST;
            case NORTH_WEST: return WHexGrid.SIDE.SOUTH_EAST;
            default: return null;
        }
    }

    /**
     * Connects boundary roads for seamless grid transitions
     */
    private void connectGridBoundaryRoads(Map<HexVector2, HexGridConfig> gridConfigs,
                                          VillageGridLayout layout) {
        // This method can be used to add additional boundary road connections
        // For now, boundary roads are already handled in placeStreet()
        log.debug("Grid boundary roads connected");
    }
}
