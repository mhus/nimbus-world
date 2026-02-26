package de.mhus.nimbus.world.generator.composer.point;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.area.Area;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.build.ComposeContext;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureStatus;
import de.mhus.nimbus.world.generator.composer.town.StructuresIndex;
import de.mhus.nimbus.world.shared.util.HexLocalUtil;
import de.mhus.nimbus.world.shared.world.HexLocalEdgeVector;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Composes Point features by calculating their precise locations within biomes.
 * Points are positioned after biomes are fully composed.
 *
 * Algorithm:
 * 1. Collect all points from all biomes and composition
 * 2. Build constraint graph (RelativeToPoint connections)
 * 3. Initialize positions based on Point type and constraints
 * 4. Iteratively adjust positions until convergence
 * 5. Calculate absolute world positions (HexVector2 + lx, lz)
 */
@Slf4j
public class PointComposer {

    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.01;

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
     * @param world The world (needed for hexGridSize and other world properties)
     * @return Result with statistics
     */
    public PointCompositionResult composePoints(HexComposition prepared,
                                                BiomePlacementResult placementResult,
                                                de.mhus.nimbus.world.shared.world.WWorld world) {
        return composePoints(prepared, placementResult, world, null);
    }

    public PointCompositionResult composePoints(HexComposition prepared,
                                                BiomePlacementResult placementResult,
                                                de.mhus.nimbus.world.shared.world.WWorld world,
                                                StructuresIndex structuresIndex) {
        log.debug("Starting point composition");

        List<String> errors = new ArrayList<>();
        int totalPoints = 0;
        int composedPoints = 0;
        int failedPoints = 0;

        try {
            // Build compose context
            ComposeContext context = buildComposeContext(prepared, placementResult, world, structuresIndex);

            // Collect all points
            List<Point> allPoints = collectAllPoints(context);

            // Filter out precomposed points (synthetic/fixed points that don't need composition)
            List<Point> points = allPoints.stream()
                    .filter(p -> !p.isPrecomposed())
                    .toList();

            int precomposedCount = allPoints.size() - points.size();
            if (precomposedCount > 0) {
                log.debug("Skipping {} precomposed points (already positioned)", precomposedCount);
            }

            totalPoints = points.size();

            if (points.isEmpty()) {
                log.debug("No points to compose");
                return PointCompositionResult.builder()
                    .totalPoints(0)
                    .composedPoints(0)
                    .failedPoints(0)
                    .success(true)
                    .build();
            }

            log.debug("Found {} points to compose", totalPoints);

            // Build constraint graph
            Map<String, List<PointConstraint>> constraintGraph = buildConstraintGraph(points, context);

            // Initialize point positions
            for (Point point : points) {
                boolean initialized = initializePointPosition(point, context);
                if (!initialized) {
                    failedPoints++;
                    errors.add("Point " + point.getName() + ": biome '" + point.getBiomeId() + "' not found");
                }
            }

            // Iteratively solve positions (only for initialized points)
            List<Point> initializedPoints = points.stream()
                .filter(p -> p.getGridCoordinate() != null)
                .toList();
            boolean converged = iterativelySolvePositions(initializedPoints, constraintGraph, context);

            if (!converged) {
                log.warn("Point positioning did not fully converge after {} iterations", MAX_ITERATIONS);
            }

            // Calculate absolute positions and finalize (only initialized points)
            for (Point point : initializedPoints) {
                try {
                    boolean success = finalizePointPosition(point, context);
                    if (success) {
                        composedPoints++;
                        log.debug("Composed point '{}': {}", point.getName(), point.getPlacedPositionString());
                    } else {
                        failedPoints++;
                        errors.add("Point " + point.getName() + ": finalization failed unexpectedly");
                        log.warn("Failed to compose point: {}", point.getName());
                    }
                } catch (Exception e) {
                    failedPoints++;
                    errors.add("Point " + point.getName() + ": " + e.getMessage());
                    log.error("Error composing point: {}", point.getName(), e);
                }
            }

            log.debug("Point composition complete: composed={}/{}, failed={}",
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
     * Builds a ComposeContext from biome placement result.
     */
    private ComposeContext buildComposeContext(HexComposition composition,
                                              BiomePlacementResult placementResult,
                                              de.mhus.nimbus.world.shared.world.WWorld world,
                                              StructuresIndex structuresIndex) {
        // Build biome maps
        Map<String, PlacedBiome> biomeMap = new HashMap<>();
        Map<String, HexVector2> biomeCenterMap = new HashMap<>();
        Map<String, String> coordinateToBiomeMap = new HashMap<>();

        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            if (placed.getBiome() != null && placed.getBiome().getName() != null) {
                String biomeName = placed.getBiome().getName();
                biomeMap.put(biomeName, placed);

                if (placed.getCenter() != null) {
                    biomeCenterMap.put(biomeName, placed.getCenter());
                }

                // Map coordinates to biome
                for (HexVector2 coord : placed.getCoordinates()) {
                    String coordKey = TypeUtil.toStringHexCoord(coord);
                    coordinateToBiomeMap.put(coordKey, biomeName);
                }
            }
        }

        // NOTE: WHexGrids are no longer available at this stage
        // They will be created later by HexGridGenerator from Central Registry
        // For now, use empty map (PointComposer should work with Central Registry instead)
        Map<String, WHexGrid> hexGridMap = new HashMap<>();
        List<WHexGrid> hexGrids = new ArrayList<>();

        return ComposeContext.builder()
            .composition(composition)
            .world(world)
            .structuresIndex(structuresIndex != null ? structuresIndex : new StructuresIndex())
            .placedBiomes(placementResult.getPlacedBiomes())
            .biomeMap(biomeMap)
            .biomeCenterMap(biomeCenterMap)
            .coordinateToBiomeMap(coordinateToBiomeMap)
            .hexGrids(hexGrids)  // Empty - WHexGrids created later by HexGridGenerator
            .hexGridMap(hexGridMap)  // Empty
            .build();
    }

    /**
     * Collects all Point features from composition and biomes.
     */
    private List<Point> collectAllPoints(ComposeContext context) {
        List<Point> points = new ArrayList<>();
        Map<String, Point> pointMap = new HashMap<>();

        // Points directly in composition
        if (context.getComposition().getFeatures() != null) {
            for (Feature feature : context.getComposition().getFeatures()) {
                if (feature instanceof Point point) {
                    points.add(point);
                    if (point.getFeatureId() != null) {
                        pointMap.put(point.getFeatureId(), point);
                    }
                    if (point.getName() != null) {
                        pointMap.put(point.getName(), point);
                    }
                }
            }
        }

        // TODO: Points from composites, sub-features, etc.

        context.setAllPoints(points);
        context.setPointMap(pointMap);

        return points;
    }

    /**
     * Builds constraint graph for point relationships.
     */
    private Map<String, List<PointConstraint>> buildConstraintGraph(
        List<Point> points, ComposeContext context) {

        Map<String, List<PointConstraint>> graph = new HashMap<>();

        for (Point point : points) {
            String pointId = point.getFeatureId() != null ? point.getFeatureId() : point.getName();
            if (pointId == null) continue;

            List<PointConstraint> constraints = new ArrayList<>();

            // RelativeToPoint constraints
            if (point.getRelativeToPoints() != null) {
                for (RelativeToPoint relative : point.getRelativeToPoints()) {
                    Point targetPoint = context.getPointMap().get(relative.getPointId());
                    if (targetPoint != null) {
                        constraints.add(new PointConstraint(
                            ConstraintType.RELATIVE_TO_POINT,
                            targetPoint,
                            relative.getDirection(),
                            relative.getDistance()
                        ));
                    }
                }
            }

            // Biome-relative constraints
            if (point.getBiomeId() != null) {
                PlacedBiome biome = context.getBiomeMap().get(point.getBiomeId());
                if (biome != null) {
                    // Direction + BiomeDistance constraint
                    if (point.getDirection() != null && point.getBiomeDistance() != null) {
                        constraints.add(new PointConstraint(
                            ConstraintType.BIOME_DIRECTION_DISTANCE,
                            biome.getBiome(),
                            point.getDirection(),
                            point.getBiomeDistance().getHexes()
                        ));
                    }

                    // BiomeSide + SideOffset constraint
                    if (point.getBiomeSide() != null) {
                        constraints.add(new PointConstraint(
                            ConstraintType.BIOME_SIDE,
                            biome.getBiome(),
                            point.getBiomeSide(),
                            point.getSideOffset() != null ? point.getSideOffset() : 0.5
                        ));
                    }
                }
            }

            if (!constraints.isEmpty()) {
                graph.put(pointId, constraints);
            }
        }

        return graph;
    }

    /**
     * Initializes point position using polymorphic dispatch.
     * Returns false only if the biome does not exist (the only valid failure case).
     */
    private boolean initializePointPosition(Point point, ComposeContext context) {
        Area biome = getBiomeForPoint(point, context);
        if (biome == null) {
            log.warn("Point '{}': biome '{}' not found — skipping",
                point.getName(), point.getBiomeId());
            return false;
        }

        point.initPosition(biome, context);
        return true;
    }

    /**
     * Iteratively adjusts point positions until convergence.
     */
    private boolean iterativelySolvePositions(List<Point> points,
                                              Map<String, List<PointConstraint>> constraintGraph,
                                              ComposeContext context) {
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double maxMovement = 0.0;

            for (Point point : points) {
                String pointId = point.getFeatureId() != null ? point.getFeatureId() : point.getName();
                if (pointId == null) continue;

                List<PointConstraint> constraints = constraintGraph.get(pointId);
                if (constraints == null || constraints.isEmpty()) continue;

                // Calculate target position based on constraints
                PointPosition currentPos = getPointPosition(point, context);
                if (currentPos == null) continue;

                PointPosition targetPos = calculateTargetPosition(point, constraints, context);
                if (targetPos == null) continue;

                // Move point towards target (spring-like)
                double movement = moveTowardsTarget(point, currentPos, targetPos, 0.3, context);
                maxMovement = Math.max(maxMovement, movement);
            }

            log.debug("Iteration {}: max movement = {}", iteration, maxMovement);

            if (maxMovement < CONVERGENCE_THRESHOLD) {
                log.debug("Converged after {} iterations", iteration + 1);
                return true;
            }
        }

        return false;
    }

    /**
     * Finalizes point position by calculating absolute coordinates.
     */
    private boolean finalizePointPosition(Point point, ComposeContext context) {
        // Check if point has shared HexLocalPosition
        if (point.getHexLocalPosition() != null) {
            HexVector2 gridCoord = point.getGridCoordinate();

            if (gridCoord != null) {
                de.mhus.nimbus.generated.types.Vector2Int relativePos =
                    HexLocalUtil.toHexGridLocalCenter(point.getHexLocalPosition());

                int lx = context.getHexGridSize() / 2 + relativePos.getX();
                int lz = context.getHexGridSize() / 2 + relativePos.getZ();

                point.setPlacedCoordinate(gridCoord);
                point.setPlacedLx(lx);
                point.setPlacedLz(lz);
                point.setPlacedInBiome(point.getBiome());
                point.setStatus(FeatureStatus.COMPOSED);
                configureHexGridForPoint(point, gridCoord, context);

                log.debug("Finalized point {} at grid {} with lx={}, lz={}",
                    point.getName(), gridCoord, lx, lz);
                return true;
            }
        }

        // Check if point has shared HexLocalEdgeVector
        if (point.getHexLocalEdgeVector() != null) {
            HexVector2 gridCoord = point.getGridCoordinate();

            if (gridCoord != null) {
                de.mhus.nimbus.generated.types.Vector2Int relativePos =
                    HexLocalUtil.toHexgridLocalCenter(
                        point.getHexLocalEdgeVector(), context.getHexGridSize());

                int lx = context.getHexGridSize() / 2 + relativePos.getX();
                int lz = context.getHexGridSize() / 2 + relativePos.getZ();

                point.setPlacedCoordinate(gridCoord);
                point.setPlacedLx(lx);
                point.setPlacedLz(lz);
                point.setPlacedInBiome(point.getBiome());
                point.setStatus(FeatureStatus.COMPOSED);
                configureHexGridForPoint(point, gridCoord, context);

                log.debug("Finalized edge point {} at grid {} with lx={}, lz={}",
                    point.getName(), gridCoord, lx, lz);
                return true;
            }
        }

        // Last resort: point has gridCoordinate but no position data
        // Create center fallback (should not happen after initPosition, but be safe)
        HexVector2 gridCoord = point.getGridCoordinate();
        if (gridCoord == null) {
            gridCoord = HexVector2.builder().q(0).r(0).build();
            point.setGridCoordinate(gridCoord);
        }
        log.warn("Point '{}': no position data after init, creating center fallback at ({},{})",
            point.getName(), gridCoord.getQ(), gridCoord.getR());

        int divider = HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;
        point.setHexLocalPosition(new de.mhus.nimbus.world.shared.world.HexLocalPosition(
            HexVector2.builder().q(0).r(0).build(), divider, size));

        de.mhus.nimbus.generated.types.Vector2Int relativePos =
            HexLocalUtil.toHexGridLocalCenter(point.getHexLocalPosition());
        int lx = context.getHexGridSize() / 2 + relativePos.getX();
        int lz = context.getHexGridSize() / 2 + relativePos.getZ();

        point.setPlacedCoordinate(gridCoord);
        point.setPlacedLx(lx);
        point.setPlacedLz(lz);
        point.setPlacedInBiome(point.getBiome());
        point.setStatus(FeatureStatus.COMPOSED);
        configureHexGridForPoint(point, gridCoord, context);

        return true;
    }

    /**
     * Configures HexGrid for point types that need it.
     */
    private void configureHexGridForPoint(Point point, HexVector2 gridCoord, ComposeContext context) {
        int hexGridSize = context.getHexGridSize();
        if (point instanceof VillagePoint vp) {
            vp.configureHexGrid(gridCoord, hexGridSize, context);
        } else if (point instanceof MountainPoint mp) {
            mp.configureHexGrid(gridCoord, hexGridSize, context);
        } else if (point instanceof SpikesPoint sp) {
            sp.configureHexGrid(gridCoord, hexGridSize, context);
        } else if (point instanceof MountainFacePoint mfp) {
            mfp.configureHexGrid(gridCoord, hexGridSize, context);
        } else if (point instanceof LakesPoint lp) {
            lp.configureHexGrid(gridCoord, hexGridSize, context);
        }
    }

    // ========== Helper Methods ==========

    private Area getBiomeForPoint(Point point, ComposeContext context) {
        // Try biomeId first (new format)
        if (point.getBiomeId() != null) {
            PlacedBiome placed = context.getBiomeMap().get(point.getBiomeId());
            if (placed != null) {
                return placed.getBiome();
            }
        }

        // Fall back to snap.target (legacy format)
        if (point.getSnap() != null && point.getSnap().getTarget() != null) {
            String targetBiomeName = point.getSnap().getTarget();
            PlacedBiome placed = context.getBiomeMap().get(targetBiomeName);
            if (placed != null) {
                return placed.getBiome();
            }
        }

        return null;
    }

    private PointPosition getPointPosition(Point point, ComposeContext context) {
        HexVector2 gridCoord = point.getGridCoordinate();
        if (gridCoord == null) {
            return null;
        }

        // Get absolute lx/lz coordinates from shared types
        if (point.getHexLocalPosition() != null) {
            de.mhus.nimbus.world.shared.world.HexLocalPosition hexLocalPos = point.getHexLocalPosition();
            de.mhus.nimbus.generated.types.Vector2Int relativePos =
                de.mhus.nimbus.world.shared.util.HexLocalUtil.toHexGridLocalCenter(hexLocalPos);

            int lx = context.getHexGridSize() / 2 + relativePos.getX();
            int lz = context.getHexGridSize() / 2 + relativePos.getZ();
            return new PointPosition(gridCoord, lx, lz);
        }

        if (point.getHexLocalEdgeVector() != null) {
            de.mhus.nimbus.world.shared.world.HexLocalEdgeVector edgeVector = point.getHexLocalEdgeVector();
            de.mhus.nimbus.generated.types.Vector2Int relativePos =
                de.mhus.nimbus.world.shared.util.HexLocalUtil.toHexgridLocalCenter(
                    edgeVector, context.getHexGridSize());

            int lx = context.getHexGridSize() / 2 + relativePos.getX();
            int lz = context.getHexGridSize() / 2 + relativePos.getZ();
            return new PointPosition(gridCoord, lx, lz);
        }

        return null;
    }

    private PointPosition calculateTargetPosition(Point point,
                                                  List<PointConstraint> constraints,
                                                  ComposeContext context) {
        // Simple average of all constraint targets
        double sumQ = 0, sumR = 0, sumLx = 0, sumLz = 0;
        int count = 0;

        for (PointConstraint constraint : constraints) {
            PointPosition target = calculateConstraintTarget(constraint, context);
            if (target != null) {
                sumQ += target.coordinate.getQ();
                sumR += target.coordinate.getR();
                sumLx += target.lx;
                sumLz += target.lz;
                count++;
            }
        }

        if (count == 0) return null;

        return new PointPosition(
            HexVector2.builder()
                .q((int) Math.round(sumQ / count))
                .r((int) Math.round(sumR / count))
                .build(),
            (int) Math.round(sumLx / count),
            (int) Math.round(sumLz / count)
        );
    }

    private PointPosition calculateConstraintTarget(PointConstraint constraint,
                                                   ComposeContext context) {
        // TODO: Implement constraint target calculation based on type
        return null;
    }

    private double moveTowardsTarget(Point point, PointPosition current,
                                    PointPosition target, double factor,
                                    ComposeContext context) {
        // Calculate movement vector
        int deltaQ = target.coordinate.getQ() - current.coordinate.getQ();
        int deltaR = target.coordinate.getR() - current.coordinate.getR();
        int deltaLx = target.lx - current.lx;
        int deltaLz = target.lz - current.lz;

        // Apply movement with factor
        int newQ = current.coordinate.getQ() + (int) Math.round(deltaQ * factor);
        int newR = current.coordinate.getR() + (int) Math.round(deltaR * factor);
        int newLx = current.lx + (int) Math.round(deltaLx * factor);
        int newLz = current.lz + (int) Math.round(deltaLz * factor);

        // Update point position
        // Convert absolute lx/lz back to HexLocal position (relative to grid center)
        int relativeLx = newLx - context.getHexGridSize() / 2;
        int relativeLz = newLz - context.getHexGridSize() / 2;

        // TODO: Convert pixel coordinates back to hex coordinates
        // For now: approximate by creating a position at (0,0) center
        de.mhus.nimbus.generated.types.HexVector2 hexPos =
            de.mhus.nimbus.generated.types.HexVector2.builder()
                .q(0)
                .r(0)
                .build();

        int divider = de.mhus.nimbus.world.shared.util.HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;

        point.setHexLocalPosition(
            new de.mhus.nimbus.world.shared.world.HexLocalPosition(hexPos, divider, size)
        );

        // Update grid coordinate if changed
        HexVector2 newGridCoord = HexVector2.builder().q(newQ).r(newR).build();
        point.setGridCoordinate(newGridCoord);

        // Calculate movement distance
        double movement = Math.sqrt(deltaQ * deltaQ + deltaR * deltaR +
            (deltaLx * deltaLx + deltaLz * deltaLz) / (context.getHexGridSize() * context.getHexGridSize()));

        return movement;
    }

    /**
     * Calculates local position (lx, lz) from side and offset.
     * Uses denominator=4 as specified (numerator 1-3: NORTH, CENTER, SOUTH).
     */
    private int[] calculateLocalPositionFromSide(WHexGrid.EDGE side, Double offset,
                                                 int hexGridSize) {
        if (offset == null) offset = 0.5;  // Default to center

        // Convert offset to numerator (0.0->0, 0.25->1, 0.5->2, 0.75->3, 1.0->4)
        int numerator = (int) Math.round(offset * 4);
        numerator = Math.max(0, Math.min(4, numerator));

        // Create HexLocalEdgeVector
        HexLocalEdgeVector vector = new HexLocalEdgeVector(side, numerator, 4);

        // Use HexLocalUtil to calculate actual lx, lz from side coordinates
        Vector2Int pos = HexLocalUtil.toHexgridLocalCenter(vector, hexGridSize);
        return new int[]{pos.getX(), pos.getZ()};
    }

    // ========== Inner Classes ==========

    @Data
    private static class PointPosition {
        HexVector2 coordinate;
        int lx;
        int lz;

        PointPosition(HexVector2 coordinate, int lx, int lz) {
            this.coordinate = coordinate;
            this.lx = lx;
            this.lz = lz;
        }
    }

    private static class PointConstraint {
        ConstraintType type;
        Object target;  // Can be Point, Area, etc.
        Direction direction;
        Object value;  // Can be Integer (distance), Double (offset), etc.

        PointConstraint(ConstraintType type, Object target, Direction direction, Object value) {
            this.type = type;
            this.target = target;
            this.direction = direction;
            this.value = value;
        }
    }

    private enum ConstraintType {
        RELATIVE_TO_POINT,
        BIOME_DIRECTION_DISTANCE,
        BIOME_SIDE
    }
}
