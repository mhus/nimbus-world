package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Composes flow features (roads, rivers, walls) by calculating routes
 * and creating FeatureHexGrid configurations with FlowSegments.
 */
@Slf4j
public class FlowComposer {

    @Data
    @Builder
    public static class FlowCompositionResult {
        private int totalFlows;
        private int composedFlows;
        private int failedFlows;
        private int totalSegments;
        private boolean success;
        private String errorMessage;
        private List<String> errors;
    }

    /**
     * Composes all flows in the prepared composition.
     * Creates flow routes and adds FlowSegments to FeatureHexGrids.
     *
     * @param prepared The prepared composition with flows
     * @param placementResult Result from BiomeComposer with placed biomes
     * @return Composition result with statistics
     */
    public FlowCompositionResult composeFlows(HexComposition prepared,
                                               BiomePlacementResult placementResult) {
        log.info("Starting flow composition");

        List<String> errors = new ArrayList<>();
        int totalFlows = 0;
        int composedFlows = 0;
        int failedFlows = 0;
        int totalSegments = 0;

        try {
            // Build grid map from placement result
            Map<String, Biome> gridMap = buildGridMap(placementResult);

            // Get all flows to process
            List<Flow> flows = collectFlows(prepared);
            totalFlows = flows.size();

            log.info("Found {} flows to compose", totalFlows);

            for (Flow flow : flows) {
                try {
                    int segments = composeFlow(flow, gridMap, prepared, placementResult);
                    if (segments > 0) {
                        composedFlows++;
                        totalSegments += segments;
                        log.info("Composed flow '{}': {} segments", flow.getName(), segments);
                    } else {
                        failedFlows++;
                        errors.add("Flow " + flow.getName() + ": no route found");
                        log.warn("Failed to compose flow: {}", flow.getName());
                    }
                } catch (Exception e) {
                    failedFlows++;
                    errors.add("Flow " + flow.getName() + ": " + e.getMessage());
                    log.error("Error composing flow: {}", flow.getName(), e);
                }
            }

            // No need to copy back - Flows now store their data directly

            // After all flows are composed, configure road={} JSON
            HexGridRoadConfigurator roadConfigurator = new HexGridRoadConfigurator();
            HexGridRoadConfigurator.RoadConfigurationResult roadResult =
                roadConfigurator.configureRoads(prepared, placementResult);

            log.info("Road configuration: configured={}/{}, segments={}",
                roadResult.getConfiguredGrids(), roadResult.getTotalGrids(),
                roadResult.getTotalSegments());

            if (!roadResult.isSuccess()) {
                log.warn("Road configuration had errors: {}", roadResult.getErrors());
            }

            log.info("Flow composition complete: composed={}/{}, segments={}, failed={}",
                composedFlows, totalFlows, totalSegments, failedFlows);

            return FlowCompositionResult.builder()
                .totalFlows(totalFlows)
                .composedFlows(composedFlows)
                .failedFlows(failedFlows)
                .totalSegments(totalSegments)
                .success(failedFlows == 0)
                .errors(errors)
                .build();

        } catch (Exception e) {
            log.error("Flow composition failed", e);
            return FlowCompositionResult.builder()
                .totalFlows(totalFlows)
                .composedFlows(composedFlows)
                .failedFlows(failedFlows)
                .totalSegments(totalSegments)
                .success(false)
                .errorMessage(e.getMessage())
                .errors(errors)
                .build();
        }
    }

    /**
     * Composes a single flow feature
     */
    private int composeFlow(Flow flow, Map<String, Biome> gridMap,
                            HexComposition prepared,
                            BiomePlacementResult placementResult) {
        log.debug("Composing flow: {} (type: {})", flow.getName(), flow.getType());

        // Resolve start/end points
        if (!resolveFlowEndpoints(flow, prepared, placementResult)) {
            log.warn("Could not resolve endpoints for flow: {}", flow.getName());
            return 0;
        }

        // Plan route
        List<HexVector2> route = planFlowRoute(flow, gridMap);
        if (route == null || route.isEmpty()) {
            log.warn("Could not plan route for flow: {}", flow.getName());
            return 0;
        }

        flow.setRoute(route);

        // Configure HexGrids for this flow (creates FeatureHexGrids with parameters)
        flow.configureHexGrids(route);

        // Create flow segments and add them to the configured HexGrids
        int segments = createFlowSegments(flow, route, gridMap, prepared);

        // Phase 1: Convert FlowSegments to ConfigParts and add to Area grids
        if (segments > 0) {
            if (flow instanceof Road) {
                convertFlowSegmentsToRoadConfigParts(flow, prepared, placementResult);
            } else if (flow instanceof River) {
                convertFlowSegmentsToRiverConfigParts(flow, prepared, placementResult);
            } else if (flow instanceof Wall) {
                convertFlowSegmentsToWallConfigParts(flow, prepared, placementResult);
            }
        }

        // Update feature status to COMPOSED
        if (segments > 0) {
            flow.setStatus(FeatureStatus.COMPOSED);
        }

        return segments;
    }

    /**
     * Resolves start/end points from feature IDs to coordinates
     */
    private boolean resolveFlowEndpoints(Flow flow, HexComposition prepared,
                                         BiomePlacementResult placementResult) {
        // Find start point
        if (flow.getStartPointId() != null) {
            HexVector2 startCoord = findFeatureCoordinate(flow.getStartPointId(),
                placementResult, prepared);
            if (startCoord == null) {
                log.warn("Could not find start point: {}", flow.getStartPointId());
                return false;
            }
            flow.setStartPoint(startCoord);
        }

        // Find end point (for roads/walls)
        if (flow instanceof Road road) {
            if (road.getEndPointId() != null) {
                HexVector2 endCoord = findFeatureCoordinate(road.getEndPointId(),
                    placementResult, prepared);
                if (endCoord == null) {
                    log.warn("Could not find end point: {}", road.getEndPointId());
                    return false;
                }
                flow.setEndPoint(endCoord);
            }
        } else if (flow instanceof Wall wall) {
            if (wall.getEndPointId() != null) {
                HexVector2 endCoord = findFeatureCoordinate(wall.getEndPointId(),
                    placementResult, prepared);
                if (endCoord == null) {
                    log.warn("Could not find end point: {}", wall.getEndPointId());
                    return false;
                }
                flow.setEndPoint(endCoord);
            }
        } else if (flow instanceof River river) {
            if (river.getMergeToId() != null) {
                HexVector2 mergeCoord = findFeatureCoordinate(river.getMergeToId(),
                    placementResult, prepared);
                if (mergeCoord == null) {
                    log.warn("Could not find merge point: {}", river.getMergeToId());
                    return false;
                }
                flow.setEndPoint(mergeCoord);
            }
        }

        // TODO: Resolve waypoints if needed

        return flow.getStartPoint() != null;
    }

    /**
     * Finds a coordinate for a feature by its ID or name
     */
    private HexVector2 findFeatureCoordinate(String featureId,
                                             BiomePlacementResult placementResult,
                                             HexComposition prepared) {
        // Search in placed biomes (has actual coordinates)
        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            Biome biome = placed.getBiome();

            // Match by feature ID
            if (biome.getFeatureId() != null && biome.getFeatureId().equals(featureId)) {
                log.debug("Found feature '{}' at center {}", featureId, placed.getCenter());
                return placed.getCenter();
            }

            // Match by name
            if (biome.getName() != null && biome.getName().equals(featureId)) {
                log.debug("Found feature '{}' by name at center {}", featureId, placed.getCenter());
                return placed.getCenter();
            }
        }

        // TODO: Search in other features (villages, towns, composites)

        log.warn("Feature '{}' not found in placed biomes", featureId);
        return null;
    }

    /**
     * Plans a route between flow waypoints using simple pathfinding
     */
    private List<HexVector2> planFlowRoute(Flow flow, Map<String, Biome> gridMap) {
        List<HexVector2> route = new ArrayList<>();

        HexVector2 start = flow.getStartPoint();
        HexVector2 end = flow.getEndPoint();

        if (start == null) {
            log.warn("Flow has no start point: {}", flow.getName());
            return route;
        }

        // Simple case: just start point (area-internal flow)
        if (end == null) {
            route.add(start);
            return route;
        }

        // Use A* pathfinding to find route
        List<HexVector2> path = findPath(start, end, gridMap);
        if (path != null && !path.isEmpty()) {
            route.addAll(path);
        }

        return route;
    }

    /**
     * Simple A* pathfinding between two hex coordinates
     */
    private List<HexVector2> findPath(HexVector2 start, HexVector2 goal,
                                      Map<String, Biome> gridMap) {
        // Simple straight-line path for now
        List<HexVector2> path = new ArrayList<>();
        path.add(start);

        // Get direction vector
        int dq = goal.getQ() - start.getQ();
        int dr = goal.getR() - start.getR();

        HexVector2 current = start;

        // Move step by step towards goal
        while (!current.equals(goal)) {
            // Determine next step
            HexVector2 next = getNextStepTowards(current, goal);
            if (next == null || next.equals(current)) {
                // Stuck, return what we have
                break;
            }
            path.add(next);
            current = next;

            // Safety: max 100 steps
            if (path.size() > 100) {
                log.warn("Path too long, stopping at 100 steps");
                break;
            }
        }

        return path;
    }

    /**
     * Gets next hex step towards goal
     */
    private HexVector2 getNextStepTowards(HexVector2 current, HexVector2 goal) {
        int dq = goal.getQ() - current.getQ();
        int dr = goal.getR() - current.getR();

        if (dq == 0 && dr == 0) {
            return current; // Already at goal
        }

        // Prefer moving in the larger delta direction
        if (Math.abs(dq) > Math.abs(dr)) {
            // Move in Q direction
            return HexVector2.builder()
                .q(current.getQ() + Integer.signum(dq))
                .r(current.getR())
                .build();
        } else if (Math.abs(dr) > Math.abs(dq)) {
            // Move in R direction
            return HexVector2.builder()
                .q(current.getQ())
                .r(current.getR() + Integer.signum(dr))
                .build();
        } else {
            // Move diagonally
            return HexVector2.builder()
                .q(current.getQ() + Integer.signum(dq))
                .r(current.getR() + Integer.signum(dr))
                .build();
        }
    }

    /**
     * Creates flow segments for a route and adds them to FeatureHexGrids
     */
    private int createFlowSegments(Flow flow, List<HexVector2> route,
                                   Map<String, Biome> gridMap,
                                   HexComposition prepared) {
        int segmentCount = 0;

        for (int i = 0; i < route.size(); i++) {
            HexVector2 coord = route.get(i);
            SIDE fromSide = null;
            SIDE toSide = null;

            // Determine entry side (from where the flow enters THIS grid)
            if (i > 0) {
                HexVector2 prev = route.get(i - 1);
                // Direction from prev to coord gives us the exit side of prev
                // But we need the ENTRY side of THIS grid, which is the opposite!
                SIDE directionFromPrev = RoadAndRiverConnector.determineSide(prev, coord);
                fromSide = RoadAndRiverConnector.getOppositeSide(directionFromPrev);
            }

            // Determine exit side (to where the flow exits THIS grid)
            if (i < route.size() - 1) {
                HexVector2 next = route.get(i + 1);
                toSide = RoadAndRiverConnector.determineSide(coord, next);
            }

            // Create flow segment
            FlowSegment segment = createFlowSegment(flow, fromSide, toSide);

            // Add segment to flow's own FeatureHexGrid (already configured by flow.configureHexGrids())
            FeatureHexGrid flowHexGrid = flow.findHexGrid(coord);
            if (flowHexGrid != null) {
                flowHexGrid.addFlowSegment(segment);
            } else {
                log.warn("No FeatureHexGrid found for flow {} at coordinate {}", flow.getName(), coord);
            }

            // Also add segment to biome's FeatureHexGrid (if flow crosses a biome)
            FeatureHexGrid biomeHexGrid = findFeatureHexGridInBiome(coord, gridMap);
            if (biomeHexGrid != null) {
                biomeHexGrid.addFlowSegment(segment);
            }

            segmentCount++;
        }

        return segmentCount;
    }

    /**
     * Creates a FlowSegment from PreparedFlow
     */
    private FlowSegment createFlowSegment(Flow flow, SIDE fromSide, SIDE toSide) {
        FlowSegment.FlowSegmentBuilder builder = FlowSegment.builder()
            .flowType(flow.getType())
            .fromSide(fromSide)
            .toSide(toSide)
            .width(flow.getCalculatedWidthBlocks())
            .flowFeatureId(flow.getFeatureId());

        // Type-specific attributes
        if (flow instanceof Road road) {
            builder.type(road.getRoadType());
            builder.level(road.getLevel());
        } else if (flow instanceof River river) {
            builder.depth(river.getDepth());
            builder.level(river.getLevel());
        } else if (flow instanceof Wall wall) {
            builder.height(wall.getHeight());
            builder.level(wall.getLevel());
            builder.material(wall.getMaterial());
        }

        return builder.build();
    }

    /**
     * Finds a FeatureHexGrid in a biome at the given coordinate.
     * Returns null if no biome exists at that coordinate or if the biome has no FeatureHexGrid there.
     */
    private FeatureHexGrid findFeatureHexGridInBiome(HexVector2 coord, Map<String, Biome> gridMap) {
        // Find the biome at this coordinate
        Biome biome = gridMap.get(coordKey(coord));

        if (biome == null) {
            // No biome at this coordinate (flow crosses empty space or filler)
            return null;
        }

        // Find existing FeatureHexGrid in biome
        FeatureHexGrid existing = biome.getHexGrids().stream()
            .filter(hg -> hg.getCoordinate() != null
                && hg.getCoordinate().getQ() == coord.getQ()
                && hg.getCoordinate().getR() == coord.getR())
            .findFirst()
            .orElse(null);

        if (existing == null) {
            // Should not happen - biomes should already have FeatureHexGrids from BiomeComposer
            log.warn("Biome {} has no FeatureHexGrid at {}", biome.getName(), coord);
        }

        return existing;
    }

    /**
     * Builds a grid map from placement result
     */
    private Map<String, Biome> buildGridMap(BiomePlacementResult placementResult) {
        Map<String, Biome> gridMap = new HashMap<>();

        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            Biome biome = placed.getBiome();
            for (HexVector2 coord : placed.getCoordinates()) {
                gridMap.put(coordKey(coord), biome);
            }
        }

        return gridMap;
    }

    /**
     * Collects all flows from prepared composition
     */
    private List<Flow> collectFlows(HexComposition prepared) {
        List<Flow> flows = new ArrayList<>();

        // Direct flows - cast to Flow interface
        flows.addAll((List<? extends Flow>) prepared.getRoads());
        flows.addAll((List<? extends Flow>) prepared.getRivers());
        flows.addAll((List<? extends Flow>) prepared.getWalls());

        // TODO: Flows from composites

        return flows;
    }


    /**
     * Phase 1: Converts FlowSegments to RoadConfigParts and adds them to Area grids.
     * This is called after flow segments have been created and added to FeatureHexGrids.
     *
     * Important: Flow FeatureHexGrids contain metadata (coordinates + FlowSegments).
     * We need to add RoadConfigParts to the Area FeatureHexGrids at the same coordinates.
     *
     * CRITICAL: Must include Filler-Biomes! They were added to placementResult by Fillers.
     *
     * @param flow The flow whose segments should be converted to RoadConfigParts
     * @param composition The composition with all features to find Area grids
     * @param placementResult The placement result with all PlacedBiomes (incl. Filler)
     */
    private void convertFlowSegmentsToRoadConfigParts(Flow flow, HexComposition composition,
                                                       BiomePlacementResult placementResult) {
        if (!(flow instanceof Road)) {
            return; // Only roads use RoadConfigParts
        }

        // Build a map of Area grids by coordinate for fast lookup
        Map<String, FeatureHexGrid> areaGridMap = new HashMap<>();

        // Collect Area grids from all features in composition
        if (composition.getFeatures() != null) {
            for (Feature feature : composition.getFeatures()) {
                if (feature instanceof Area) {
                    collectAreaGrids((Area) feature, areaGridMap);
                }
            }
        }

        // Collect Area grids from composites
        if (composition.getComposites() != null) {
            for (Composite composite : composition.getComposites()) {
                for (Feature nestedFeature : composite.getFeatures()) {
                    if (nestedFeature instanceof Area) {
                        collectAreaGrids((Area) nestedFeature, areaGridMap);
                    }
                }
            }
        }

        // IMPORTANT: Also collect from PlacedBiomes (includes Filler-Biomes!)
        collectAreaGridsFromPlacedBiomes(placementResult, areaGridMap);

        log.debug("Collected {} Area grids (incl. Filler) for road config conversion", areaGridMap.size());

        // Now convert FlowSegments to RoadConfigParts and add to Area grids
        for (FeatureHexGrid flowGrid : flow.getHexGrids()) {
            String coordKey = flowGrid.getPositionKey();
            if (coordKey == null) {
                continue;
            }

            // Find the Area grid at this coordinate
            FeatureHexGrid areaGrid = areaGridMap.get(coordKey);
            if (areaGrid == null) {
                log.debug("No Area grid found at {}, flow crosses empty space", coordKey);
                continue;
            }

            // Get flow segments for this grid
            List<FlowSegment> roadSegments = flowGrid.getFlowSegmentsByType(FlowType.ROAD);

            if (roadSegments.isEmpty()) {
                continue;
            }

            // Convert each FlowSegment to RoadConfigPart
            for (FlowSegment segment : roadSegments) {
                // Create ROUTE parts for fromSide
                if (segment.getFromSide() != null) {
                    RoadConfigPart part = RoadConfigPart.createRouteSidePart(
                        segment.getFromSide(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getType()
                    );
                    areaGrid.addRoadConfigPart(part);
                }

                // Create ROUTE parts for toSide (if different from fromSide)
                if (segment.getToSide() != null && !segment.getToSide().equals(segment.getFromSide())) {
                    RoadConfigPart part = RoadConfigPart.createRouteSidePart(
                        segment.getToSide(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getType()
                    );
                    areaGrid.addRoadConfigPart(part);
                }
            }

            log.debug("Converted {} road segments to {} config parts for Area grid {}",
                roadSegments.size(), areaGrid.getRoadConfigParts().size(), coordKey);
        }
    }

    /**
     * Phase 1: Converts FlowSegments to RiverConfigParts and adds them to Area grids.
     *
     * @param flow The river flow whose segments should be converted
     * @param composition The composition with all features to find Area grids
     * @param placementResult The placement result with all PlacedBiomes (incl. Filler)
     */
    private void convertFlowSegmentsToRiverConfigParts(Flow flow, HexComposition composition,
                                                        BiomePlacementResult placementResult) {
        if (!(flow instanceof River)) {
            return;
        }

        River river = (River) flow;

        // Build a map of Area grids by coordinate for fast lookup
        Map<String, FeatureHexGrid> areaGridMap = new HashMap<>();
        collectAllAreaGrids(composition, areaGridMap);

        // Also collect from PlacedBiomes (includes Filler-Biomes!)
        collectAreaGridsFromPlacedBiomes(placementResult, areaGridMap);

        // Convert FlowSegments to RiverConfigParts and add to Area grids
        for (FeatureHexGrid flowGrid : flow.getHexGrids()) {
            String coordKey = flowGrid.getPositionKey();
            if (coordKey == null) {
                continue;
            }

            // Find the Area grid at this coordinate
            FeatureHexGrid areaGrid = areaGridMap.get(coordKey);
            if (areaGrid == null) {
                log.debug("No Area grid found at {}, river crosses empty space", coordKey);
                continue;
            }

            // Get river segments for this grid
            List<FlowSegment> riverSegments = flowGrid.getFlowSegmentsByType(FlowType.RIVER);
            if (riverSegments.isEmpty()) {
                continue;
            }

            // Convert each FlowSegment to RiverConfigPart
            for (FlowSegment segment : riverSegments) {
                String groupId = segment.getFlowFeatureId() != null ? segment.getFlowFeatureId() : river.getFeatureId();

                // Create FROM parts
                if (segment.getFromSide() != null) {
                    RiverConfigPart part = RiverConfigPart.createFromPart(
                        segment.getFromSide(),
                        segment.getWidth(),
                        segment.getDepth(),
                        segment.getLevel(),
                        groupId
                    );
                    areaGrid.addRiverConfigPart(part);
                }

                // Create TO parts
                if (segment.getToSide() != null) {
                    RiverConfigPart part = RiverConfigPart.createToPart(
                        segment.getToSide(),
                        segment.getWidth(),
                        segment.getDepth(),
                        segment.getLevel(),
                        groupId
                    );
                    areaGrid.addRiverConfigPart(part);
                }
            }

            log.debug("Converted {} river segments to {} config parts for Area grid {}",
                riverSegments.size(), areaGrid.getRiverConfigParts().size(), coordKey);
        }
    }

    /**
     * Phase 1: Converts FlowSegments to WallConfigParts and adds them to Area grids.
     *
     * @param flow The wall flow whose segments should be converted
     * @param composition The composition with all features to find Area grids
     * @param placementResult The placement result with all PlacedBiomes (incl. Filler)
     */
    private void convertFlowSegmentsToWallConfigParts(Flow flow, HexComposition composition,
                                                       BiomePlacementResult placementResult) {
        if (!(flow instanceof Wall)) {
            return;
        }

        Wall wall = (Wall) flow;

        // Build a map of Area grids by coordinate for fast lookup
        Map<String, FeatureHexGrid> areaGridMap = new HashMap<>();
        collectAllAreaGrids(composition, areaGridMap);

        // Also collect from PlacedBiomes (includes Filler-Biomes!)
        collectAreaGridsFromPlacedBiomes(placementResult, areaGridMap);

        // Convert FlowSegments to WallConfigParts and add to Area grids
        for (FeatureHexGrid flowGrid : flow.getHexGrids()) {
            String coordKey = flowGrid.getPositionKey();
            if (coordKey == null) {
                continue;
            }

            // Find the Area grid at this coordinate
            FeatureHexGrid areaGrid = areaGridMap.get(coordKey);
            if (areaGrid == null) {
                log.debug("No Area grid found at {}, wall crosses empty space", coordKey);
                continue;
            }

            // Get wall segments for this grid
            List<FlowSegment> wallSegments = flowGrid.getFlowSegmentsByType(FlowType.WALL);
            if (wallSegments.isEmpty()) {
                continue;
            }

            // Convert each FlowSegment to WallConfigPart
            for (FlowSegment segment : wallSegments) {
                // Create SIDE parts for fromSide
                if (segment.getFromSide() != null) {
                    WallConfigPart part = WallConfigPart.createSidePart(
                        segment.getFromSide(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    areaGrid.addWallConfigPart(part);
                }

                // Create SIDE parts for toSide (if different)
                if (segment.getToSide() != null && !segment.getToSide().equals(segment.getFromSide())) {
                    WallConfigPart part = WallConfigPart.createSidePart(
                        segment.getToSide(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    areaGrid.addWallConfigPart(part);
                }
            }

            log.debug("Converted {} wall segments to {} config parts for Area grid {}",
                wallSegments.size(), areaGrid.getWallConfigParts().size(), coordKey);
        }
    }

    /**
     * Collects Area grids from composition into a map by coordinate key
     */
    private void collectAllAreaGrids(HexComposition composition, Map<String, FeatureHexGrid> areaGridMap) {
        // Collect Area grids from all features
        if (composition.getFeatures() != null) {
            for (Feature feature : composition.getFeatures()) {
                if (feature instanceof Area) {
                    collectAreaGrids((Area) feature, areaGridMap);
                }
            }
        }

        // Collect Area grids from composites
        if (composition.getComposites() != null) {
            for (Composite composite : composition.getComposites()) {
                for (Feature nestedFeature : composite.getFeatures()) {
                    if (nestedFeature instanceof Area) {
                        collectAreaGrids((Area) nestedFeature, areaGridMap);
                    }
                }
            }
        }
    }

    /**
     * Collects Area grids into a map by coordinate key
     */
    private void collectAreaGrids(Area area, Map<String, FeatureHexGrid> areaGridMap) {
        if (area.getHexGrids() == null) {
            return;
        }

        for (FeatureHexGrid hexGrid : area.getHexGrids()) {
            String coordKey = hexGrid.getPositionKey();
            if (coordKey != null) {
                areaGridMap.put(coordKey, hexGrid);
            }
        }
    }

    /**
     * Collects Area grids from all PlacedBiomes (including Filler-Biomes!)
     * This is CRITICAL for flows that cross Filler grids (CoastFiller, OceanFiller, etc.)
     *
     * @param placementResult The placement result with all PlacedBiomes
     * @param areaGridMap Map to add grids to
     */
    private void collectAreaGridsFromPlacedBiomes(BiomePlacementResult placementResult,
                                                   Map<String, FeatureHexGrid> areaGridMap) {
        if (placementResult == null || placementResult.getPlacedBiomes() == null) {
            log.warn("placementResult or PlacedBiomes is null!");
            return;
        }

        int collectedCount = 0;
        int biomesWithGrids = 0;
        int biomesWithoutGrids = 0;

        for (PlacedBiome placedBiome : placementResult.getPlacedBiomes()) {
            Biome biome = placedBiome.getBiome();
            if (biome != null && biome.getHexGrids() != null && !biome.getHexGrids().isEmpty()) {
                biomesWithGrids++;
                for (FeatureHexGrid hexGrid : biome.getHexGrids()) {
                    String coordKey = hexGrid.getPositionKey();
                    if (coordKey != null) {
                        // Add to map (may overwrite, but that's OK - same coordinate)
                        areaGridMap.put(coordKey, hexGrid);
                        collectedCount++;
                    }
                }
            } else {
                biomesWithoutGrids++;
                if (biome != null) {
                    log.warn("PlacedBiome has no FeatureHexGrids: {} (type: {})",
                        biome.getName(), biome.getType());
                }
            }
        }

        log.info("Collected {} FeatureHexGrids from {} PlacedBiomes ({} with grids, {} without)",
            collectedCount, placementResult.getPlacedBiomes().size(), biomesWithGrids, biomesWithoutGrids);
    }

    /**
     * Creates coordinate key
     */
    private String coordKey(HexVector2 coord) {
        return coord.getQ() + "," + coord.getR();
    }
}
