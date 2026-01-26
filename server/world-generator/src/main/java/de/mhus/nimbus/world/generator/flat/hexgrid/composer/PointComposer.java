package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Composes Point features by calculating their precise locations within biomes.
 * Points are placed according to their position and snap configurations.
 */
@Slf4j
public class PointComposer {

    private static final int FLAT_SIZE = 512; // Default hex grid size

    @Data
    @Builder
    public static class PointCompositionResult {
        private int totalPoints;
        private int composedPoints;
        private int failedPoints;
        private boolean success;
        private String errorMessage;
        private List<String> errors;
    }

    /**
     * Composes all points in the composition.
     *
     * @param prepared The prepared composition with points to place
     * @param placementResult Result from biome placement (needed to know where biomes are)
     * @return Result with statistics
     */
    public PointCompositionResult composePoints(HexComposition prepared,
                                                BiomePlacementResult placementResult) {
        log.info("Starting point composition");

        List<String> errors = new ArrayList<>();
        int totalPoints = 0;
        int composedPoints = 0;
        int failedPoints = 0;

        try {
            // Collect all points
            List<Point> points = collectPoints(prepared);
            totalPoints = points.size();

            log.info("Found {} points to compose", totalPoints);

            // Build biome coordinate map for lookups
            Map<String, PlacedBiome> biomeMap = buildBiomeMap(placementResult);
            Map<String, HexVector2> biomeCoordinateMap = buildBiomeCoordinateMap(biomeMap);

            // Compose each point
            for (Point point : points) {
                try {
                    boolean success = composePoint(point, biomeMap, biomeCoordinateMap);
                    if (success) {
                        composedPoints++;
                        log.info("Composed point '{}': {}", point.getName(), point.getPlacedPositionString());
                    } else {
                        failedPoints++;
                        errors.add("Point " + point.getName() + ": could not find valid placement");
                        log.warn("Failed to compose point: {}", point.getName());
                    }
                } catch (Exception e) {
                    failedPoints++;
                    errors.add("Point " + point.getName() + ": " + e.getMessage());
                    log.error("Error composing point: {}", point.getName(), e);
                }
            }

            log.info("Point composition complete: composed={}/{}, failed={}",
                composedPoints, totalPoints, failedPoints);

            return PointCompositionResult.builder()
                .totalPoints(totalPoints)
                .composedPoints(composedPoints)
                .failedPoints(failedPoints)
                .success(failedPoints == 0)
                .errors(errors)
                .build();

        } catch (Exception e) {
            log.error("Point composition failed", e);
            return PointCompositionResult.builder()
                .totalPoints(totalPoints)
                .composedPoints(composedPoints)
                .failedPoints(failedPoints)
                .success(false)
                .errorMessage(e.getMessage())
                .errors(errors)
                .build();
        }
    }

    /**
     * Collects all Point features from the composition.
     */
    private List<Point> collectPoints(HexComposition composition) {
        List<Point> points = new ArrayList<>();

        if (composition.getFeatures() != null) {
            for (Feature feature : composition.getFeatures()) {
                if (feature instanceof Point point) {
                    points.add(point);
                }
            }
        }

        // TODO: Also collect points from composites

        return points;
    }

    /**
     * Builds a map of biome names to PlacedBiome objects.
     */
    private Map<String, PlacedBiome> buildBiomeMap(BiomePlacementResult placementResult) {
        Map<String, PlacedBiome> map = new HashMap<>();

        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            if (placed.getBiome() != null && placed.getBiome().getName() != null) {
                map.put(placed.getBiome().getName(), placed);
            }
        }

        return map;
    }

    /**
     * Builds a map of biome names to their center coordinates.
     */
    private Map<String, HexVector2> buildBiomeCoordinateMap(Map<String, PlacedBiome> biomeMap) {
        Map<String, HexVector2> map = new HashMap<>();

        for (Map.Entry<String, PlacedBiome> entry : biomeMap.entrySet()) {
            PlacedBiome placed = entry.getValue();
            if (placed.getCenter() != null) {
                map.put(entry.getKey(), placed.getCenter());
            }
        }

        return map;
    }

    /**
     * Composes a single point by calculating its exact position.
     *
     * @return true if successfully placed, false otherwise
     */
    private boolean composePoint(Point point,
                                 Map<String, PlacedBiome> biomeMap,
                                 Map<String, HexVector2> biomeCoordinateMap) {
        // Prepare point (convert positions)
        point.prepareForComposition();

        // Determine target biome from snap config or positions
        String targetBiomeName = determineTargetBiome(point, biomeCoordinateMap);
        if (targetBiomeName == null) {
            log.warn("Point '{}' has no target biome", point.getName());
            return false;
        }

        PlacedBiome targetBiome = biomeMap.get(targetBiomeName);
        if (targetBiome == null) {
            log.warn("Point '{}' target biome '{}' not found", point.getName(), targetBiomeName);
            return false;
        }

        // Find valid placement coordinate
        HexVector2 placementCoord = findPlacementCoordinate(point, targetBiome, biomeMap);
        if (placementCoord == null) {
            return false;
        }

        // Calculate local position within the hex grid
        // For now, place at center of hex (256, 256 for 512x512 grid)
        // TODO: More sophisticated placement based on snap config
        int localX = FLAT_SIZE / 2;
        int localZ = FLAT_SIZE / 2;

        // Store calculated position
        point.setPlacedCoordinate(placementCoord);
        point.setPlacedLx(localX);
        point.setPlacedLz(localZ);
        point.setPlacedInBiome(targetBiomeName);
        point.setStatus(FeatureStatus.COMPOSED);

        return true;
    }

    /**
     * Determines the target biome for point placement.
     */
    private String determineTargetBiome(Point point, Map<String, HexVector2> biomeCoordinateMap) {
        // Priority 1: Snap config target
        if (point.getSnap() != null && point.getSnap().getTarget() != null) {
            return point.getSnap().getTarget();
        }

        // Priority 2: First prepared position anchor (if it's a biome)
        if (point.getPreparedPositions() != null && !point.getPreparedPositions().isEmpty()) {
            String anchor = point.getPreparedPositions().get(0).getAnchor();
            if (anchor != null && !anchor.equals("origin") && biomeCoordinateMap.containsKey(anchor)) {
                return anchor;
            }
        }

        // Priority 3: First position anchor
        if (point.getPositions() != null && !point.getPositions().isEmpty()) {
            String anchor = point.getPositions().get(0).getAnchor();
            if (anchor != null && !anchor.equals("origin") && biomeCoordinateMap.containsKey(anchor)) {
                return anchor;
            }
        }

        return null;
    }

    /**
     * Finds a valid hex coordinate for point placement within the target biome.
     */
    private HexVector2 findPlacementCoordinate(Point point,
                                               PlacedBiome targetBiome,
                                               Map<String, PlacedBiome> biomeMap) {
        List<HexVector2> validCoordinates = new ArrayList<>(targetBiome.getCoordinates());

        if (validCoordinates.isEmpty()) {
            return null;
        }

        // Apply snap mode filtering
        if (point.getSnap() != null) {
            validCoordinates = applySnapMode(point.getSnap(), validCoordinates, targetBiome);
            if (validCoordinates.isEmpty()) {
                return null;
            }

            // Apply avoid filters
            if (point.getSnap().getAvoid() != null && !point.getSnap().getAvoid().isEmpty()) {
                validCoordinates = applyAvoidFilter(validCoordinates, point.getSnap().getAvoid(), biomeMap);
                if (validCoordinates.isEmpty()) {
                    return null;
                }
            }

            // Apply preferNear scoring (pick best coordinate)
            if (point.getSnap().getPreferNear() != null && !point.getSnap().getPreferNear().isEmpty()) {
                return selectPreferredCoordinate(validCoordinates, point.getSnap().getPreferNear(), biomeMap);
            }
        }

        // Return center coordinate if possible, otherwise first valid coordinate
        HexVector2 center = targetBiome.getCenter();
        if (center != null && validCoordinates.contains(center)) {
            return center;
        }

        return validCoordinates.get(0);
    }

    /**
     * Applies snap mode to filter valid coordinates.
     */
    private List<HexVector2> applySnapMode(SnapConfig snap,
                                           List<HexVector2> coordinates,
                                           PlacedBiome targetBiome) {
        if (snap.getMode() == null || snap.getMode() == SnapMode.INSIDE) {
            // INSIDE: All coordinates are valid
            return new ArrayList<>(coordinates);
        }

        if (snap.getMode() == SnapMode.EDGE) {
            // EDGE: Only coordinates at the boundary
            return filterEdgeCoordinates(coordinates, targetBiome);
        }

        // OUTSIDE_NEAR: Not implemented yet (would need adjacent coordinates)
        log.warn("SnapMode.OUTSIDE_NEAR not yet implemented, using INSIDE");
        return new ArrayList<>(coordinates);
    }

    /**
     * Filters coordinates to only include edge/boundary hexagons.
     */
    private List<HexVector2> filterEdgeCoordinates(List<HexVector2> coordinates,
                                                   PlacedBiome targetBiome) {
        Set<String> coordSet = new HashSet<>();
        for (HexVector2 coord : coordinates) {
            coordSet.add(TypeUtil.toStringHexCoord(coord));
        }

        List<HexVector2> edgeCoords = new ArrayList<>();

        for (HexVector2 coord : coordinates) {
            // Check if at least one neighbor is NOT in the biome
            boolean isEdge = false;
            for (HexVector2 neighbor : getHexNeighbors(coord)) {
                if (!coordSet.contains(TypeUtil.toStringHexCoord(neighbor))) {
                    isEdge = true;
                    break;
                }
            }

            if (isEdge) {
                edgeCoords.add(coord);
            }
        }

        return edgeCoords;
    }

    /**
     * Applies avoid filter to remove coordinates too close to avoided features.
     */
    private List<HexVector2> applyAvoidFilter(List<HexVector2> coordinates,
                                              List<String> avoidNames,
                                              Map<String, PlacedBiome> biomeMap) {
        // Collect all coordinates to avoid
        Set<String> avoidCoords = new HashSet<>();

        for (String avoidName : avoidNames) {
            PlacedBiome avoidBiome = biomeMap.get(avoidName);
            if (avoidBiome != null) {
                for (HexVector2 coord : avoidBiome.getCoordinates()) {
                    avoidCoords.add(TypeUtil.toStringHexCoord(coord));
                    // Also add neighbors (minDistance = 1)
                    for (HexVector2 neighbor : getHexNeighbors(coord)) {
                        avoidCoords.add(TypeUtil.toStringHexCoord(neighbor));
                    }
                }
            }
        }

        // Filter out avoided coordinates
        List<HexVector2> filtered = new ArrayList<>();
        for (HexVector2 coord : coordinates) {
            if (!avoidCoords.contains(TypeUtil.toStringHexCoord(coord))) {
                filtered.add(coord);
            }
        }

        return filtered;
    }

    /**
     * Selects the best coordinate based on preferNear features.
     */
    private HexVector2 selectPreferredCoordinate(List<HexVector2> coordinates,
                                                 List<String> preferNearNames,
                                                 Map<String, PlacedBiome> biomeMap) {
        // Collect all preferNear coordinates
        List<HexVector2> preferCoords = new ArrayList<>();

        for (String preferName : preferNearNames) {
            PlacedBiome preferBiome = biomeMap.get(preferName);
            if (preferBiome != null) {
                preferCoords.addAll(preferBiome.getCoordinates());
            }
        }

        if (preferCoords.isEmpty()) {
            return coordinates.get(0);
        }

        // Find coordinate with minimum distance to any preferNear coordinate
        HexVector2 bestCoord = null;
        int minDistance = Integer.MAX_VALUE;

        for (HexVector2 coord : coordinates) {
            int minDist = Integer.MAX_VALUE;
            for (HexVector2 preferCoord : preferCoords) {
                int dist = hexDistance(coord, preferCoord);
                minDist = Math.min(minDist, dist);
            }

            if (minDist < minDistance) {
                minDistance = minDist;
                bestCoord = coord;
            }
        }

        return bestCoord != null ? bestCoord : coordinates.get(0);
    }

    /**
     * Gets all 6 hex neighbors of a coordinate.
     */
    private List<HexVector2> getHexNeighbors(HexVector2 coord) {
        int[][] directions = {{1,-1}, {1,0}, {0,1}, {-1,1}, {-1,0}, {0,-1}};
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
     * Calculates hex distance between two coordinates.
     */
    private int hexDistance(HexVector2 a, HexVector2 b) {
        int dq = Math.abs(a.getQ() - b.getQ());
        int dr = Math.abs(a.getR() - b.getR());
        int ds = Math.abs((a.getQ() + a.getR()) - (b.getQ() + b.getR()));
        return (dq + dr + ds) / 2;
    }

}
