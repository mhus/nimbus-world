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

        // Create flow segments
        int segments = createFlowSegments(flow, route, gridMap, prepared);

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

            // Determine entry side
            if (i > 0) {
                HexVector2 prev = route.get(i - 1);
                fromSide = RoadAndRiverConnector.determineSide(prev, coord);
            }

            // Determine exit side
            if (i < route.size() - 1) {
                HexVector2 next = route.get(i + 1);
                toSide = RoadAndRiverConnector.determineSide(coord, next);
            }

            // Create flow segment
            FlowSegment segment = createFlowSegment(flow, fromSide, toSide);

            // Add segment to biome's FeatureHexGrid (if exists)
            FeatureHexGrid biomeHexGrid = findOrCreateFeatureHexGrid(coord, gridMap, prepared);
            if (biomeHexGrid != null) {
                biomeHexGrid.addFlowSegment(segment);
            }

            // Also add as standalone FeatureHexGrid to the flow itself
            FeatureHexGrid flowHexGrid = FeatureHexGrid.builder()
                .coordinate(coord)
                .name(flow.getName() + " [" + coord.getQ() + "," + coord.getR() + "]")
                .description("Flow segment for " + flow.getName())
                .build();
            flowHexGrid.addFlowSegment(segment);
            flow.addHexGrid(flowHexGrid);

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
            builder.material(wall.getMaterial());
        }

        return builder.build();
    }

    /**
     * Finds or creates a FeatureHexGrid for a coordinate
     */
    private FeatureHexGrid findOrCreateFeatureHexGrid(HexVector2 coord,
                                                      Map<String, Biome> gridMap,
                                                      HexComposition prepared) {
        // Find the biome at this coordinate
        Biome biome = gridMap.get(coordKey(coord));

        if (biome == null) {
            log.debug("No biome at coordinate {}, creating standalone grid", coord);
            // Create a standalone FeatureHexGrid (for flows crossing empty space)
            // This would need to be stored somewhere - for now, skip
            return null;
        }

        // Find existing FeatureHexGrid in biome
        FeatureHexGrid existing = biome.getHexGrids().stream()
            .filter(hg -> hg.getCoordinate() != null
                && hg.getCoordinate().getQ() == coord.getQ()
                && hg.getCoordinate().getR() == coord.getR())
            .findFirst()
            .orElse(null);

        if (existing != null) {
            return existing;
        }

        // Should not happen - biomes should already have FeatureHexGrids from BiomeComposer
        log.warn("Biome {} has no FeatureHexGrid at {}", biome.getName(), coord);
        return null;
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
     * Creates coordinate key
     */
    private String coordKey(HexVector2 coord) {
        return coord.getQ() + "," + coord.getR();
    }
}
