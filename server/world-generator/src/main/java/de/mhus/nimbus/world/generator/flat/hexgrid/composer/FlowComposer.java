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
            if (river.getMergeToId() != null) {
                // Rivers can also use Points as merge targets
                Point mergePoint = findPoint(river.getMergeToId(), prepared);
                if (mergePoint != null) {
                    flow.setEndPoint(mergePoint.getPlacedCoordinate());
                    flow.setEndPointFeature(mergePoint);
                    log.debug("Flow '{}' merges at Point '{}' with lx={}, lz={}",
                        flow.getName(), mergePoint.getName(),
                        mergePoint.getPlacedLx(), mergePoint.getPlacedLz());
                } else {
                    // Fall back to Biome
                    HexVector2 mergeCoord = findFeatureCoordinate(river.getMergeToId(),
                        placementResult, prepared);
                    if (mergeCoord == null) {
                        log.warn("Could not find merge point: {}", river.getMergeToId());
                        return false;
                    }
                    flow.setEndPoint(mergeCoord);
                    flow.setEndPointFeature(null);
                }
            }
        }

        // Check for closed loop: startPointId == endPointId
        if (flow instanceof Road road) {
            if (road.getEndPointId() != null && road.getEndPointId().equals(flow.getStartPointId())) {
                flow.setClosedLoop(true);
                log.info("Flow '{}' is a closed loop (start == end)", flow.getName());
            }
        } else if (flow instanceof Wall wall) {
            if (wall.getEndPointId() != null && wall.getEndPointId().equals(flow.getStartPointId())) {
                flow.setClosedLoop(true);
                log.info("Flow '{}' is a closed loop (start == end)", flow.getName());
            }
        }

        // TODO: Resolve waypoints if needed

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
            log.info("Planning closed loop route for flow '{}' around point {},{} with radius {}",
                flow.getName(), start.getQ(), start.getR(), flow.getEffectiveSizeFrom());
            return planClosedLoopRoute(flow, start);
        }

        // Simple case: just start point (area-internal flow)
        if (end == null) {
            route.add(start);
            return route;
        }

        // Use pathfinding to find route (with deviation support)
        List<HexVector2> path = findPath(flow, start, end, gridMap);
        if (path != null && !path.isEmpty()) {
            route.addAll(path);
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
     * Uses pointy-top hex coordinate system.
     *
     * @param center Center coordinate
     * @param radius Ring radius (distance from center)
     * @return List of coordinates forming the ring
     */
    private List<HexVector2> createHexRing(HexVector2 center, int radius) {
        List<HexVector2> ring = new ArrayList<>();

        // Hex direction vectors (pointy-top)
        int[][] directions = {
            {1, -1},  // NE
            {1, 0},   // E
            {0, 1},   // SE
            {-1, 1},  // SW
            {-1, 0},  // W
            {0, -1}   // NW
        };

        // Start at a point 'radius' steps away in direction 4 (W)
        int q = center.getQ() - radius;
        int r = center.getR();

        // Walk around the ring
        for (int i = 0; i < 6; i++) {
            // Walk 'radius' steps in direction i
            for (int j = 0; j < radius; j++) {
                ring.add(HexVector2.builder()
                    .q(q)
                    .r(r)
                    .build());

                // Move in direction i
                q += directions[i][0];
                r += directions[i][1];
            }
        }

        return ring;
    }

    /**
     * Pathfinding between two hex coordinates with deviation support.
     * Supports tendLeft/tendRight for curved routes.
     */
    private List<HexVector2> findPath(Flow flow, HexVector2 start, HexVector2 goal,
                                      Map<String, Biome> gridMap) {
        List<HexVector2> path = new ArrayList<>();
        path.add(start);

        HexVector2 current = start;
        Random random = new Random(flow.getName().hashCode()); // Deterministic based on flow name

        // Get deviation tendencies
        DeviationTendency tendLeft = flow.getTendLeft();
        DeviationTendency tendRight = flow.getTendRight();
        boolean hasDeviation = (tendLeft != null && tendLeft != DeviationTendency.NONE) ||
                               (tendRight != null && tendRight != DeviationTendency.NONE);

        // Move step by step towards goal
        while (!current.equals(goal)) {
            HexVector2 next;

            if (hasDeviation) {
                // Apply deviation logic (with downhill constraint for rivers)
                next = getNextStepWithDeviation(current, goal, tendLeft, tendRight, flow, gridMap, random);
            } else {
                // Simple straight path (with downhill constraint for rivers)
                next = getNextStepTowards(current, goal, flow, gridMap);
            }

            if (next == null || next.equals(current)) {
                // Stuck - check if this is acceptable
                if (flow instanceof River river) {
                    // Check if river reached ocean (no more downhill path)
                    if (isAtOceanLevel(current, gridMap)) {
                        log.info("River '{}' reached ocean at {},{}", flow.getName(), current.getQ(), current.getR());
                        break;
                    }

                    // River is stuck but not at ocean
                    boolean forceFlag = river.getForce() != null && river.getForce();
                    if (forceFlag) {
                        throw new FlowRoutingException("River '" + flow.getName() +
                            "' cannot reach destination - stuck at " + current.getQ() + "," + current.getR() +
                            " (no downhill path available)");
                    } else {
                        log.warn("River '{}' stopped at {},{} - no downhill path (force=false)",
                            flow.getName(), current.getQ(), current.getR());
                        break;
                    }
                } else {
                    // Non-river flow stuck
                    log.warn("Flow '{}' pathfinding stuck at {},{}", flow.getName(), current.getQ(), current.getR());
                    break;
                }
            }
            path.add(next);
            current = next;

            // Safety: max 200 steps (increased for curved paths)
            if (path.size() > 200) {
                log.warn("Path too long, stopping at 200 steps");
                break;
            }
        }

        // Check if goal was reached (for rivers with force=true)
        if (flow instanceof River river && !current.equals(goal)) {
            boolean forceFlag = river.getForce() != null && river.getForce();
            if (forceFlag) {
                throw new FlowRoutingException("River '" + flow.getName() +
                    "' cannot reach destination " + goal.getQ() + "," + goal.getR() +
                    " - stopped at " + current.getQ() + "," + current.getR());
            }
        }

        return path;
    }

    /**
     * Gets next hex step towards goal.
     * Returns a valid hex neighbor (one of 6 directions) that brings us closer to goal.
     * For rivers, enforces downhill flow constraint.
     */
    private HexVector2 getNextStepTowards(HexVector2 current, HexVector2 goal,
                                          Flow flow, Map<String, Biome> gridMap) {
        int dq = goal.getQ() - current.getQ();
        int dr = goal.getR() - current.getR();

        if (dq == 0 && dr == 0) {
            return current; // Already at goal
        }

        // Get all 6 valid hex neighbors
        List<HexVector2> neighbors = getHexNeighbors(current);

        // For rivers, filter neighbors to enforce downhill flow
        if (flow instanceof River river) {
            neighbors = filterNeighborsForRiver(current, neighbors, river, gridMap);
            if (neighbors.isEmpty()) {
                // No valid downhill neighbors - stuck
                log.warn("River '{}' stuck at {},{} - no downhill path",
                    river.getName(), current.getQ(), current.getR());
                return current;
            }
        }

        // Find neighbor that gets us closest to goal
        HexVector2 best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (HexVector2 neighbor : neighbors) {
            int distance = hexDistance(neighbor, goal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = neighbor;
            }
        }

        return best != null ? best : current;
    }

    /**
     * Gets next hex step with deviation support (for curved routes).
     * Randomly deviates left or right based on tendLeft/tendRight probabilities.
     * Always makes progress towards goal to avoid infinite loops.
     * For rivers, enforces downhill flow constraint.
     */
    private HexVector2 getNextStepWithDeviation(HexVector2 current, HexVector2 goal,
                                                DeviationTendency tendLeft,
                                                DeviationTendency tendRight,
                                                Flow flow,
                                                Map<String, Biome> gridMap,
                                                Random random) {
        // Calculate best direction towards goal (with downhill constraint for rivers)
        HexVector2 bestStep = getNextStepTowards(current, goal, flow, gridMap);

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

        // Get all 6 neighbors
        List<HexVector2> neighbors = getHexNeighbors(current);

        // For rivers, filter neighbors to enforce downhill flow
        if (flow instanceof River river) {
            neighbors = filterNeighborsForRiver(current, neighbors, river, gridMap);
            if (neighbors.isEmpty()) {
                // No valid downhill neighbors - use bestStep
                return bestStep;
            }
        }

        // Find the neighbor that represents deviation
        HexVector2 deviatedStep = findDeviatedNeighbor(current, bestStep, neighbors, deviateLeft);

        // If deviated step would take us further from goal than we already are, use best step instead
        int currentDistance = hexDistance(current, goal);
        int deviatedDistance = hexDistance(deviatedStep, goal);

        if (deviatedDistance > currentDistance + 1) {
            // Deviation would take us too far off course
            return bestStep;
        }

        return deviatedStep;
    }

    /**
     * Finds a neighbor that deviates left or right from the best step.
     */
    private HexVector2 findDeviatedNeighbor(HexVector2 current, HexVector2 bestStep,
                                            List<HexVector2> neighbors, boolean deviateLeft) {
        // Find the direction of bestStep relative to current
        int bestDq = bestStep.getQ() - current.getQ();
        int bestDr = bestStep.getR() - current.getR();

        // Hex directions in order (pointy-top): NE, E, SE, SW, W, NW
        int[][] directions = {
            {1, -1},  // NE
            {1, 0},   // E
            {0, 1},   // SE
            {-1, 1},  // SW
            {-1, 0},  // W
            {0, -1}   // NW
        };

        // Find current direction index
        int currentDirIndex = -1;
        for (int i = 0; i < directions.length; i++) {
            if (directions[i][0] == bestDq && directions[i][1] == bestDr) {
                currentDirIndex = i;
                break;
            }
        }

        if (currentDirIndex == -1) {
            // Couldn't find direction, return best step
            return bestStep;
        }

        // Rotate left (counter-clockwise) or right (clockwise)
        int deviatedIndex;
        if (deviateLeft) {
            deviatedIndex = (currentDirIndex - 1 + directions.length) % directions.length;
        } else {
            deviatedIndex = (currentDirIndex + 1) % directions.length;
        }

        int[] deviatedDir = directions[deviatedIndex];
        return HexVector2.builder()
            .q(current.getQ() + deviatedDir[0])
            .r(current.getR() + deviatedDir[1])
            .build();
    }

    /**
     * Calculates hex distance between two coordinates.
     */
    private int hexDistance(HexVector2 a, HexVector2 b) {
        int dq = Math.abs(a.getQ() - b.getQ());
        int dr = Math.abs(a.getR() - b.getR());
        int ds = Math.abs((a.getQ() + a.getR()) - (b.getQ() + b.getR()));
        return (dq + dr + ds) / 2;
    }

    /**
     * Gets all 6 hex neighbors for a coordinate.
     */
    private List<HexVector2> getHexNeighbors(HexVector2 coord) {
        int[][] directions = {
            {1, -1},  // NE
            {1, 0},   // E
            {0, 1},   // SE
            {-1, 1},  // SW
            {-1, 0},  // W
            {0, -1}   // NW
        };

        List<HexVector2> neighbors = new ArrayList<>();
        for (int[] dir : directions) {
            neighbors.add(HexVector2.builder()
                .q(coord.getQ() + dir[0])
                .r(coord.getR() + dir[1])
                .build());
        }
        return neighbors;
    }

    /**
     * Filters neighbors for river flow to enforce downhill constraint.
     * Rivers can only flow to neighbors that:
     * - Have terrain level <= current level (downhill or level) - always enforced
     * - Have terrain level >= ocean level (don't flow through deep ocean) - always enforced
     *
     * Note: Rivers can flow through terrain between ocean and river level.
     * This allows rivers to "cut through" coastal terrain and reach the ocean.
     *
     * @param current Current coordinate
     * @param neighbors All possible neighbors
     * @param river The river being routed
     * @param gridMap Map of biomes by coordinate
     * @return Filtered list of valid neighbors
     */
    private List<HexVector2> filterNeighborsForRiver(HexVector2 current,
                                                     List<HexVector2> neighbors,
                                                     River river,
                                                     Map<String, Biome> gridMap) {
        // Get current terrain level
        int currentLevel = getTerrainLevel(current, gridMap);

        // Ocean level (typically 50) - rivers can flow to this level
        int oceanLevel = 50; // TODO: Get from composition settings

        List<HexVector2> validNeighbors = new ArrayList<>();

        for (HexVector2 neighbor : neighbors) {
            int neighborLevel = getTerrainLevel(neighbor, gridMap);

            // Check downhill constraint: neighbor level must be <= current level
            // Rivers always flow downhill, never uphill
            if (neighborLevel > currentLevel) {
                log.debug("River '{}': rejecting neighbor {},{} (uphill: {} > {})",
                    river.getName(), neighbor.getQ(), neighbor.getR(), neighborLevel, currentLevel);
                continue;
            }

            // Check minimum level: don't flow into deep ocean (below ocean level)
            // Rivers can flow to ocean level and merge with the sea
            if (neighborLevel < oceanLevel) {
                log.debug("River '{}': rejecting neighbor {},{} (below ocean level: {} < {})",
                    river.getName(), neighbor.getQ(), neighbor.getR(), neighborLevel, oceanLevel);
                continue;
            }

            validNeighbors.add(neighbor);
        }

        return validNeighbors;
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
     * Checks if a coordinate is at ocean level (no more valid downhill flow possible).
     * Returns true if the terrain is at or very close to ocean level.
     *
     * @param coord The coordinate to check
     * @param gridMap Map of biomes by coordinate
     * @return true if at ocean level
     */
    private boolean isAtOceanLevel(HexVector2 coord, Map<String, Biome> gridMap) {
        int terrainLevel = getTerrainLevel(coord, gridMap);
        int oceanLevel = 50; // TODO: Get from composition settings

        // Consider it "at ocean" if within 5 levels of ocean level
        return terrainLevel <= (oceanLevel + 5);
    }

    /**
     * Exception thrown when river routing fails with force=true.
     */
    public static class FlowRoutingException extends RuntimeException {
        public FlowRoutingException(String message) {
            super(message);
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
            Integer fromLx = null;
            Integer fromLz = null;
            Integer toLx = null;
            Integer toLz = null;

            // Determine entry side (from where the flow enters THIS grid)
            if (i > 0) {
                HexVector2 prev = route.get(i - 1);
                // Direction from prev to coord gives us the exit side of prev
                // But we need the ENTRY side of THIS grid, which is the opposite!
                SIDE directionFromPrev = RoadAndRiverConnector.determineSide(prev, coord);
                fromSide = RoadAndRiverConnector.getOppositeSide(directionFromPrev);
            } else if (i == 0) {
                // First segment
                if (flow.isClosedLoop() && route.size() > 1) {
                    // Closed loop: first segment comes from last
                    HexVector2 last = route.get(route.size() - 1);
                    SIDE directionFromLast = RoadAndRiverConnector.determineSide(last, coord);
                    fromSide = RoadAndRiverConnector.getOppositeSide(directionFromLast);
                    log.debug("Closed loop: first segment comes from last ({})", fromSide);
                } else if (flow.getStartPointFeature() != null) {
                    // Start is a Point - use Point's lx/lz instead of SIDE
                    Point startPoint = flow.getStartPointFeature();
                    fromLx = startPoint.getPlacedLx();
                    fromLz = startPoint.getPlacedLz();
                    fromSide = null; // Don't use SIDE when using lx/lz
                    log.debug("First segment uses Point '{}' coordinates: lx={}, lz={}",
                        startPoint.getName(), fromLx, fromLz);
                }
            }

            // Determine exit side (to where the flow exits THIS grid)
            if (i < route.size() - 1) {
                HexVector2 next = route.get(i + 1);
                toSide = RoadAndRiverConnector.determineSide(coord, next);
            } else if (i == route.size() - 1) {
                // Last segment
                if (flow.isClosedLoop() && route.size() > 1) {
                    // Closed loop: connect last segment back to first
                    HexVector2 first = route.get(0);
                    toSide = RoadAndRiverConnector.determineSide(coord, first);
                    log.debug("Closed loop: last segment connects to first ({})", toSide);
                } else if (flow.getEndPointFeature() != null) {
                    // End is a Point - use Point's lx/lz instead of SIDE
                    Point endPoint = flow.getEndPointFeature();
                    toLx = endPoint.getPlacedLx();
                    toLz = endPoint.getPlacedLz();
                    toSide = null; // Don't use SIDE when using lx/lz
                    log.debug("Last segment uses Point '{}' coordinates: lx={}, lz={}",
                        endPoint.getName(), toLx, toLz);
                }
            }

            // Create flow segment with both SIDE and lx/lz coordinates
            FlowSegment segment = createFlowSegment(flow, fromSide, toSide, fromLx, fromLz, toLx, toLz);

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
    private FlowSegment createFlowSegment(Flow flow, SIDE fromSide, SIDE toSide,
                                          Integer fromLx, Integer fromLz,
                                          Integer toLx, Integer toLz) {
        FlowSegment.FlowSegmentBuilder builder = FlowSegment.builder()
            .flowType(flow.getType())
            .fromSide(fromSide)
            .toSide(toSide)
            .fromLx(fromLx)
            .fromLz(fromLz)
            .toLx(toLx)
            .toLz(toLz)
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
                // Create ROUTE parts for entry point (fromSide or fromLx/fromLz)
                if (segment.hasFromCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint)
                    RoadConfigPart part = RoadConfigPart.createRoutePositionPart(
                        segment.getFromLx(),
                        segment.getFromLz(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getType()
                    );
                    areaGrid.addRoadConfigPart(part);
                    log.debug("Added position-based route part (from) at lx={}, lz={}",
                        segment.getFromLx(), segment.getFromLz());
                } else if (segment.getFromSide() != null) {
                    // Use SIDE (Biome endpoint)
                    RoadConfigPart part = RoadConfigPart.createRouteSidePart(
                        segment.getFromSide(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getType()
                    );
                    areaGrid.addRoadConfigPart(part);
                }

                // Create ROUTE parts for exit point (toSide or toLx/toLz)
                if (segment.hasToCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint)
                    RoadConfigPart part = RoadConfigPart.createRoutePositionPart(
                        segment.getToLx(),
                        segment.getToLz(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getType()
                    );
                    areaGrid.addRoadConfigPart(part);
                    log.debug("Added position-based route part (to) at lx={}, lz={}",
                        segment.getToLx(), segment.getToLz());
                } else if (segment.getToSide() != null && !segment.getToSide().equals(segment.getFromSide())) {
                    // Use SIDE (Biome endpoint)
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

                // Create FROM parts (from lx/lz or from SIDE)
                if (segment.hasFromCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint)
                    RiverConfigPart part = RiverConfigPart.createFromPositionPart(
                        segment.getFromLx(),
                        segment.getFromLz(),
                        segment.getWidth(),
                        segment.getDepth(),
                        segment.getLevel(),
                        groupId
                    );
                    areaGrid.addRiverConfigPart(part);
                    log.debug("Added position-based river FROM part at lx={}, lz={}",
                        segment.getFromLx(), segment.getFromLz());
                } else if (segment.getFromSide() != null) {
                    // Use SIDE (Biome endpoint)
                    RiverConfigPart part = RiverConfigPart.createFromPart(
                        segment.getFromSide(),
                        segment.getWidth(),
                        segment.getDepth(),
                        segment.getLevel(),
                        groupId
                    );
                    areaGrid.addRiverConfigPart(part);
                }

                // Create TO parts (to lx/lz or to SIDE)
                if (segment.hasToCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint)
                    RiverConfigPart part = RiverConfigPart.createToPositionPart(
                        segment.getToLx(),
                        segment.getToLz(),
                        segment.getWidth(),
                        segment.getDepth(),
                        segment.getLevel(),
                        groupId
                    );
                    areaGrid.addRiverConfigPart(part);
                    log.debug("Added position-based river TO part at lx={}, lz={}",
                        segment.getToLx(), segment.getToLz());
                } else if (segment.getToSide() != null) {
                    // Use SIDE (Biome endpoint)
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
                // Create parts for entry point (fromSide or fromLx/fromLz)
                if (segment.hasFromCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint)
                    WallConfigPart part = WallConfigPart.createPositionPart(
                        segment.getFromLx(),
                        segment.getFromLz(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    areaGrid.addWallConfigPart(part);
                    log.debug("Added position-based wall part (from) at lx={}, lz={}",
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
                    areaGrid.addWallConfigPart(part);
                }

                // Create parts for exit point (toSide or toLx/toLz)
                if (segment.hasToCoordinates()) {
                    // Use lx/lz coordinates (Point endpoint)
                    WallConfigPart part = WallConfigPart.createPositionPart(
                        segment.getToLx(),
                        segment.getToLz(),
                        segment.getHeight(),
                        segment.getWidth(),
                        segment.getLevel(),
                        segment.getMaterial()
                    );
                    areaGrid.addWallConfigPart(part);
                    log.debug("Added position-based wall part (to) at lx={}, lz={}",
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
        log.info("Composing SideWall '{}' for target '{}'", sideWall.getName(), sideWall.getTargetBiomeId());

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

        if (targetBiome.getHexGrids() == null || targetBiome.getHexGrids().isEmpty()) {
            log.warn("Target biome '{}' has no HexGrids", targetBiome.getName());
            return 0;
        }

        // Find edge grids of the target biome
        List<FeatureHexGrid> edgeGrids = findBiomeEdgeGrids(targetBiome, placementResult);
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
                List<SIDE> exposedSides = getExposedSides(edgeGrid, targetBiome, placementResult);
                boolean hasRequestedSide = false;
                for (SIDE side : sideWall.getSides()) {
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

        log.info("Configured {} edge grids with sidewall for '{}'", configuredCount, sideWall.getName());

        // Update feature status
        if (configuredCount > 0) {
            sideWall.setStatus(FeatureStatus.COMPOSED);
        }

        return configuredCount;
    }

    /**
     * Finds edge grids of a biome (grids that have at least one side not connected to another biome grid).
     */
    private List<FeatureHexGrid> findBiomeEdgeGrids(Biome biome, BiomePlacementResult placementResult) {
        List<FeatureHexGrid> edgeGrids = new ArrayList<>();

        // Build set of all biome coordinates for quick lookup
        Set<String> biomeCoords = new HashSet<>();
        for (FeatureHexGrid grid : biome.getHexGrids()) {
            biomeCoords.add(coordKey(grid.getCoordinate()));
        }

        // Find edge grids (grids with at least one neighbor not in biome)
        for (FeatureHexGrid grid : biome.getHexGrids()) {
            List<HexVector2> neighbors = getHexNeighbors(grid.getCoordinate());
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
    private List<SIDE> getExposedSides(FeatureHexGrid grid, Biome biome,
                                        BiomePlacementResult placementResult) {
        List<SIDE> exposedSides = new ArrayList<>();

        // Build set of biome coordinates
        Set<String> biomeCoords = new HashSet<>();
        for (FeatureHexGrid g : biome.getHexGrids()) {
            biomeCoords.add(coordKey(g.getCoordinate()));
        }

        // Check each direction
        SIDE[] sides = {
            SIDE.NORTH_EAST,
            SIDE.EAST,
            SIDE.SOUTH_EAST,
            SIDE.SOUTH_WEST,
            SIDE.WEST,
            SIDE.NORTH_WEST
        };

        int[][] directions = {
            {1, -1},  // NE
            {1, 0},   // E
            {0, 1},   // SE
            {-1, 1},  // SW
            {-1, 0},  // W
            {0, -1}   // NW
        };

        for (int i = 0; i < sides.length; i++) {
            HexVector2 neighbor = HexVector2.builder()
                .q(grid.getCoordinate().getQ() + directions[i][0])
                .r(grid.getCoordinate().getR() + directions[i][1])
                .build();

            // Side is exposed if neighbor is not in biome
            if (!biomeCoords.contains(coordKey(neighbor))) {
                exposedSides.add(sides[i]);
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
                for (SIDE side : sideWall.getSides()) {
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
