package de.mhus.nimbus.world.generator.composer.flow;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.feature.FeatureStatus;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.build.HexGridRoadConfigurator;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.area.Area;
import de.mhus.nimbus.world.generator.composer.area.Composite;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.biome.BiomeType;
import de.mhus.nimbus.world.generator.composer.point.Point;
import de.mhus.nimbus.world.generator.composer.util.HexComposeUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid.EDGE;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Composes flow features (roads, rivers, walls) by calculating routes
 * and creating FeatureHexGrid configurations with FlowSegments.
 */
@Slf4j
public class FlowComposer {

    /** Sea level — absolute minimum for river water surface levels. */
    public static final int SEA_LEVEL = 50;

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
        log.debug("Starting flow composition");

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

            log.debug("Found {} flows to compose", totalFlows);

            for (Flow flow : flows) {
                try {
                    int segments = composeFlow(flow, gridMap, prepared, placementResult);
                    if (segments > 0) {
                        composedFlows++;
                        totalSegments += segments;
                        log.debug("Composed flow '{}': {} segments", flow.getName(), segments);
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

            // Note: Road configuration (g_road, g_river, g_wall parameters) is now done
            // in HexCompositeBuilder after populateCentralRegistry(), so that
            // HexGridRoadConfigurator works with the complete central registry

            log.debug("Flow composition complete: composed={}/{}, segments={}, failed={}",
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
     * Phase 1: Converts FlowSegments (from central registry) to RoadConfigParts/RiverConfigParts
     * and adds them to the central registry FeatureHexGrids.
     *
     * MUST be called AFTER populateCentralRegistry() so that Flow.hexGrids with flowSegments
     * have been transferred to the central registry.
     *
     * @param composition The composition with all features
     * @param placementResult The placement result with all PlacedBiomes
     * @return Number of flows processed
     */
    public int convertAllFlowSegmentsToConfigParts(HexComposition composition,
                                                    BiomePlacementResult placementResult) {
        int processedFlows = 0;

        if (composition.getFeatures() == null) {
            return 0;
        }

        // Iterate through all Flow features and convert their segments to ConfigParts
        for (Feature feature : composition.getFeatures()) {
            if (!(feature instanceof Flow)) {
                continue;
            }

            Flow flow = (Flow) feature;

            if (flow instanceof Road) {
                convertFlowSegmentsToRoadConfigParts(flow, composition, placementResult);
                processedFlows++;
            } else if (flow instanceof River) {
                convertFlowSegmentsToRiverConfigParts(flow, composition, placementResult);
                processedFlows++;
            } else if (flow instanceof Wall) {
                convertFlowSegmentsToWallConfigParts(flow, composition, placementResult);
                processedFlows++;
            }
        }

        log.info("Converted FlowSegments to ConfigParts for {} flows", processedFlows);
        return processedFlows;
    }

    /**
     * Composes a single flow feature
     */
    private int composeFlow(Flow flow, Map<String, Biome> gridMap,
                            HexComposition prepared,
                            BiomePlacementResult placementResult) {
        log.debug("Composing flow: {} (type: {})", flow.getName(), flow.getType());

        // SideWalls are handled differently - they don't route from A to B
        // but instead decorate the edges of a target biome
        if (flow instanceof SideWall) {
            return composeSideWall((SideWall) flow, prepared, placementResult);
        }

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

        // Create flow segments and add them directly to central registry
        // No need for flow.configureHexGrids() - segments are added to composition.getOrCreateFeatureHexGrid()
        int segments = createFlowSegments(flow, route, gridMap, prepared);

        // Note: Phase 1 (Convert FlowSegments to ConfigParts) was moved to HexCompositeBuilder
        // after populateCentralRegistry(), so that flowSegments are in central registry first

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
            // First try to find a Point
            Point startPoint = findPoint(flow.getStartPointId(), prepared);
            if (startPoint != null) {
                flow.setStartPoint(startPoint.getPlacedCoordinate());
                flow.setStartPointFeature(startPoint);
                log.debug("Flow '{}' starts at Point '{}' with lx={}, lz={}",
                    flow.getName(), startPoint.getName(),
                    startPoint.getPlacedLx(), startPoint.getPlacedLz());
            } else {
                // Fall back to Biome
                HexVector2 startCoord = findFeatureCoordinate(flow.getStartPointId(),
                    placementResult, prepared);
                if (startCoord == null) {
                    log.warn("Could not find start point: {}", flow.getStartPointId());
                    return false;
                }
                flow.setStartPoint(startCoord);
                flow.setStartPointFeature(null);
            }
        }

        // Find end point (for roads/walls)
        if (flow instanceof Road road) {
            if (road.getEndPointId() != null) {
                // First try to find a Point
                Point endPoint = findPoint(road.getEndPointId(), prepared);
                if (endPoint != null) {
                    flow.setEndPoint(endPoint.getPlacedCoordinate());
                    flow.setEndPointFeature(endPoint);
                    log.debug("Flow '{}' ends at Point '{}' with lx={}, lz={}",
                        flow.getName(), endPoint.getName(),
                        endPoint.getPlacedLx(), endPoint.getPlacedLz());
                } else {
                    // Fall back to Biome
                    HexVector2 endCoord = findFeatureCoordinate(road.getEndPointId(),
                        placementResult, prepared);
                    if (endCoord == null) {
                        log.warn("Could not find end point: {}", road.getEndPointId());
                        return false;
                    }
                    flow.setEndPoint(endCoord);
                    flow.setEndPointFeature(null);
                }
            }
        } else if (flow instanceof Wall wall) {
            if (wall.getEndPointId() != null) {
                // First try to find a Point
                Point endPoint = findPoint(wall.getEndPointId(), prepared);
                if (endPoint != null) {
                    flow.setEndPoint(endPoint.getPlacedCoordinate());
                    flow.setEndPointFeature(endPoint);
                    log.debug("Flow '{}' ends at Point '{}' with lx={}, lz={}",
                        flow.getName(), endPoint.getName(),
                        endPoint.getPlacedLx(), endPoint.getPlacedLz());
                } else {
                    // Fall back to Biome
                    HexVector2 endCoord = findFeatureCoordinate(wall.getEndPointId(),
                        placementResult, prepared);
                    if (endCoord == null) {
                        log.warn("Could not find end point: {}", wall.getEndPointId());
                        return false;
                    }
                    flow.setEndPoint(endCoord);
                    flow.setEndPointFeature(null);
                }
            }
        } else if (flow instanceof River river) {
            if (river.getEndPointId() != null) {
                // Rivers merge at a Point (end point)
                Point endPoint = findPoint(river.getEndPointId(), prepared);
                if (endPoint != null) {
                    flow.setEndPoint(endPoint.getPlacedCoordinate());
                    flow.setEndPointFeature(endPoint);
                    log.debug("Flow '{}' merges at Point '{}' with lx={}, lz={}",
                        flow.getName(), endPoint.getName(),
                        endPoint.getPlacedLx(), endPoint.getPlacedLz());
                } else {
                    // Fall back to Biome
                    HexVector2 endCoord = findFeatureCoordinate(river.getEndPointId(),
                        placementResult, prepared);
                    if (endCoord == null) {
                        log.warn("Could not find end point: {}", river.getEndPointId());
                        return false;
                    }
                    flow.setEndPoint(endCoord);
                    flow.setEndPointFeature(null);
                }
            }
        }

        // Check for closed loop: startPointId == endPointId
        if (flow instanceof Road road) {
            if (road.getEndPointId() != null && road.getEndPointId().equals(flow.getStartPointId())) {
                flow.setClosedLoop(true);
                log.debug("Flow '{}' is a closed loop (start == end)", flow.getName());
            }
        } else if (flow instanceof Wall wall) {
            if (wall.getEndPointId() != null && wall.getEndPointId().equals(flow.getStartPointId())) {
                flow.setClosedLoop(true);
                log.debug("Flow '{}' is a closed loop (start == end)", flow.getName());
            }
        }

        // Resolve waypoints to coordinates
        if (flow.getWaypointIds() != null && !flow.getWaypointIds().isEmpty()) {
            List<HexVector2> waypointCoords = new ArrayList<>();
            for (String waypointId : flow.getWaypointIds()) {
                HexVector2 waypointCoord = findFeatureCoordinate(waypointId, placementResult, prepared);
                if (waypointCoord != null) {
                    waypointCoords.add(waypointCoord);
                    log.debug("Flow '{}' waypoint '{}' resolved to ({},{})",
                        flow.getName(), waypointId, waypointCoord.getQ(), waypointCoord.getR());
                } else {
                    log.warn("Flow '{}': waypoint '{}' not found, skipping", flow.getName(), waypointId);
                }
            }
            if (!waypointCoords.isEmpty()) {
                flow.setWaypoints(waypointCoords);
                log.debug("Flow '{}': resolved {} waypoints", flow.getName(), waypointCoords.size());
            }
        }

        return flow.getStartPoint() != null;
    }

    /**
     * Finds a Point feature by its ID or name.
     * Returns null if not found or if feature is not a Point.
     */
    private Point findPoint(String featureId, HexComposition prepared) {
        if (prepared.getFeatures() == null) {
            return null;
        }

        for (Feature feature : prepared.getFeatures()) {
            if (!(feature instanceof Point point)) {
                continue;
            }

            // Match by feature ID
            if (point.getFeatureId() != null && point.getFeatureId().equals(featureId)) {
                if (point.isPlaced()) {
                    log.debug("Found Point '{}' at {}", featureId, point.getPlacedPositionString());
                    return point;
                } else {
                    log.warn("Point '{}' found but not placed yet", featureId);
                    return null;
                }
            }

            // Match by name
            if (point.getName() != null && point.getName().equals(featureId)) {
                if (point.isPlaced()) {
                    log.debug("Found Point '{}' by name at {}", featureId, point.getPlacedPositionString());
                    return point;
                } else {
                    log.warn("Point '{}' found by name but not placed yet", featureId);
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Finds a coordinate for a feature by its ID or name
     */
    private HexVector2 findFeatureCoordinate(String featureId,
                                             BiomePlacementResult placementResult,
                                             HexComposition prepared) {
        // First, try to find a Point (Points have priority)
        Point point = findPoint(featureId, prepared);
        if (point != null) {
            return point.getPlacedCoordinate();
        }

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

        log.warn("Feature '{}' not found in placed biomes or points", featureId);
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

        // Check for closed loop
        if (flow.isClosedLoop()) {
            log.debug("Planning closed loop route for flow '{}' around point {},{} with radius {}",
                flow.getName(), start.getQ(), start.getR(), flow.getEffectiveSizeFrom());
            return planClosedLoopRoute(flow, start);
        }

        // Simple case: just start point (area-internal flow)
        if (end == null) {
            route.add(start);
            return route;
        }

        // Build list of waypoints to route through: start → waypoint1 → ... → end
        List<HexVector2> targets = new ArrayList<>();
        if (flow.getWaypoints() != null) {
            targets.addAll(flow.getWaypoints());
        }
        targets.add(end);

        // Single Random for the entire route — so waypoint segments get different deviation patterns
        Random routeRandom = new Random(flow.getName().hashCode());

        // Route segment by segment through each waypoint
        HexVector2 current = start;
        for (HexVector2 target : targets) {
            List<HexVector2> segment = findPath(flow, current, target, gridMap, routeRandom);
            if (segment == null || segment.isEmpty()) {
                log.warn("Flow '{}': no path from ({},{}) to ({},{})",
                    flow.getName(), current.getQ(), current.getR(), target.getQ(), target.getR());
                break;
            }
            // Avoid duplicate coordinate at segment boundary
            if (!route.isEmpty() && !segment.isEmpty()
                    && route.get(route.size() - 1).getQ() == segment.get(0).getQ()
                    && route.get(route.size() - 1).getR() == segment.get(0).getR()) {
                segment = segment.subList(1, segment.size());
            }
            route.addAll(segment);
            current = target;
        }

        return route;
    }

    /**
     * Plans a closed loop route (ring/circle) around a center point.
     * Creates a hexagonal ring with the given radius.
     * Minimum 3 segments, typically 6*radius segments for a full hex ring.
     *
     * @param flow The flow to plan the route for
     * @param center The center point to create the ring around
     * @return List of coordinates forming a closed ring
     */
    private List<HexVector2> planClosedLoopRoute(Flow flow, HexVector2 center) {
        List<HexVector2> route = new ArrayList<>();

        // Get radius from flow configuration
        int radius = flow.getEffectiveSizeFrom();
        if (radius < 1) {
            log.warn("Closed loop radius < 1, using minimum radius of 1");
            radius = 1;
        }

        // Get shape hint (default: RING for hexagonal ring)
        String shapeHint = flow.getShapeHint();
        if (shapeHint == null || shapeHint.isEmpty()) {
            shapeHint = "RING";
        }

        log.debug("Creating closed loop with shape '{}' and radius {}", shapeHint, radius);

        // Create hexagonal ring around center
        // A hex ring of radius r has 6*r hexagons
        // We walk around the ring using the 6 hex directions
        route.addAll(createHexRing(center, radius));

        // Ensure minimum 3 segments
        if (route.size() < 3) {
            log.warn("Closed loop has only {} segments, minimum is 3. Increasing radius.", route.size());
            route.clear();
            route.addAll(createHexRing(center, radius + 1));
        }

        log.debug("Created closed loop with {} segments", route.size());
        return route;
    }

    /**
     * Creates a hexagonal ring around a center point at the given radius.
     * Uses odd-r offset hex coordinate system via HexComposeUtil.getNeighborPosition().
     *
     * @param center Center coordinate
     * @param radius Ring radius (distance from center)
     * @return List of coordinates forming the ring
     */
    private List<HexVector2> createHexRing(HexVector2 center, int radius) {
        List<HexVector2> ring = new ArrayList<>();

        // Walk directions for a ring: after starting at WEST, walk these edges
        EDGE[] walkDirections = {
            EDGE.NORTH_EAST,
            EDGE.EAST,
            EDGE.SOUTH_EAST,
            EDGE.SOUTH_WEST,
            EDGE.WEST,
            EDGE.NORTH_WEST
        };

        // Start at position 'radius' steps WEST from center
        HexVector2 current = center;
        for (int i = 0; i < radius; i++) {
            current = HexComposeUtil.getNeighborPosition(current, EDGE.WEST);
        }

        // Walk around the ring
        for (EDGE direction : walkDirections) {
            for (int j = 0; j < radius; j++) {
                ring.add(current);
                current = HexComposeUtil.getNeighborPosition(current, direction);
            }
        }

        return ring;
    }

    /**
     * Pathfinding between two hex coordinates with deviation support.
     * Uses polymorphic flow.selectNextStep() for type-specific routing.
     * Rivers prefer downhill with fallback; roads use greedy closest-to-goal.
     */
    private List<HexVector2> findPath(Flow flow, HexVector2 start, HexVector2 goal,
                                      Map<String, Biome> gridMap, Random random) {
        List<HexVector2> path = new ArrayList<>();
        path.add(start);

        HexVector2 current = start;
        // Track all visited positions to prevent cycles (not just immediate backtrack)
        Set<String> visited = new HashSet<>();
        visited.add(TypeUtil.toStringHexCoord(current));

        // Get deviation tendencies
        DeviationTendency tendLeft = flow.getTendLeft();
        DeviationTendency tendRight = flow.getTendRight();
        boolean hasDeviation = (tendLeft != null && tendLeft != DeviationTendency.NONE) ||
                               (tendRight != null && tendRight != DeviationTendency.NONE);

        ToIntFunction<HexVector2> terrainLevelAt = coord -> getTerrainLevel(coord, gridMap);
        Predicate<HexVector2> isLowPriorityBiome = coord -> isLowPriorityBiome(coord, gridMap);
        int maxSteps = flow.getMaxRoutingSteps();

        // Move step by step towards goal
        while (!current.equals(goal)) {
            List<HexVector2> neighbors = getHexNeighbors(current);

            // If goal is a direct neighbor, go straight to it (skip routing/deviation)
            boolean goalIsNeighbor = neighbors.stream()
                    .anyMatch(n -> n.getQ() == goal.getQ() && n.getR() == goal.getR());
            if (goalIsNeighbor) {
                path.add(goal);
                break;
            }

            // Remove already visited positions to prevent cycles
            neighbors.removeIf(n -> visited.contains(TypeUtil.toStringHexCoord(n)));

            if (neighbors.isEmpty()) {
                log.warn("Flow '{}' stuck at ({},{}) — all neighbors visited",
                    flow.getName(), current.getQ(), current.getR());
                break;
            }

            HexVector2 next;
            if (hasDeviation) {
                next = getNextStepWithDeviation(current, goal, tendLeft, tendRight,
                    flow, neighbors, terrainLevelAt, isLowPriorityBiome, random);
            } else {
                next = flow.selectNextStep(current, goal, neighbors, terrainLevelAt, isLowPriorityBiome);
            }

            if (next == null || next.equals(current)) {
                log.warn("Flow '{}' stuck at ({},{})", flow.getName(), current.getQ(), current.getR());
                break;
            }

            path.add(next);
            visited.add(TypeUtil.toStringHexCoord(next));
            current = next;

            if (path.size() > maxSteps) {
                log.warn("Flow '{}' reached max steps ({})", flow.getName(), maxSteps);
                break;
            }
        }

        return path;
    }

    /**
     * Gets next hex step with deviation support (for curved routes).
     * Uses polymorphic flow.selectNextStep() for type-specific neighbor selection.
     * Randomly deviates left or right based on tendLeft/tendRight probabilities.
     */
    private HexVector2 getNextStepWithDeviation(HexVector2 current, HexVector2 goal,
                                                DeviationTendency tendLeft,
                                                DeviationTendency tendRight,
                                                Flow flow,
                                                List<HexVector2> neighbors,
                                                ToIntFunction<HexVector2> terrainLevelAt,
                                                Predicate<HexVector2> isLowPriorityBiome,
                                                Random random) {
        // Calculate best direction towards goal via polymorphic dispatch
        HexVector2 bestStep = flow.selectNextStep(current, goal, neighbors, terrainLevelAt, isLowPriorityBiome);
        if (bestStep == null) return null;

        // Don't deviate when goal is a direct neighbor — go straight to it
        int currentDistance = Flow.hexDistance(current, goal);
        if (currentDistance <= 1) {
            return bestStep;
        }

        // Determine if we should deviate
        double leftProb = tendLeft != null ? tendLeft.getProbability() : 0.0;
        double rightProb = tendRight != null ? tendRight.getProbability() : 0.0;
        double totalProb = leftProb + rightProb;

        if (totalProb == 0.0) {
            return bestStep; // No deviation
        }

        // Roll for deviation
        double roll = random.nextDouble();
        if (roll > totalProb) {
            return bestStep; // No deviation this step
        }

        // Determine deviation direction (left or right)
        boolean deviateLeft = roll < leftProb;

        // Find the neighbor that represents deviation
        HexVector2 deviatedStep = findDeviatedNeighbor(current, bestStep, neighbors, deviateLeft);

        // Validate deviated step is in the allowed neighbors list (which has 'previous' filtered out)
        boolean isValidNeighbor = neighbors.stream()
                .anyMatch(n -> n.getQ() == deviatedStep.getQ() && n.getR() == deviatedStep.getR());
        if (!isValidNeighbor) {
            return bestStep;
        }

        // If deviated step would take us further from goal than we already are, use best step instead
        int deviatedDistance = Flow.hexDistance(deviatedStep, goal);

        if (deviatedDistance > currentDistance + 1) {
            // Deviation would take us too far off course
            return bestStep;
        }

        // Don't deviate into low-priority biome if bestStep is high-priority
        if (isLowPriorityBiome.test(deviatedStep) && !isLowPriorityBiome.test(bestStep)) {
            return bestStep;
        }

        return deviatedStep;
    }

    /**
     * Finds a neighbor that deviates left or right from the best step.
     * Uses odd-r offset coordinates via HexComposeUtil.
     */
    private HexVector2 findDeviatedNeighbor(HexVector2 current, HexVector2 bestStep,
                                            List<HexVector2> neighbors, boolean deviateLeft) {
        // EDGE ordering for clockwise rotation
        EDGE[] clockwiseOrder = {
            EDGE.NORTH_EAST,
            EDGE.EAST,
            EDGE.SOUTH_EAST,
            EDGE.SOUTH_WEST,
            EDGE.WEST,
            EDGE.NORTH_WEST
        };

        // Find which EDGE direction corresponds to bestStep
        EDGE bestEdge;
        try {
            bestEdge = RoadAndRiverConnector.determineSide(current, bestStep);
        } catch (IllegalArgumentException e) {
            return bestStep;
        }

        // Find current direction index
        int currentDirIndex = -1;
        for (int i = 0; i < clockwiseOrder.length; i++) {
            if (clockwiseOrder[i] == bestEdge) {
                currentDirIndex = i;
                break;
            }
        }

        if (currentDirIndex == -1) {
            return bestStep;
        }

        // Rotate left (counter-clockwise) or right (clockwise)
        int deviatedIndex;
        if (deviateLeft) {
            deviatedIndex = (currentDirIndex - 1 + clockwiseOrder.length) % clockwiseOrder.length;
        } else {
            deviatedIndex = (currentDirIndex + 1) % clockwiseOrder.length;
        }

        return HexComposeUtil.getNeighborPosition(current, clockwiseOrder[deviatedIndex]);
    }

    /**
     * Gets all 6 hex neighbors for a coordinate using odd-r offset coordinates.
     */
    private List<HexVector2> getHexNeighbors(HexVector2 coord) {
        List<HexVector2> neighbors = new ArrayList<>();
        for (EDGE edge : EDGE.values()) {
            neighbors.add(HexComposeUtil.getNeighborPosition(coord, edge));
        }
        return neighbors;
    }

    /**
     * Gets the terrain level at a coordinate from the biome gridMap.
     * For mountains, uses landLevel parameter.
     * For other biomes, uses default landLevel or fallback.
     *
     * @param coord The coordinate to check
     * @param gridMap Map of biomes by coordinate
     * @return Terrain level (typically 50-200)
     */
    /**
     * Checks if a coordinate has a low-priority biome for river routing.
     * Low priority: Ocean, Coast, Island biomes, or empty space (no biome placed).
     * Empty coordinates will become coast/ocean fillers after routing,
     * so rivers should prefer to stay on placed land biomes.
     */
    private boolean isLowPriorityBiome(HexVector2 coord, Map<String, Biome> gridMap) {
        Biome biome = gridMap.get(coordKey(coord));
        // No biome = empty space that will become coast/ocean filler = low priority
        if (biome == null) return true;
        if (biome.getType() == null) return false;
        return biome.getType() == BiomeType.OCEAN
            || biome.getType() == BiomeType.COAST
            || biome.getType() == BiomeType.ISLAND;
    }

    private int getTerrainLevel(HexVector2 coord, Map<String, Biome> gridMap) {
        Biome biome = gridMap.get(coordKey(coord));

        if (biome == null) {
            // No biome at this coordinate - assume ocean level
            return 50;
        }

        // Check if biome has landLevel parameter
        if (biome.getParameters() != null && biome.getParameters().containsKey("g_asl")) {
            try {
                return Integer.parseInt(biome.getParameters().get("g_asl"));
            } catch (NumberFormatException e) {
                log.warn("Invalid landLevel for biome at {},{}: {}",
                    coord.getQ(), coord.getR(), biome.getParameters().get("g_asl"));
            }
        }

        // Default landLevel based on biome type
        if (biome.getType() != null) {
            return switch (biome.getType()) {
                case MOUNTAINS -> 120;  // Default mountain level
                case PLAINS, FOREST -> 80;
                case DESERT -> 75;
                case SWAMP -> 60;
                case COAST -> 55;
                case ISLAND -> 70;
                case OCEAN -> 45;
                default -> 70;  // Default fallback
            };
        }

        return 70; // Ultimate fallback
    }

    /**
     * Creates flow segments for a route and adds them to FeatureHexGrids.
     * Rivers use a two-pass level calculation via flow.calculateRouteLevels()
     * to ensure continuity (endLevel[N] = startLevel[N+1]) and monotonically decreasing levels.
     */
    private int createFlowSegments(Flow flow, List<HexVector2> route,
                                   Map<String, Biome> gridMap,
                                   HexComposition prepared) {
        int segmentCount = 0;
        Integer previousToLevel = null; // Track TO level from previous segment (becomes FROM of next segment)

        // Pre-calculate route levels if flow supports it (rivers use two-pass approach)
        Integer fixedLevelForRoute = getFlowFixedLevel(flow);
        ToIntFunction<HexVector2> rawLevelAt = coord -> {
            Biome biome = gridMap.get(coordKey(coord));
            return flow.calculateSegmentLevel(
                getBiomeLandLevel(biome), getBiomeLandOffset(biome),
                null, null, null, fixedLevelForRoute);
        };
        List<Integer> routeLevels = flow.calculateRouteLevels(route, rawLevelAt, SEA_LEVEL);

        // Pre-compute random edge numerators (1, 2, or 3) for each grid-to-grid transition.
        // Seeded from flow name so results are deterministic.
        // edgeNumerators[j] is the numerator for the edge between route[j] and route[j+1].
        Random edgeRandom = new Random(flow.getName().hashCode());
        int[] edgeNumerators = new int[route.size()]; // index j = transition from j to j+1
        for (int j = 0; j < route.size() - 1; j++) {
            edgeNumerators[j] = edgeRandom.nextInt(3) + 1; // 1, 2, or 3
        }

        for (int i = 0; i < route.size(); i++) {
            HexVector2 coord = route.get(i);
            EDGE fromSide = null;
            EDGE toSide = null;
            String fromPosition = null;
            String toPosition = null;
            Integer fromLx = null;
            Integer fromLz = null;
            Integer toLx = null;
            Integer toLz = null;

            // Determine entry side (from where the flow enters THIS grid)
            if (i > 0) {
                HexVector2 prev = route.get(i - 1);
                // Direction from prev to coord gives us the exit side of prev
                // But we need the ENTRY side of THIS grid, which is the opposite!
                EDGE directionFromPrev = RoadAndRiverConnector.determineSide(prev, coord);
                fromSide = RoadAndRiverConnector.getOppositeSide(directionFromPrev);
                // Generate HexLocal position string with same numerator as the exit of previous grid
                fromPosition = edgeToHexLocalPosition(fromSide, edgeNumerators[i - 1]);
            } else if (i == 0) {
                // First segment
                if (flow.isClosedLoop() && route.size() > 1) {
                    // Closed loop: first segment comes from last
                    HexVector2 last = route.get(route.size() - 1);
                    EDGE directionFromLast = RoadAndRiverConnector.determineSide(last, coord);
                    fromSide = RoadAndRiverConnector.getOppositeSide(directionFromLast);
                    log.debug("Closed loop: first segment comes from last ({})", fromSide);
                } else if (flow.getStartPointFeature() != null) {
                    // Start is a Point - use Point's HexLocal position
                    Point startPoint = flow.getStartPointFeature();

                    // Extract HexLocal position string from pointComposed
                    if (startPoint.getPointComposed() != null) {
                        if (startPoint.getPointComposed().getHexLocalPosition() != null) {
                            fromPosition = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(
                                startPoint.getPointComposed().getHexLocalPosition());
                            log.debug("First segment uses Point '{}' HexLocalPosition: {}",
                                startPoint.getName(), fromPosition);
                        } else if (startPoint.getPointComposed().getHexLocalEdgeVector() != null) {
                            fromPosition = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(
                                startPoint.getPointComposed().getHexLocalEdgeVector());
                            log.debug("First segment uses Point '{}' HexLocalEdgeVector: {}",
                                startPoint.getName(), fromPosition);
                        }
                    }

                    // Also set fromLx/fromLz for backward compatibility if available
                    if (startPoint.getPlacedLx() != null && startPoint.getPlacedLz() != null) {
                        fromLx = startPoint.getPlacedLx();
                        fromLz = startPoint.getPlacedLz();
                        if (fromPosition == null) {
                            log.debug("First segment uses Point '{}' deprecated lx/lz: {}, {}",
                                startPoint.getName(), fromLx, fromLz);
                        }
                    }

                    fromSide = null; // Don't use SIDE when using position/lx/lz
                }
            }

            // Determine exit side (to where the flow exits THIS grid)
            if (i < route.size() - 1) {
                HexVector2 next = route.get(i + 1);
                toSide = RoadAndRiverConnector.determineSide(coord, next);
                // Generate HexLocal position string with pre-computed numerator for this transition
                toPosition = edgeToHexLocalPosition(toSide, edgeNumerators[i]);
            } else if (i == route.size() - 1) {
                // Last segment
                if (flow.isClosedLoop() && route.size() > 1) {
                    // Closed loop: connect last segment back to first
                    HexVector2 first = route.get(0);
                    toSide = RoadAndRiverConnector.determineSide(coord, first);
                    toPosition = edgeToHexLocalPosition(toSide, edgeNumerators[0]);
                    log.debug("Closed loop: last segment connects to first ({})", toSide);
                } else if (flow.getEndPointFeature() != null) {
                    // End is a Point - use Point's HexLocal position
                    Point endPoint = flow.getEndPointFeature();

                    // Extract HexLocal position string from pointComposed
                    if (endPoint.getPointComposed() != null) {
                        if (endPoint.getPointComposed().getHexLocalPosition() != null) {
                            toPosition = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(
                                endPoint.getPointComposed().getHexLocalPosition());
                            log.debug("Last segment uses Point '{}' HexLocalPosition: {}",
                                endPoint.getName(), toPosition);
                        } else if (endPoint.getPointComposed().getHexLocalEdgeVector() != null) {
                            toPosition = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(
                                endPoint.getPointComposed().getHexLocalEdgeVector());
                            log.debug("Last segment uses Point '{}' HexLocalEdgeVector: {}",
                                endPoint.getName(), toPosition);
                        }
                    }

                    // Also set toLx/toLz for backward compatibility if available
                    if (endPoint.getPlacedLx() != null && endPoint.getPlacedLz() != null) {
                        toLx = endPoint.getPlacedLx();
                        toLz = endPoint.getPlacedLz();
                        if (toPosition == null) {
                            log.debug("Last segment uses Point '{}' deprecated lx/lz: {}, {}",
                                endPoint.getName(), toLx, toLz);
                        }
                    }

                    toSide = null; // Don't use SIDE when using position/lx/lz
                }
            }

            // Calculate FROM and TO levels for this segment
            Integer fromLevel;
            Integer toLevel;

            if (routeLevels != null) {
                // Pre-calculated levels (rivers): ensures continuity and monotonic decrease
                fromLevel = routeLevels.get(i);
                toLevel = (i < route.size() - 1) ? routeLevels.get(i + 1) : routeLevels.get(i);
            } else if (i == 0) {
                // First segment: Calculate both FROM and TO (roads, walls)
                fromLevel = calculateSegmentLevel(flow, coord, route, i, null, gridMap);
                toLevel = calculateSegmentLevel(flow, coord, route, i, fromLevel, gridMap);
            } else {
                // Subsequent segments: FROM = previous TO, calculate new TO (roads, walls)
                fromLevel = previousToLevel;
                toLevel = calculateSegmentLevel(flow, coord, route, i, fromLevel, gridMap);
            }

            // Create flow segment with SIDE, position strings, lx/lz coordinates, and FROM/TO levels
            FlowSegment segment = createFlowSegment(flow, fromSide, toSide, fromPosition, toPosition,
                fromLx, fromLz, toLx, toLz, fromLevel, toLevel);

            // Add segment directly to central registry (no longer to flow.hexGrids!)
            // This merges the segment into existing grid (created by Biome) or creates new orphan grid
            FeatureHexGrid centralGrid = prepared.getOrCreateFeatureHexGrid(coord);
            centralGrid.addFlowSegment(segment);

            // Store TO level for next segment's FROM
            previousToLevel = toLevel;

            segmentCount++;
        }

        return segmentCount;
    }

    /**
     * Calculates the level for a flow segment at the given grid.
     * Uses flow.calculateSegmentLevel() with biome data.
     */
    private Integer calculateSegmentLevel(Flow flow, HexVector2 gridCoord,
                                          List<HexVector2> route, int routeIndex,
                                          Integer previousLevel,
                                          Map<String, Biome> gridMap) {
        // Get biome at current grid
        Biome gridABiome = gridMap.get(coordKey(gridCoord));

        // Get biome at next grid (if exists)
        Biome gridBBiome = null;
        if (routeIndex < route.size() - 1) {
            HexVector2 nextCoord = route.get(routeIndex + 1);
            gridBBiome = gridMap.get(coordKey(nextCoord));
        }

        // Extract landLevel and landOffset from biomes
        Integer gridALandLevel = getBiomeLandLevel(gridABiome);
        Integer gridALandOffset = getBiomeLandOffset(gridABiome);
        Integer gridBLandLevel = getBiomeLandLevel(gridBBiome);
        Integer gridBLandOffset = getBiomeLandOffset(gridBBiome);

        // Get fixed level for FIXED mode
        Integer fixedLevel = getFlowFixedLevel(flow);

        // Calculate level using flow's method
        return flow.calculateSegmentLevel(gridALandLevel, gridALandOffset,
            gridBLandLevel, gridBLandOffset, previousLevel, fixedLevel);
    }

    /**
     * Gets landLevel from biome parameters.
     */
    private Integer getBiomeLandLevel(Biome biome) {
        if (biome == null || biome.getParameters() == null) {
            return null;
        }

        // Get landLevel directly from biome parameters
        if (biome.getParameters().containsKey("g_asl")) {
            try {
                return Integer.parseInt(biome.getParameters().get("g_asl"));
            } catch (NumberFormatException e) {
                log.warn("Invalid g_asl value in biome {}: {}", biome.getName(),
                    biome.getParameters().get("g_asl"));
            }
        }

        return null;
    }

    /**
     * Gets landOffset from biome parameters.
     */
    private Integer getBiomeLandOffset(Biome biome) {
        if (biome == null || biome.getParameters() == null) {
            return null;
        }

        // Get landOffset directly from biome parameters
        if (biome.getParameters().containsKey("g_offset")) {
            try {
                return Integer.parseInt(biome.getParameters().get("g_offset"));
            } catch (NumberFormatException e) {
                log.warn("Invalid g_offset value in biome {}: {}", biome.getName(),
                    biome.getParameters().get("g_offset"));
            }
        }

        return null;
    }

    /**
     * Gets the fixed level value for a flow (used in FIXED mode).
     */
    private Integer getFlowFixedLevel(Flow flow) {
        if (flow instanceof River river) {
            return river.getLevel();
        } else if (flow instanceof Road road) {
            return road.getLevel();
        } else if (flow instanceof Wall wall) {
            return wall.getLevel();
        }
        return null;
    }

    /**
     * Converts an EDGE and numerator to a HexLocal position string.
     * Format: "<EDGE numerator/4>" where numerator is 1-3 (1=north, 2=center, 3=south along edge)
     *
     * @param edge The hex edge
     * @param numerator Position along edge (1-3)
     * @return HexLocal position string like "<NE 2/4>"
     */
    private String edgeToHexLocalPosition(EDGE edge, int numerator) {
        if (edge == null || numerator < 1 || numerator > 3) {
            return null;
        }

        String edgeAbbrev = switch (edge) {
            case NORTH_EAST -> "NE";
            case EAST -> "E";
            case SOUTH_EAST -> "SE";
            case SOUTH_WEST -> "SW";
            case WEST -> "W";
            case NORTH_WEST -> "NW";
        };

        return String.format("<%s %d/4>", edgeAbbrev, numerator);
    }

    /**
     * Creates a FlowSegment from PreparedFlow
     */
    private FlowSegment createFlowSegment(Flow flow, EDGE fromSide, EDGE toSide,
                                          String fromPosition, String toPosition,
                                          Integer fromLx, Integer fromLz,
                                          Integer toLx, Integer toLz,
                                          Integer fromLevel, Integer toLevel) {
        FlowSegment.FlowSegmentBuilder builder = FlowSegment.builder()
            .flowType(flow.getType())
            .fromSide(fromSide)
            .toSide(toSide)
            .fromPosition(fromPosition)
            .toPosition(toPosition)
            .fromLx(fromLx)
            .fromLz(fromLz)
            .toLx(toLx)
            .toLz(toLz)
            .width(flow.getCalculatedWidthBlocks())
            .flowFeatureId(flow.getFeatureId())
            .fromLevel(fromLevel)
            .toLevel(toLevel);

        // Type-specific attributes
        if (flow instanceof Road road) {
            builder.type(road.getRoadType());
            builder.level(fromLevel != null ? fromLevel : road.getLevel()); // Deprecated field for backward compatibility
        } else if (flow instanceof River river) {
            builder.depth(river.getDepth());
            builder.level(fromLevel != null ? fromLevel : river.getLevel()); // Deprecated field for backward compatibility
        } else if (flow instanceof Wall wall) {
            builder.height(wall.getHeight());
            builder.level(fromLevel != null ? fromLevel : wall.getLevel()); // Deprecated field for backward compatibility
            builder.material(wall.getMaterial());
        }

        return builder.build();
    }

    /**
     * Finds a FeatureHexGrid in central registry at the given coordinate.
     * Returns null if no FeatureHexGrid exists at that coordinate.
     */
    private FeatureHexGrid findFeatureHexGridInBiome(HexVector2 coord, Map<String, Biome> gridMap, HexComposition composition) {
        // Find the biome at this coordinate
        Biome biome = gridMap.get(coordKey(coord));

        if (biome == null) {
            // No biome at this coordinate (flow crosses empty space or filler)
            return null;
        }

        // Find existing FeatureHexGrid in central registry
        String coordKey = de.mhus.nimbus.shared.utils.TypeUtil.toStringHexCoord(coord);
        FeatureHexGrid existing = null;
        if (composition.getFeatureHexGridRegistry() != null) {
            existing = composition.getFeatureHexGridRegistry().get(coordKey);
        }

        if (existing == null) {
            // Should not happen - biomes should already have FeatureHexGrids from BiomeComposer
            log.warn("Biome {} has no FeatureHexGrid at {} in central registry", biome.getName(), coord);
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

        // Collect all Flow subclasses from features
        if (prepared.getFeatures() != null) {
            for (Feature feature : prepared.getFeatures()) {
                if (feature instanceof Flow) {
                    flows.add((Flow) feature);
                }
            }
        }

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

        // Iterate directly over central registry instead of flow.getHexGrids()
        // This ensures we find ALL grids with road segments, even orphan grids
        if (composition.getFeatureHexGridRegistry() == null) {
            log.warn("Central registry is null, cannot convert road segments");
            return;
        }

        int convertedGrids = 0;

        for (FeatureHexGrid centralGrid : composition.getFeatureHexGridRegistry().values()) {
            if (centralGrid == null) {
                continue;
            }

            // Get road segments for this grid (from this specific flow)
            List<FlowSegment> roadSegments = centralGrid.getFlowSegmentsByType(FlowType.ROAD);
            if (roadSegments.isEmpty()) {
                continue;
            }

            // Filter segments that belong to this flow
            List<FlowSegment> flowRoadSegments = roadSegments.stream()
                .filter(seg -> flow.getFeatureId().equals(seg.getFlowFeatureId()))
                .toList();

            if (flowRoadSegments.isEmpty()) {
                continue;
            }

            convertedGrids++;

            // Convert each FlowSegment to RoadConfigPart
            for (FlowSegment segment : flowRoadSegments) {
                // Get fromLevel and toLevel from segment
                Integer fromLevel = segment.getFromLevel();
                Integer toLevel = segment.getToLevel();

                // Create ROUTE parts for entry point (priority: position > lx/lz > side)
                if (segment.hasFromPosition()) {
                    // Use HexLocal position string (Point endpoint or grid-to-grid transition)
                    RoadConfigPart part = RoadConfigPart.createRoutePositionStringPartWithLevels(
                        segment.getFromPosition(),
                        segment.getWidth(),
                        fromLevel,
                        toLevel,
                        segment.getType()
                    );
                    centralGrid.addRoadConfigPart(part);
                    log.debug("Added position-string route part (from) '{}' with levels {}/{}",
                        segment.getFromPosition(), fromLevel, toLevel);
                } else if (segment.hasFromCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint, deprecated)
                    RoadConfigPart part = RoadConfigPart.createRoutePositionPartWithLevels(
                        segment.getFromLx(),
                        segment.getFromLz(),
                        segment.getWidth(),
                        fromLevel,
                        toLevel,
                        segment.getType()
                    );
                    centralGrid.addRoadConfigPart(part);
                    log.debug("Added position-based route part (from) at lx={}, lz={} with levels {}/{}",
                        segment.getFromLx(), segment.getFromLz(), fromLevel, toLevel);
                } else if (segment.getFromSide() != null) {
                    // Use SIDE (Biome endpoint, legacy fallback)
                    RoadConfigPart part = RoadConfigPart.createRouteSidePartWithLevels(
                        segment.getFromSide(),
                        segment.getWidth(),
                        fromLevel,
                        toLevel,
                        segment.getType()
                    );
                    centralGrid.addRoadConfigPart(part);
                    log.debug("Added SIDE-based route part (from) at {} with levels {}/{}",
                        segment.getFromSide(), fromLevel, toLevel);
                }

                // Create ROUTE parts for exit point (priority: position > lx/lz > side)
                if (segment.hasToPosition()) {
                    // Use HexLocal position string (Point endpoint or grid-to-grid transition)
                    RoadConfigPart part = RoadConfigPart.createRoutePositionStringPartWithLevels(
                        segment.getToPosition(),
                        segment.getWidth(),
                        fromLevel,
                        toLevel,
                        segment.getType()
                    );
                    centralGrid.addRoadConfigPart(part);
                    log.debug("Added position-string route part (to) '{}' with levels {}/{}",
                        segment.getToPosition(), fromLevel, toLevel);
                } else if (segment.hasToCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint, deprecated)
                    RoadConfigPart part = RoadConfigPart.createRoutePositionPartWithLevels(
                        segment.getToLx(),
                        segment.getToLz(),
                        segment.getWidth(),
                        fromLevel,
                        toLevel,
                        segment.getType()
                    );
                    centralGrid.addRoadConfigPart(part);
                    log.debug("Added position-based route part (to) at lx={}, lz={} with levels {}/{}",
                        segment.getToLx(), segment.getToLz(), fromLevel, toLevel);
                } else if (segment.getToSide() != null && !segment.getToSide().equals(segment.getFromSide())) {
                    // Use SIDE (Biome endpoint, legacy fallback)
                    RoadConfigPart part = RoadConfigPart.createRouteSidePartWithLevels(
                        segment.getToSide(),
                        segment.getWidth(),
                        fromLevel,
                        toLevel,
                        segment.getType()
                    );
                    centralGrid.addRoadConfigPart(part);
                    log.debug("Added SIDE-based route part (to) at {} with levels {}/{}",
                        segment.getToSide(), fromLevel, toLevel);
                }
            }
        }

        log.info("Converted road segments to config parts for {} grids (flow: {})",
            convertedGrids, flow.getName());
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

        // Iterate directly over central registry instead of flow.getHexGrids()
        if (composition.getFeatureHexGridRegistry() == null) {
            log.warn("Central registry is null, cannot convert river segments");
            return;
        }

        int convertedGrids = 0;

        for (FeatureHexGrid centralGrid : composition.getFeatureHexGridRegistry().values()) {
            if (centralGrid == null) {
                continue;
            }

            // Get river segments for this grid (from this specific flow)
            List<FlowSegment> riverSegments = centralGrid.getFlowSegmentsByType(FlowType.RIVER);
            if (riverSegments.isEmpty()) {
                continue;
            }

            // Filter segments that belong to this flow
            List<FlowSegment> flowRiverSegments = riverSegments.stream()
                .filter(seg -> flow.getFeatureId().equals(seg.getFlowFeatureId()))
                .toList();

            if (flowRiverSegments.isEmpty()) {
                continue;
            }

            convertedGrids++;

            // Convert each FlowSegment to RiverConfigPart
            for (FlowSegment segment : flowRiverSegments) {
                String groupId = segment.getFlowFeatureId() != null ? segment.getFlowFeatureId() : river.getFeatureId();

                // Get FROM and TO levels (fallback to deprecated 'level' if not set)
                Integer fromLevel = segment.getFromLevel() != null ? segment.getFromLevel() : segment.getLevel();
                Integer toLevel = segment.getToLevel() != null ? segment.getToLevel() : segment.getLevel();

                // Create FROM parts (priority: position string > SIDE)
                if (segment.hasFromPosition()) {
                    // Use HexLocal position string (grid-to-grid transition or Point endpoint)
                    RiverConfigPart part = RiverConfigPart.createFromPositionStringPart(
                        segment.getFromPosition(),
                        segment.getWidth(),
                        segment.getDepth(),
                        fromLevel,
                        groupId
                    );
                    centralGrid.addRiverConfigPart(part);
                    log.debug("Added position-string-based river FROM part: {} with level={}",
                        segment.getFromPosition(), fromLevel);
                } else if (segment.getFromSide() != null) {
                    // Use SIDE (fallback for backward compatibility)
                    RiverConfigPart part = RiverConfigPart.createFromPart(
                        segment.getFromSide(),
                        segment.getWidth(),
                        segment.getDepth(),
                        fromLevel,
                        groupId
                    );
                    centralGrid.addRiverConfigPart(part);
                    log.debug("Added SIDE-based river FROM part: {} with level={}", segment.getFromSide(), fromLevel);
                }

                // Create TO parts (priority: position string > SIDE)
                if (segment.hasToPosition()) {
                    // Use HexLocal position string (grid-to-grid transition or Point endpoint)
                    RiverConfigPart part = RiverConfigPart.createToPositionStringPart(
                        segment.getToPosition(),
                        segment.getWidth(),
                        segment.getDepth(),
                        toLevel,
                        groupId
                    );
                    centralGrid.addRiverConfigPart(part);
                    log.debug("Added position-string-based river TO part: {} with level={}",
                        segment.getToPosition(), toLevel);
                } else if (segment.getToSide() != null) {
                    // Use SIDE (fallback for backward compatibility)
                    RiverConfigPart part = RiverConfigPart.createToPart(
                        segment.getToSide(),
                        segment.getWidth(),
                        segment.getDepth(),
                        toLevel,
                        groupId
                    );
                    centralGrid.addRiverConfigPart(part);
                    log.debug("Added SIDE-based river TO part: {} with level={}", segment.getToSide(), toLevel);
                }
            }
        }

        log.info("Converted river segments to config parts for {} grids (flow: {})",
            convertedGrids, flow.getName());
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

        // Iterate directly over central registry instead of flow.getHexGrids()
        if (composition.getFeatureHexGridRegistry() == null) {
            log.warn("Central registry is null, cannot convert wall segments");
            return;
        }

        int convertedGrids = 0;

        for (FeatureHexGrid centralGrid : composition.getFeatureHexGridRegistry().values()) {
            if (centralGrid == null) {
                continue;
            }

            // Get wall segments for this grid (from this specific flow)
            List<FlowSegment> wallSegments = centralGrid.getFlowSegmentsByType(FlowType.WALL);
            if (wallSegments.isEmpty()) {
                continue;
            }

            // Filter segments that belong to this flow
            List<FlowSegment> flowWallSegments = wallSegments.stream()
                .filter(seg -> flow.getFeatureId().equals(seg.getFlowFeatureId()))
                .toList();

            if (flowWallSegments.isEmpty()) {
                continue;
            }

            convertedGrids++;

            // Convert each FlowSegment to WallConfigPart
            for (FlowSegment segment : flowWallSegments) {
                // Create parts for entry point (fromPosition or fromSide)
                if (segment.getFromPosition() != null) {
                    // Use HexLocal position string (Point endpoint)
                    WallConfigPart part = WallConfigPart.createPositionPart(
                        segment.getFromPosition(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    centralGrid.addWallConfigPart(part);
                    log.debug("Added position-based wall part (from) at position={}",
                        segment.getFromPosition());
                } else if (segment.hasFromCoordinates()) {
                    // Fallback: Use deprecated lx/lz coordinates
                    WallConfigPart part = WallConfigPart.createPositionPartDeprecated(
                        segment.getFromLx(),
                        segment.getFromLz(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    centralGrid.addWallConfigPart(part);
                    log.warn("Using deprecated lx/lz for wall (from) at grid ({},{}): lx={}, lz={}",
                        centralGrid.getCoordinate().getQ(), centralGrid.getCoordinate().getR(),
                        segment.getFromLx(), segment.getFromLz());
                } else if (segment.getFromSide() != null) {
                    // Use SIDE (Biome endpoint)
                    WallConfigPart part = WallConfigPart.createSidePart(
                        segment.getFromSide(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    centralGrid.addWallConfigPart(part);
                }

                // Create parts for exit point (toPosition or toSide)
                if (segment.getToPosition() != null) {
                    // Use HexLocal position string (Point endpoint)
                    WallConfigPart part = WallConfigPart.createPositionPart(
                        segment.getToPosition(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    centralGrid.addWallConfigPart(part);
                    log.debug("Added position-based wall part (to) at position={}",
                        segment.getToPosition());
                } else if (segment.hasToCoordinates()) {
                    // Fallback: Use deprecated lx/lz coordinates
                    WallConfigPart part = WallConfigPart.createPositionPartDeprecated(
                        segment.getToLx(),
                        segment.getToLz(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    centralGrid.addWallConfigPart(part);
                    log.warn("Using deprecated lx/lz for wall (to) at grid ({},{}): lx={}, lz={}",
                        centralGrid.getCoordinate().getQ(), centralGrid.getCoordinate().getR(),
                        segment.getToLx(), segment.getToLz());
                } else if (segment.getToSide() != null && !segment.getToSide().equals(segment.getFromSide())) {
                    // Use SIDE (Biome endpoint)
                    WallConfigPart part = WallConfigPart.createSidePart(
                        segment.getToSide(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    centralGrid.addWallConfigPart(part);
                }
            }
        }

        log.info("Converted wall segments to config parts for {} grids (flow: {})",
            convertedGrids, flow.getName());
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
     * Collects Area grids into a map by coordinate key.
     * Only Structures have hexGrids - Biomes store them in central registry.
     * Note: Flows are not Areas, they are handled separately.
     */
    private void collectAreaGrids(Area area, Map<String, FeatureHexGrid> areaGridMap) {
        List<FeatureHexGrid> hexGrids = null;

        // Only Structures have local hexGrids (Flows are not Areas)
        if (area instanceof de.mhus.nimbus.world.generator.composer.structure.Structure) {
            hexGrids = ((de.mhus.nimbus.world.generator.composer.structure.Structure) area).getHexGrids();
        }
        // Note: Biomes no longer have local hexGrids - they use central registry

        if (hexGrids == null) {
            return;
        }

        for (FeatureHexGrid hexGrid : hexGrids) {
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
     * Note: Biomes no longer have local hexGrids - they are stored in central registry.
     * This method now collects from central HexComposition.featureHexGridRegistry.
     *
     * @param placementResult The placement result with all PlacedBiomes
     * @param areaGridMap Map to add grids to
     * @param composition The composition with central FeatureHexGrid registry
     */
    private void collectAreaGridsFromPlacedBiomes(BiomePlacementResult placementResult,
                                                   Map<String, FeatureHexGrid> areaGridMap,
                                                   HexComposition composition) {
        if (placementResult == null || placementResult.getPlacedBiomes() == null) {
            log.warn("placementResult or PlacedBiomes is null!");
            return;
        }

        // Collect from central FeatureHexGrid registry instead of biome.getHexGrids()
        if (composition.getFeatureHexGridRegistry() == null || composition.getFeatureHexGridRegistry().isEmpty()) {
            log.warn("Central FeatureHexGrid registry is empty!");
            return;
        }

        int collectedCount = 0;

        // Iterate through all PlacedBiomes and collect their coordinates from central registry
        for (PlacedBiome placedBiome : placementResult.getPlacedBiomes()) {
            Biome biome = placedBiome.getBiome();
            if (biome == null) {
                continue;
            }

            // For each coordinate of this biome, get the FeatureHexGrid from central registry
            for (HexVector2 coord : placedBiome.getCoordinates()) {
                String coordKey = de.mhus.nimbus.shared.utils.TypeUtil.toStringHexCoord(coord);
                FeatureHexGrid hexGrid = composition.getFeatureHexGridRegistry().get(coordKey);
                if (hexGrid != null) {
                    // Add to map (may overwrite, but that's OK - same coordinate)
                    areaGridMap.put(coordKey, hexGrid);
                    collectedCount++;
                }
            }
        }

        log.debug("Collected {} FeatureHexGrids from central registry for {} PlacedBiomes",
            collectedCount, placementResult.getPlacedBiomes().size());
    }

    /**
     * Creates coordinate key
     */
    private String coordKey(HexVector2 coord) {
        return TypeUtil.toStringHexCoord(coord);
    }

    /**
     * Composes a SideWall by finding edge grids of the target biome
     * and adding sidewall parameters to them.
     *
     * @param sideWall The SideWall feature to compose
     * @param prepared The prepared composition
     * @param placementResult Result from BiomeComposer
     * @return Number of grids configured with sidewall
     */
    private int composeSideWall(SideWall sideWall, HexComposition prepared,
                                BiomePlacementResult placementResult) {
        log.debug("Composing SideWall '{}' for target '{}'", sideWall.getName(), sideWall.getTargetBiomeId());

        if (sideWall.getTargetBiomeId() == null) {
            log.warn("SideWall '{}' has no targetBiomeId", sideWall.getName());
            return 0;
        }

        // Find target biome
        Biome targetBiome = findBiomeByFeatureId(sideWall.getTargetBiomeId(), prepared, placementResult);
        if (targetBiome == null) {
            log.warn("Could not find target biome '{}' for SideWall '{}'",
                sideWall.getTargetBiomeId(), sideWall.getName());
            return 0;
        }

        // Note: Biomes no longer have local hexGrids - they use central registry
        // findBiomeEdgeGrids() uses placementResult.coordinates and central registry

        // Find edge grids of the target biome
        List<FeatureHexGrid> edgeGrids = findBiomeEdgeGrids(targetBiome, placementResult, prepared);
        if (edgeGrids.isEmpty()) {
            log.warn("Target biome '{}' has no edge grids", targetBiome.getName());
            return 0;
        }

        log.debug("Found {} edge grids for target biome '{}'", edgeGrids.size(), targetBiome.getName());

        // Build sidewall JSON configuration
        String sidewallJson = buildSideWallJson(sideWall);

        // Add sidewall parameter to edge grids
        int configuredCount = 0;
        for (FeatureHexGrid edgeGrid : edgeGrids) {
            // Filter by sides if specified
            if (sideWall.getSides() != null && !sideWall.getSides().isEmpty()) {
                // Only add sidewall to grids that have the requested sides exposed
                List<EDGE> exposedSides = getExposedSides(edgeGrid, targetBiome, placementResult, prepared);
                boolean hasRequestedSide = false;
                for (EDGE side : sideWall.getSides()) {
                    if (exposedSides.contains(side)) {
                        hasRequestedSide = true;
                        break;
                    }
                }
                if (!hasRequestedSide) {
                    continue;
                }
            }

            edgeGrid.addParameter("g_sidewall", sidewallJson);
            configuredCount++;
        }

        log.debug("Configured {} edge grids with sidewall for '{}'", configuredCount, sideWall.getName());

        // Update feature status
        if (configuredCount > 0) {
            sideWall.setStatus(FeatureStatus.COMPOSED);
        }

        return configuredCount;
    }

    /**
     * Finds edge grids of a biome (grids that have at least one side not connected to another biome grid).
     * Uses PlacementResult coordinates and central FeatureHexGrid registry.
     */
    private List<FeatureHexGrid> findBiomeEdgeGrids(Biome biome, BiomePlacementResult placementResult, HexComposition composition) {
        List<FeatureHexGrid> edgeGrids = new ArrayList<>();

        // Find PlacedBiome for this biome
        PlacedBiome placedBiome = null;
        for (PlacedBiome pb : placementResult.getPlacedBiomes()) {
            if (pb.getBiome() == biome) {
                placedBiome = pb;
                break;
            }
        }

        if (placedBiome == null) {
            log.warn("Could not find PlacedBiome for biome: {}", biome.getName());
            return edgeGrids;
        }

        // Build set of all biome coordinates for quick lookup
        Set<String> biomeCoords = new HashSet<>();
        for (HexVector2 coord : placedBiome.getCoordinates()) {
            biomeCoords.add(coordKey(coord));
        }

        // Find edge grids (grids with at least one neighbor not in biome)
        // Get FeatureHexGrids from central registry
        for (HexVector2 coord : placedBiome.getCoordinates()) {
            String coordKey = de.mhus.nimbus.shared.utils.TypeUtil.toStringHexCoord(coord);
            FeatureHexGrid grid = composition.getFeatureHexGridRegistry() != null
                ? composition.getFeatureHexGridRegistry().get(coordKey)
                : null;

            if (grid == null) {
                continue;
            }

            List<HexVector2> neighbors = getHexNeighbors(coord);
            boolean isEdge = false;
            for (HexVector2 neighbor : neighbors) {
                if (!biomeCoords.contains(coordKey(neighbor))) {
                    isEdge = true;
                    break;
                }
            }
            if (isEdge) {
                edgeGrids.add(grid);
            }
        }

        return edgeGrids;
    }

    /**
     * Gets which sides of a grid are exposed (facing outside the biome).
     */
    private List<EDGE> getExposedSides(FeatureHexGrid grid, Biome biome,
                                       BiomePlacementResult placementResult,
                                       HexComposition composition) {
        List<EDGE> exposedSides = new ArrayList<>();

        // Find PlacedBiome for this biome
        PlacedBiome placedBiome = null;
        for (PlacedBiome pb : placementResult.getPlacedBiomes()) {
            if (pb.getBiome() == biome) {
                placedBiome = pb;
                break;
            }
        }

        if (placedBiome == null) {
            return exposedSides;
        }

        // Build set of biome coordinates from PlacementResult
        Set<String> biomeCoords = new HashSet<>();
        for (HexVector2 coord : placedBiome.getCoordinates()) {
            biomeCoords.add(coordKey(coord));
        }

        // Check each direction using odd-r offset coordinates
        for (EDGE side : EDGE.values()) {
            HexVector2 neighbor = HexComposeUtil.getNeighborPosition(grid.getCoordinate(), side);

            // Side is exposed if neighbor is not in biome
            if (!biomeCoords.contains(coordKey(neighbor))) {
                exposedSides.add(side);
            }
        }

        return exposedSides;
    }

    /**
     * Builds sidewall JSON configuration from SideWall feature.
     * Format: {"sides": ["NE","E","SE"], "height": 5, "level": 50, "width": 3, "distance": 5, "minimum": 3, "type": 3}
     */
    private String buildSideWallJson(SideWall sideWall) {
        try {
            Map<String, Object> config = new HashMap<>();

            // Sides (if specified, otherwise all sides)
            if (sideWall.getSides() != null && !sideWall.getSides().isEmpty()) {
                List<String> sideNames = new ArrayList<>();
                for (EDGE side : sideWall.getSides()) {
                    sideNames.add(side.name());
                }
                config.put("sides", sideNames);
            } else {
                // All sides
                config.put("sides", List.of("NE", "E", "SE", "SW", "W", "NW"));
            }

            config.put("height", sideWall.getEffectiveHeight());
            config.put("level", sideWall.getEffectiveLevel());
            config.put("width", sideWall.getEffectiveWidthBlocks());
            config.put("distance", sideWall.getEffectiveDistance());
            config.put("minimum", sideWall.getEffectiveMinimum());
            config.put("type", sideWall.getEffectiveMaterialType());
            config.put("material", sideWall.getEffectiveMaterialType()); // Use same as type
            config.put("respectRoad", sideWall.isEffectiveRespectRoad());
            config.put("respectRiver", sideWall.isEffectiveRespectRiver());

            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to build sidewall JSON", e);
            return "{}";
        }
    }

    /**
     * Finds a biome by featureId.
     */
    private Biome findBiomeByFeatureId(String featureId, HexComposition prepared,
                                       BiomePlacementResult placementResult) {
        // First try placed biomes
        for (PlacedBiome placedBiome : placementResult.getPlacedBiomes()) {
            Biome biome = placedBiome.getBiome();
            if (biome != null && featureId.equals(biome.getFeatureId())) {
                return biome;
            }
            if (biome != null && featureId.equals(biome.getName())) {
                return biome;
            }
        }

        // Try features in composition
        if (prepared.getFeatures() != null) {
            for (Feature feature : prepared.getFeatures()) {
                if (feature instanceof Biome) {
                    Biome biome = (Biome) feature;
                    if (featureId.equals(biome.getFeatureId()) || featureId.equals(biome.getName())) {
                        return biome;
                    }
                }
            }
        }

        return null;
    }
}
