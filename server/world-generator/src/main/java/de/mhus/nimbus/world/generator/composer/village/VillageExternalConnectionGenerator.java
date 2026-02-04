package de.mhus.nimbus.world.generator.composer.village;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.point.Direction;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.point.Point;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates synthetic external connection points for villages.
 * These points are placed in neighboring grids outside the village structure
 * to provide entry/exit points for external roads.
 *
 * The number of connection points scales with village size:
 * - 1 district: 2 external points
 * - 2-3 districts: 4 external points
 * - 4+ districts: 6 external points
 */
@Slf4j
public class VillageExternalConnectionGenerator {

    /**
     * Generates external connection points for all villages in the composition.
     *
     * @param composition The hex composition containing villages
     * @param placementResult Biome placement result with grid-to-biome mapping
     * @return Generation result with created connection points
     */
    public GenerationResult generateExternalConnections(HexComposition composition,
                                                        BiomePlacementResult placementResult) {
        log.info("Starting external connection point generation for villages");

        GenerationResult result = new GenerationResult();

        // Find all villages in the composition
        List<Village> villages = new ArrayList<>();
        for (Feature feature : composition.getFeatures()) {
            if (feature instanceof Village) {
                villages.add((Village) feature);
            }
        }

        if (villages.isEmpty()) {
            log.info("No villages found, skipping external connection generation");
            return result;
        }

        log.info("Found {} villages to process", villages.size());

        // Generate connection points for each village
        for (Village village : villages) {
            List<VillageConnectionPoint> villagePoints = generateForVillage(village, placementResult);
            result.addPoints(villagePoints);

            // Store connection points in village
            if (village.getExternalConnectionPoints() == null) {
                village.setExternalConnectionPoints(new ArrayList<>());
            }
            village.getExternalConnectionPoints().addAll(villagePoints);

            // Add to composition features so they can be found by other composers
            // (marked as precomposed=true, so PointComposer will skip them)
            composition.getFeatures().addAll(villagePoints);

            log.info("Generated {} external connection points for village '{}'",
                    villagePoints.size(), village.getName());
        }

        log.info("External connection generation complete: {} points created", result.getTotalPoints());
        return result;
    }

    /**
     * Generates external connection points for a single village.
     *
     * @param village The village to generate connection points for
     * @param placementResult Biome placement result for finding biomeId
     * @return List of generated connection points
     */
    private List<VillageConnectionPoint> generateForVillage(Village village,
                                                            BiomePlacementResult placementResult) {
        List<VillageConnectionPoint> points = new ArrayList<>();

        // Get village districts to determine size
        int districtCount = village.getDistricts() != null ? village.getDistricts().size() : 0;
        if (districtCount == 0) {
            log.warn("Village '{}' has no districts, skipping external connection generation", village.getName());
            return points;
        }

        // Determine number of external points based on district count
        int externalPointCount = determineExternalPointCount(districtCount);
        log.debug("Village '{}' with {} districts will get {} external connection points",
                village.getName(), districtCount, externalPointCount);

        // Get village center from HexGrids (absolute coordinates in world)
        // The village's HexGrids have already been positioned in absolute world coordinates
        HexVector2 villageCenter = getVillageCenter(village);
        log.debug("Village '{}' center: [{},{}]", village.getName(), villageCenter.getQ(), villageCenter.getR());

        // Determine directions evenly distributed around village
        Direction[] directions = distributeDirections(externalPointCount);

        // Get internal connection points for linking
        Map<Direction, String> internalConnectionPoints = findInternalConnectionPoints(village);

        // Generate connection point for each direction
        for (int i = 0; i < externalPointCount; i++) {
            Direction direction = directions[i];

            // Find neighbor grid in this direction
            HexVector2 neighborGrid = findNeighborGridPosition(villageCenter, direction);

            // Check if neighbor grid is suitable (not occupied by village itself)
            if (!isGridSuitable(neighborGrid, village)) {
                log.warn("Neighbor grid [{},{}] in direction {} is not suitable for external connection point, skipping",
                        neighborGrid.getQ(), neighborGrid.getR(), direction);
                continue;
            }

            // Find closest internal connection point for this direction
            String internalPointName = internalConnectionPoints.getOrDefault(direction, null);

            // Find which biome this neighbor grid belongs to
            String biomeId = findBiomeForGrid(neighborGrid, placementResult);
            if (biomeId == null) {
                log.warn("Could not find biome for neighbor grid [{},{}], connection point may not work properly",
                        neighborGrid.getQ(), neighborGrid.getR());
            }

            // Create external connection point
            VillageConnectionPoint point = new VillageConnectionPoint();
            point.setName(village.getName() + "-" + direction.name().toLowerCase());
            point.setTitle("Connection " + direction.name());
            point.setFeatureId(village.getName() + "-" + direction.name().toLowerCase());
            point.setVillageId(village.getName());
            point.setInternalConnectionPointName(internalPointName);
            point.setExternalDirection(direction);
            point.setNeighborGridCoordinate(neighborGrid);
            point.setPlacedInNeighborGrid(true);
            point.setGridDistance(1);
            point.setBiomeId(biomeId); // Set biomeId for reference
            point.setPrecomposed(true); // Mark as pre-positioned, skip PointComposer processing

            // Set pointComposed data so the point can be rendered
            // Place point at the edge of neighbor grid that faces back toward the village
            Point.PointComposed composed = new Point.PointComposed();
            composed.setGridCoordinate(neighborGrid);
            composed.setBiome(biomeId);

            // Determine which edge of the neighbor grid faces the village
            // Use opposite direction to place point on the edge facing back toward village
            de.mhus.nimbus.world.shared.world.WHexGrid.EDGE edge = getOppositeEdge(direction);

            composed.setHexLocalEdgeVector(
                    new de.mhus.nimbus.world.shared.world.HexLocalEdgeVector(
                            edge,
                            2, // numerator (center of edge)
                            de.mhus.nimbus.world.shared.util.HexLocalUtil.DEFAULT_EDGE_DIVIDER // denominator
                    )
            );

            // IMPORTANT: Also set legacy fields for FlowComposer/TerrainPathFinder compatibility
            // FlowComposer uses placedCoordinate to set flow start/end points
            // TerrainPathFinder needs placedCoordinate/placedLx/placedLz to find paths
            composed.setPlacedCoordinate(neighborGrid);
            composed.setPlacedLx(0);  // Center of grid (will be overridden by HexLocalEdgeVector for rendering)
            composed.setPlacedLz(0);
            composed.setPlacedInBiome(biomeId);

            point.setPointComposed(composed);

            points.add(point);

            log.debug("Created external connection point '{}' at grid [{},{}] direction {} (internal: {})",
                    point.getName(), neighborGrid.getQ(), neighborGrid.getR(), direction, internalPointName);
        }

        return points;
    }

    /**
     * Determines the number of external connection points based on district count.
     *
     * @param districtCount Number of districts in the village
     * @return Number of external points to generate
     */
    private int determineExternalPointCount(int districtCount) {
        if (districtCount == 1) {
            return 2; // Small village: 2 points (N, S or E, W)
        } else if (districtCount <= 3) {
            return 4; // Medium village: 4 points (N, E, S, W)
        } else {
            return 6; // Large village: 6 points (N, NE, SE, S, SW, NW)
        }
    }

    /**
     * Distributes directions evenly around the village perimeter.
     *
     * @param count Number of directions to distribute
     * @return Array of evenly distributed directions
     */
    private Direction[] distributeDirections(int count) {
        // All 8 possible directions in clockwise order
        Direction[] all = {Direction.N, Direction.NE, Direction.E, Direction.SE,
                Direction.S, Direction.SW, Direction.W, Direction.NW};

        if (count == 2) {
            // Opposite sides: N, S
            return new Direction[]{Direction.N, Direction.S};
        } else if (count == 4) {
            // Cardinal directions: N, E, S, W
            return new Direction[]{Direction.N, Direction.E, Direction.S, Direction.W};
        } else if (count == 6) {
            // Skip two directions (E, W) for better distribution
            return new Direction[]{Direction.N, Direction.NE, Direction.SE, Direction.S, Direction.SW, Direction.NW};
        }

        // Fallback: return first 'count' directions
        Direction[] result = new Direction[count];
        System.arraycopy(all, 0, result, 0, Math.min(count, all.length));
        return result;
    }

    /**
     * Gets the center coordinate of the village from its HexGrids.
     * Uses the coordinate of the "center" district grid, or first grid as fallback.
     *
     * @param village The village
     * @return Village center coordinate (absolute world position)
     */
    private HexVector2 getVillageCenter(Village village) {
        if (village.getHexGrids() == null || village.getHexGrids().isEmpty()) {
            log.warn("No HexGrids available for village '{}', cannot determine position", village.getName());
            return HexVector2.builder().q(0).r(0).build();
        }

        // Look for grid with "center" in the name (e.g., "small-village - center")
        for (FeatureHexGrid grid : village.getHexGrids()) {
            if (grid.getName() != null && grid.getName().toLowerCase().contains("center")) {
                return grid.getCoordinate();
            }
        }

        // Fallback: use first grid coordinate
        HexVector2 firstCoord = village.getHexGrids().get(0).getCoordinate();
        log.debug("No 'center' grid found, using first grid position [{},{}]",
                firstCoord.getQ(), firstCoord.getR());
        return firstCoord;
    }

    /**
     * Finds neighbor grid position in given direction from center.
     *
     * @param center Village center coordinate
     * @param direction Direction to find neighbor
     * @return Neighbor grid coordinate
     */
    private HexVector2 findNeighborGridPosition(HexVector2 center, Direction direction) {
        int[] offset = getAxialOffset(direction);
        return HexVector2.builder()
                .q(center.getQ() + offset[0])
                .r(center.getR() + offset[1])
                .build();
    }

    /**
     * Gets the opposite edge for a given direction.
     * This determines which edge of the neighbor grid faces back toward the village.
     * WHexGrid.EDGE has 6 values for flat-top hexagons: NE, E, SE, SW, W, NW
     *
     * @param direction Direction from village to neighbor grid
     * @return Opposite edge that faces back toward village
     */
    private de.mhus.nimbus.world.shared.world.WHexGrid.EDGE getOppositeEdge(Direction direction) {
        // Map direction to opposite edge (the edge of neighbor grid facing the village)
        // Note: District grids use pointy-top, but WHexGrid.EDGE is defined for flat-top
        // We use the closest available edge
        switch (direction) {
            case N:  // North neighbor: use SW edge (closest to south)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_WEST;
            case NE: // Northeast neighbor: use SW edge (opposite)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_WEST;
            case E:  // East neighbor: use W edge (opposite)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.WEST;
            case SE: // Southeast neighbor: use NW edge (opposite)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_WEST;
            case S:  // South neighbor: use NE edge (closest to north)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_EAST;
            case SW: // Southwest neighbor: use NE edge (opposite)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_EAST;
            case W:  // West neighbor: use E edge (opposite)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.EAST;
            case NW: // Northwest neighbor: use SE edge (opposite)
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.SOUTH_EAST;
            default:
                return de.mhus.nimbus.world.shared.world.WHexGrid.EDGE.NORTH_EAST;
        }
    }

    /**
     * Converts a direction to axial hex offset (dq, dr).
     * Uses pointy-top hexagon coordinates for district grids.
     *
     * @param direction The direction
     * @return Array [dq, dr]
     */
    private int[] getAxialOffset(Direction direction) {
        // Pointy-top hexagon offsets (district grids use pointy-top orientation)
        switch (direction) {
            case N:  return new int[]{0, -1};
            case NE: return new int[]{1, -1};
            case E:  return new int[]{1, 0};
            case SE: return new int[]{1, 1};
            case S:  return new int[]{0, 1};
            case SW: return new int[]{-1, 1};
            case W:  return new int[]{-1, 0};
            case NW: return new int[]{-1, -1};
            default: return new int[]{0, 0};
        }
    }

    /**
     * Checks if a grid position is suitable for placing an external connection point.
     * A grid is suitable if it's NOT occupied by the village itself.
     *
     * @param gridCoord Grid coordinate to check (absolute)
     * @param village The village
     * @return True if suitable, false otherwise
     */
    private boolean isGridSuitable(HexVector2 gridCoord, Village village) {
        // Check if this grid is occupied by any of the village's district grids
        if (village.getHexGrids() == null || village.getHexGrids().isEmpty()) {
            return true;
        }

        // Check if gridCoord matches any village grid coordinate (absolute positions)
        for (FeatureHexGrid grid : village.getHexGrids()) {
            HexVector2 gridPos = grid.getCoordinate();
            if (gridPos.getQ() == gridCoord.getQ() && gridPos.getR() == gridCoord.getR()) {
                log.debug("Grid [{},{}] is occupied by village district '{}', not suitable",
                        gridCoord.getQ(), gridCoord.getR(), grid.getName());
                return false;
            }
        }

        return true;
    }

    /**
     * Finds internal connection points within the village and maps them to external directions.
     * This allows linking external connection points to specific internal connection points.
     *
     * @param village The village
     * @return Map of direction to internal connection point name
     */
    private Map<Direction, String> findInternalConnectionPoints(Village village) {
        Map<Direction, String> result = new HashMap<>();
        String fallbackConnectionPoint = null;

        if (village.getDistricts() == null) {
            return result;
        }

        // First pass: Map connection points to district directions
        for (District district : village.getDistricts()) {
            if (district.getPlaces() == null) {
                continue;
            }

            for (Place place : district.getPlaces()) {
                if (place.isConnectionPoint()) {
                    // Remember first connection point as fallback
                    if (fallbackConnectionPoint == null) {
                        fallbackConnectionPoint = place.getName();
                    }

                    // Map to district direction if available
                    if (district.getDirection() != null) {
                        result.putIfAbsent(district.getDirection(), place.getName());
                    }
                }
            }
        }

        // Second pass: Fill missing directions with fallback or nearest direction
        // If we have connection points but some directions are missing, use heuristics
        if (fallbackConnectionPoint != null && !result.isEmpty()) {
            // For directions without a direct match, use the fallback (usually center connection point)
            for (Direction dir : Direction.values()) {
                if (!result.containsKey(dir)) {
                    // Try to find a connection point in a nearby direction
                    String nearbyPoint = findNearbyConnectionPoint(dir, result);
                    result.put(dir, nearbyPoint != null ? nearbyPoint : fallbackConnectionPoint);
                }
            }
        }

        return result;
    }

    /**
     * Finds a connection point in a nearby direction.
     *
     * @param direction The target direction
     * @param existingPoints Existing direction-to-point mappings
     * @return Connection point name from nearby direction, or null
     */
    private String findNearbyConnectionPoint(Direction direction, Map<Direction, String> existingPoints) {
        // Map each direction to its neighbors (clockwise order)
        return switch (direction) {
            case N -> existingPoints.getOrDefault(Direction.NE, existingPoints.get(Direction.NW));
            case NE -> existingPoints.getOrDefault(Direction.N, existingPoints.get(Direction.E));
            case E -> existingPoints.getOrDefault(Direction.NE, existingPoints.get(Direction.SE));
            case SE -> existingPoints.getOrDefault(Direction.E, existingPoints.get(Direction.S));
            case S -> existingPoints.getOrDefault(Direction.SE, existingPoints.get(Direction.SW));
            case SW -> existingPoints.getOrDefault(Direction.S, existingPoints.get(Direction.W));
            case W -> existingPoints.getOrDefault(Direction.SW, existingPoints.get(Direction.NW));
            case NW -> existingPoints.getOrDefault(Direction.W, existingPoints.get(Direction.N));
        };
    }

    /**
     * Finds which biome a specific grid coordinate belongs to.
     *
     * @param gridCoord Grid coordinate to check
     * @param placementResult Biome placement result
     * @return BiomeId if found, null otherwise
     */
    private String findBiomeForGrid(HexVector2 gridCoord, BiomePlacementResult placementResult) {
        if (placementResult == null || placementResult.getPlacedBiomes() == null) {
            return null;
        }

        // Search through all placed biomes to find which one contains this grid
        for (PlacedBiome placedBiome : placementResult.getPlacedBiomes()) {
            if (placedBiome.getCoordinates() != null) {
                for (HexVector2 coord : placedBiome.getCoordinates()) {
                    if (coord.getQ() == gridCoord.getQ() && coord.getR() == gridCoord.getR()) {
                        return placedBiome.getBiome().getName();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Result of external connection generation.
     */
    public static class GenerationResult {
        private final List<VillageConnectionPoint> points = new ArrayList<>();

        public void addPoints(List<VillageConnectionPoint> newPoints) {
            points.addAll(newPoints);
        }

        public List<VillageConnectionPoint> getPoints() {
            return points;
        }

        public int getTotalPoints() {
            return points.size();
        }
    }
}
