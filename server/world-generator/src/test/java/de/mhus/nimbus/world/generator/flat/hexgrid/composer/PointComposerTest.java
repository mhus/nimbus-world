package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PointComposer - placing Points within biomes with snap configuration
 */
@Slf4j
public class PointComposerTest {

    @Test
    public void testPointPlacementWithSnap() {
        log.info("=== Testing Point Placement with Snap Configuration ===");

        // Create composition with biomes and points
        HexComposition composition = createCompositionWithPoints();

        // Prepare composition
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        boolean prepared = preparer.prepare(composition);
        assertTrue(prepared, "Composition should prepare successfully");

        // Compose biomes first
        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult placementResult = biomeComposer.compose(composition, "test-world", 12345L);

        assertTrue(placementResult.isSuccess(), "Biome composition should succeed");
        log.info("Placed {} biomes", placementResult.getPlacedBiomes().size());

        // Compose points
        PointComposer pointComposer = new PointComposer();
        PointComposer.PointCompositionResult pointResult = pointComposer.composePoints(
            composition, placementResult);

        // Verify results
        assertTrue(pointResult.isSuccess(), "Point composition should succeed");
        assertEquals(2, pointResult.getTotalPoints(), "Should have 2 points");
        assertEquals(2, pointResult.getComposedPoints(), "Should compose both points");
        assertEquals(0, pointResult.getFailedPoints(), "Should have no failed points");

        // Verify point placements
        Point city = findPoint(composition, "minas-tirith");
        assertNotNull(city, "Should find minas-tirith");
        assertTrue(city.isPlaced(), "City should be placed");
        assertNotNull(city.getPlacedCoordinate(), "Should have coordinate");
        assertNotNull(city.getPlacedLx(), "Should have local X");
        assertNotNull(city.getPlacedLz(), "Should have local Z");
        assertEquals("gondor-heartlands", city.getPlacedInBiome(), "Should be in gondor-heartlands");

        log.info("Point 'minas-tirith' placed at: {}", city.getPlacedPositionString());

        Point lighthouse = findPoint(composition, "coastal-lighthouse");
        assertNotNull(lighthouse, "Should find lighthouse");
        assertTrue(lighthouse.isPlaced(), "Lighthouse should be placed");
        assertEquals("coast", lighthouse.getPlacedInBiome(), "Should be at coast");

        log.info("Point 'coastal-lighthouse' placed at: {}", lighthouse.getPlacedPositionString());

        log.info("=== Point Placement Test Completed ===");
    }

    @Test
    public void testPointSnapModeEdge() {
        log.info("=== Testing Point Snap Mode EDGE ===");

        HexComposition composition = new HexComposition();
        composition.setName("edge-test");

        // Create a large biome
        PlainsBiome plains = new PlainsBiome();
        plains.setName("large-plains");
        plains.setType(BiomeType.PLAINS);
        plains.setSize(AreaSize.LARGE);
        plains.setShape(AreaShape.CIRCLE);
        plains.initialize();

        // Create point at edge
        Point edgePoint = new Point();
        edgePoint.setName("edge-marker");
        edgePoint.setFeatureId("edge-marker");
        edgePoint.setStatus(FeatureStatus.NEW);
        edgePoint.setSnap(SnapConfig.builder()
            .mode(SnapMode.EDGE)
            .target("large-plains")
            .build());

        composition.setFeatures(List.of(plains, edgePoint));

        // Prepare composition
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        boolean prepared = preparer.prepare(composition);
        assertTrue(prepared, "Composition should prepare successfully");

        // Compose
        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult placementResult = biomeComposer.compose(composition, "test-world", 12345L);
        assertTrue(placementResult.isSuccess());

        PointComposer pointComposer = new PointComposer();
        PointComposer.PointCompositionResult pointResult = pointComposer.composePoints(
            composition, placementResult);

        assertTrue(pointResult.isSuccess());
        assertTrue(edgePoint.isPlaced());

        log.info("Edge point placed at: {}", edgePoint.getPlacedPositionString());

        // Verify point is actually at the edge
        PlacedBiome placedPlains = placementResult.getPlacedBiomes().stream()
            .filter(p -> p.getBiome().getName().equals("large-plains"))
            .findFirst()
            .orElse(null);

        assertNotNull(placedPlains);

        // Point should be at one of the edge coordinates
        boolean isAtEdge = isCoordinateAtEdge(edgePoint.getPlacedCoordinate(), placedPlains.getCoordinates());
        assertTrue(isAtEdge, "Point should be at edge of biome");

        log.info("=== Edge Mode Test Completed ===");
    }

    @Test
    public void testPointAvoidFilter() {
        log.info("=== Testing Point Avoid Filter ===");

        HexComposition composition = new HexComposition();
        composition.setName("avoid-test");

        // Create two biomes
        PlainsBiome plains = new PlainsBiome();
        plains.setName("plains");
        plains.setType(BiomeType.PLAINS);
        plains.setSize(AreaSize.LARGE);
        plains.setShape(AreaShape.CIRCLE);
        plains.initialize();

        ForestBiome forest = new ForestBiome();
        forest.setName("forest");
        forest.setType(BiomeType.FOREST);
        forest.setSize(AreaSize.MEDIUM);
        forest.setShape(AreaShape.CIRCLE);
        forest.setPositions(List.of(RelativePosition.builder()
            .anchor("plains")
            .direction(Direction.E)
            .distanceFrom(1)
            .distanceTo(3)
            .build()));
        forest.initialize();

        // Create point that avoids forest
        Point point = new Point();
        point.setName("avoid-forest-point");
        point.setFeatureId("avoid-forest-point");
        point.setStatus(FeatureStatus.NEW);
        point.setSnap(SnapConfig.builder()
            .mode(SnapMode.INSIDE)
            .target("plains")
            .avoid(List.of("forest"))
            .build());

        composition.setFeatures(List.of(plains, forest, point));

        // Prepare composition
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        boolean prepared = preparer.prepare(composition);
        assertTrue(prepared, "Composition should prepare successfully");

        // Compose
        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult placementResult = biomeComposer.compose(composition, "test-world", 54321L);

        PointComposer pointComposer = new PointComposer();
        PointComposer.PointCompositionResult pointResult = pointComposer.composePoints(
            composition, placementResult);

        assertTrue(pointResult.isSuccess());
        assertTrue(point.isPlaced());

        log.info("Point placed at: {}", point.getPlacedPositionString());

        // Verify point is NOT near forest
        PlacedBiome placedForest = placementResult.getPlacedBiomes().stream()
            .filter(p -> p.getBiome().getName().equals("forest"))
            .findFirst()
            .orElse(null);

        assertNotNull(placedForest);

        // Point should not be at or adjacent to forest coordinates
        boolean isNearForest = isCoordinateNearBiome(point.getPlacedCoordinate(), placedForest.getCoordinates());
        assertFalse(isNearForest, "Point should avoid forest");

        log.info("=== Avoid Filter Test Completed ===");
    }

    /**
     * Creates a composition with biomes and points similar to the Gondor example
     */
    private HexComposition createCompositionWithPoints() {
        HexComposition composition = new HexComposition();
        composition.setName("gondor-region");

        List<Feature> features = new ArrayList<>();

        // Biome: Gondor Heartlands
        PlainsBiome gondorHeartlands = new PlainsBiome();
        gondorHeartlands.setName("gondor-heartlands");
        gondorHeartlands.setType(BiomeType.PLAINS);
        gondorHeartlands.setSize(AreaSize.LARGE);
        gondorHeartlands.setShape(AreaShape.CIRCLE);
        gondorHeartlands.initialize();
        features.add(gondorHeartlands);

        // Biome: White Mountains
        MountainBiome whiteMountains = new MountainBiome();
        whiteMountains.setName("white-mountains");
        whiteMountains.setType(BiomeType.MOUNTAINS);
        whiteMountains.setSize(AreaSize.MEDIUM);
        whiteMountains.setShape(AreaShape.CIRCLE);
        whiteMountains.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        whiteMountains.setPositions(List.of(RelativePosition.builder()
            .anchor("gondor-heartlands")
            .direction(Direction.N)
            .distanceFrom(1)
            .distanceTo(3)
            .build()));
        whiteMountains.initialize();
        features.add(whiteMountains);

        // Biome: Anduin River (simplified as area for this test)
        ForestBiome anduinRiver = new ForestBiome();
        anduinRiver.setName("anduin-great-river");
        anduinRiver.setType(BiomeType.FOREST);
        anduinRiver.setSize(AreaSize.SMALL);
        anduinRiver.setShape(AreaShape.CIRCLE);
        anduinRiver.setPositions(List.of(RelativePosition.builder()
            .anchor("gondor-heartlands")
            .direction(Direction.W)
            .distanceFrom(1)
            .distanceTo(2)
            .build()));
        anduinRiver.initialize();
        features.add(anduinRiver);

        // Biome: Coast
        CoastBiome coast = new CoastBiome();
        coast.setName("coast");
        coast.setType(BiomeType.COAST);
        coast.setSize(AreaSize.SMALL);
        coast.setShape(AreaShape.CIRCLE);
        coast.setPositions(List.of(RelativePosition.builder()
            .anchor("gondor-heartlands")
            .direction(Direction.S)
            .distanceFrom(3)
            .distanceTo(5)
            .build()));
        coast.initialize();
        features.add(coast);

        // Point: Minas Tirith
        Point minasTirith = new Point();
        minasTirith.setName("minas-tirith");
        minasTirith.setFeatureId("minas-tirith");
        minasTirith.setStatus(FeatureStatus.NEW);
        minasTirith.setSnap(SnapConfig.builder()
            .mode(SnapMode.INSIDE)
            .target("gondor-heartlands")
            .avoid(List.of("anduin-great-river"))
            .preferNear(List.of("white-mountains"))
            .build());
        features.add(minasTirith);

        // Point: Coastal Lighthouse
        Point lighthouse = new Point();
        lighthouse.setName("coastal-lighthouse");
        lighthouse.setFeatureId("coastal-lighthouse");
        lighthouse.setStatus(FeatureStatus.NEW);
        lighthouse.setSnap(SnapConfig.builder()
            .mode(SnapMode.EDGE)
            .target("coast")
            .build());
        features.add(lighthouse);

        composition.setFeatures(features);
        return composition;
    }

    /**
     * Finds a Point by name in the composition
     */
    private Point findPoint(HexComposition composition, String name) {
        if (composition.getFeatures() == null) return null;

        for (Feature feature : composition.getFeatures()) {
            if (feature instanceof Point && name.equals(feature.getName())) {
                return (Point) feature;
            }
        }
        return null;
    }

    /**
     * Checks if a coordinate is at the edge of a biome
     */
    private boolean isCoordinateAtEdge(HexVector2 coord, List<HexVector2> biomeCoords) {
        // Build set of biome coordinates
        Set<String> coordSet = new java.util.HashSet<>();
        for (HexVector2 c : biomeCoords) {
            coordSet.add(c.getQ() + ":" + c.getR());
        }

        // Check if at least one neighbor is NOT in the biome
        int[][] directions = {{1,-1}, {1,0}, {0,1}, {-1,1}, {-1,0}, {0,-1}};
        for (int[] dir : directions) {
            String neighborKey = (coord.getQ() + dir[0]) + ":" + (coord.getR() + dir[1]);
            if (!coordSet.contains(neighborKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a coordinate is at or adjacent to biome coordinates
     */
    private boolean isCoordinateNearBiome(HexVector2 coord, List<HexVector2> biomeCoords) {
        Set<String> coordSet = new java.util.HashSet<>();
        for (HexVector2 c : biomeCoords) {
            coordSet.add(c.getQ() + ":" + c.getR());
        }

        // Check if coord itself is in biome
        if (coordSet.contains(coord.getQ() + ":" + coord.getR())) {
            return true;
        }

        // Check if any neighbor is in biome
        int[][] directions = {{1,-1}, {1,0}, {0,1}, {-1,1}, {-1,0}, {0,-1}};
        for (int[] dir : directions) {
            String neighborKey = (coord.getQ() + dir[0]) + ":" + (coord.getR() + dir[1]);
            if (coordSet.contains(neighborKey)) {
                return true;
            }
        }
        return false;
    }
}
