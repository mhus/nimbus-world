package de.mhus.nimbus.world.generator.composer.pathfinding;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * A* pathfinding algorithm for routing streets through hex grid villages.
 * Finds optimal paths between connection points while respecting obstacles and district boundaries.
 * Uses HexCoord for navigation with cartesian X,Z for distance calculations.
 */
@Slf4j
public class VillageHexPathfinder {

    private static final int BASE_MOVE_COST = 10;
    private static final int DISTRICT_CROSSING_PENALTY = 20;

    /**
     * Find path from start to goal using A* algorithm.
     *
     * @param start Starting hex coordinate
     * @param goal Goal hex coordinate
     * @return Path from start to goal, or null if no path found
     */
    public HexPath findPath(HexCoord start, HexCoord goal) {
        if (start == null || goal == null) {
            log.warn("Cannot find path: start or goal is null");
            return null;
        }

        if (!start.isWalkable() || !goal.isWalkable()) {
            log.warn("Cannot find path: start or goal is not walkable");
            return null;
        }

        // Priority queue for open set (sorted by f-score)
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingInt(PathNode::getF));

        // Track visited coordinates
        Set<String> closedSet = new HashSet<>();

        // Track best g-scores
        Map<String, Integer> gScores = new HashMap<>();

        // Start coordinate
        PathNode startNode = new PathNode(start, 0, heuristic(start, goal), null);
        openSet.add(startNode);
        gScores.put(start.getKey(), 0);

        int iterations = 0;
        int maxIterations = 10000; // Safety limit

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;

            // Get coordinate with lowest f-score
            PathNode current = openSet.poll();

            // Check if we reached the goal
            if (current.getCoord().equals(goal)) {
                log.debug("Path found after {} iterations", iterations);
                return reconstructPath(current);
            }

            // Mark as visited
            closedSet.add(current.getCoord().getKey());

            // Explore neighbors
            for (HexCoord neighbor : current.getCoord().getNeighbors()) {
                // Skip if not walkable or already visited
                if (!neighbor.isWalkable() || closedSet.contains(neighbor.getKey())) {
                    continue;
                }

                // Calculate tentative g-score
                int moveCost = calculateCost(current.getCoord(), neighbor);
                int tentativeG = current.getG() + moveCost;

                // Check if this path is better
                Integer existingG = gScores.get(neighbor.getKey());
                if (existingG == null || tentativeG < existingG) {
                    // Better path found
                    gScores.put(neighbor.getKey(), tentativeG);

                    int h = heuristic(neighbor, goal);
                    PathNode neighborNode = new PathNode(neighbor, tentativeG, h, current);

                    // Add to open set (or update if already there)
                    openSet.removeIf(n -> n.getCoord().getKey().equals(neighbor.getKey()));
                    openSet.add(neighborNode);
                }
            }
        }

        if (iterations >= maxIterations) {
            log.warn("Pathfinding exceeded max iterations ({})", maxIterations);
        } else {
            log.debug("No path found from {} to {}", start, goal);
        }

        return null;
    }

    /**
     * Calculate movement cost from one coordinate to another.
     * Applies penalties for district crossing.
     *
     * @param from Source coordinate
     * @param to Target coordinate
     * @return Movement cost
     */
    private int calculateCost(HexCoord from, HexCoord to) {
        int cost = BASE_MOVE_COST;

        // Penalty for crossing district boundary
        if (!from.getDistrictName().equals(to.getDistrictName())) {
            cost += DISTRICT_CROSSING_PENALTY;
        }

        return cost;
    }

    /**
     * Heuristic function for A* (cartesian distance).
     * Uses X,Z coordinates for unified distance calculation across districts.
     *
     * @param from Current coordinate
     * @param goal Goal coordinate
     * @return Estimated cost to goal
     */
    private int heuristic(HexCoord from, HexCoord goal) {
        // Use cartesian distance for cross-district heuristic
        return (int) (BASE_MOVE_COST * from.cartesianDistance(goal) / 10);
    }

    /**
     * Reconstruct path from goal coordinate by following parent pointers.
     *
     * @param goalNode The goal PathNode
     * @return Complete path
     */
    private HexPath reconstructPath(PathNode goalNode) {
        List<HexCoord> coords = new ArrayList<>();
        PathNode current = goalNode;

        while (current != null) {
            coords.add(0, current.getCoord()); // Prepend to reverse order
            current = current.getParent();
        }

        return new HexPath(coords, goalNode.getG());
    }

    /**
     * Internal class for A* pathfinding nodes.
     */
    @Data
    private static class PathNode {
        private final HexCoord coord;
        private final int g; // Cost from start
        private final int h; // Heuristic to goal
        private final PathNode parent;

        public int getF() {
            return g + h; // Total estimated cost
        }
    }
}
