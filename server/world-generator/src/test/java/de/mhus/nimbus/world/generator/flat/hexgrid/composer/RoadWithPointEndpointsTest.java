package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Roads connecting Points with lx/lz coordinates instead of sides
 */
@Slf4j
public class RoadWithPointEndpointsTest {

    @Test
    public void testRoadBetweenTwoPoints() {
        log.info("=== Testing Road Between Two Points ===");

        // Create composition with biomes, points, and road
        HexComposition composition = createCompositionWithRoadBetweenPoints();

        // Prepare composition
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        boolean prepared = preparer.prepare(composition);
        assertTrue(prepared, "Composition should prepare successfully");

        // Compose biomes
        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult biomePlacementResult = biomeComposer.compose(composition, "test-world", 12345L);
        assertTrue(biomePlacementResult.isSuccess(), "Biome composition should succeed");
        log.info("Placed {} biomes", biomePlacementResult.getPlacedBiomes().size());

        // Compose points
        PointComposer pointComposer = new PointComposer();
        PointComposer.PointCompositionResult pointResult = pointComposer.composePoints(
            composition, biomePlacementResult);
        assertTrue(pointResult.isSuccess(), "Point composition should succeed");
        assertEquals(2, pointResult.getComposedPoints(), "Should compose both points");
        log.info("Placed {} points", pointResult.getComposedPoints());

        // Compose flows (roads)
        FlowComposer flowComposer = new FlowComposer();
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomePlacementResult);
        assertTrue(flowResult.isSuccess(), "Flow composition should succeed");
        assertEquals(1, flowResult.getComposedFlows(), "Should compose the road");
        log.info("Composed {} flows with {} total segments",
            flowResult.getComposedFlows(), flowResult.getTotalSegments());

        // Verify road endpoints
        Road road = findRoad(composition, "road-between-cities");
        assertNotNull(road, "Should find road");
        assertNotNull(road.getStartPointFeature(), "Should have start point feature");
        assertNotNull(road.getEndPointFeature(), "Should have end point feature");
        assertEquals("city-a", road.getStartPointFeature().getName(), "Should start at city-a");
        assertEquals("city-b", road.getEndPointFeature().getName(), "Should end at city-b");

        log.info("Road starts at Point '{}' with lx={}, lz={}",
            road.getStartPointFeature().getName(),
            road.getStartPointFeature().getPlacedLx(),
            road.getStartPointFeature().getPlacedLz());

        log.info("Road ends at Point '{}' with lx={}, lz={}",
            road.getEndPointFeature().getName(),
            road.getEndPointFeature().getPlacedLx(),
            road.getEndPointFeature().getPlacedLz());

        // Verify flow segments have lx/lz coordinates instead of sides
        assertTrue(road.getRoute().size() > 0, "Road should have route");

        // First segment should have fromLx/fromLz (from Point)
        FeatureHexGrid firstGrid = road.getHexGrids().get(0);
        List<FlowSegment> firstSegments = firstGrid.getFlowSegmentsByType(FlowType.ROAD);
        assertFalse(firstSegments.isEmpty(), "First grid should have road segments");

        FlowSegment firstSegment = firstSegments.get(0);
        if (firstGrid.getCoordinate().equals(road.getStartPoint())) {
            // This is the start grid - should have fromLx/fromLz
            assertTrue(firstSegment.hasFromCoordinates(), "Start segment should have from coordinates");
            assertNotNull(firstSegment.getFromLx(), "Start segment should have fromLx");
            assertNotNull(firstSegment.getFromLz(), "Start segment should have fromLz");
            log.info("First segment uses Point coordinates: fromLx={}, fromLz={}",
                firstSegment.getFromLx(), firstSegment.getFromLz());
        }

        // Last segment should have toLx/toLz (to Point)
        FeatureHexGrid lastGrid = road.getHexGrids().get(road.getHexGrids().size() - 1);
        List<FlowSegment> lastSegments = lastGrid.getFlowSegmentsByType(FlowType.ROAD);
        assertFalse(lastSegments.isEmpty(), "Last grid should have road segments");

        FlowSegment lastSegment = lastSegments.get(0);
        if (lastGrid.getCoordinate().equals(road.getEndPoint())) {
            // This is the end grid - should have toLx/toLz
            assertTrue(lastSegment.hasToCoordinates(), "End segment should have to coordinates");
            assertNotNull(lastSegment.getToLx(), "End segment should have toLx");
            assertNotNull(lastSegment.getToLz(), "End segment should have toLz");
            log.info("Last segment uses Point coordinates: toLx={}, toLz={}",
                lastSegment.getToLx(), lastSegment.getToLz());
        }

        log.info("=== Road Between Points Test Completed ===");
    }

    /**
     * Creates a composition with two points and a road connecting them
     */
    private HexComposition createCompositionWithRoadBetweenPoints() {
        HexComposition composition = new HexComposition();
        composition.setName("road-test");

        List<Feature> features = new ArrayList<>();

        // Biome: Large plains
        PlainsBiome plains = new PlainsBiome();
        plains.setName("plains");
        plains.setType(BiomeType.PLAINS);
        plains.setSize(AreaSize.LARGE);
        plains.setShape(AreaShape.CIRCLE);
        plains.initialize();
        features.add(plains);

        // Point A: City on the west side
        Point cityA = new Point();
        cityA.setName("city-a");
        cityA.setFeatureId("city-a");
        cityA.setStatus(FeatureStatus.NEW);
        cityA.setSnap(SnapConfig.builder()
            .mode(SnapMode.INSIDE)
            .target("plains")
            .build());
        features.add(cityA);

        // Point B: City on the east side
        Point cityB = new Point();
        cityB.setName("city-b");
        cityB.setFeatureId("city-b");
        cityB.setStatus(FeatureStatus.NEW);
        cityB.setSnap(SnapConfig.builder()
            .mode(SnapMode.INSIDE)
            .target("plains")
            .build());
        features.add(cityB);

        // Road connecting both cities
        Road road = Road.builder()
            .roadType("street")
            .level(100)
            .build();
        road.setName("road-between-cities");
        road.setFeatureId("road-between-cities");
        road.setStatus(FeatureStatus.NEW);
        road.setType(FlowType.ROAD);
        road.setStartPointId("city-a");
        road.setEndPointId("city-b");
        road.setWidthBlocks(3);
        road.initialize();
        features.add(road);

        composition.setFeatures(features);
        return composition;
    }

    /**
     * Finds a Road by name in the composition
     */
    private Road findRoad(HexComposition composition, String name) {
        if (composition.getFeatures() == null) return null;

        for (Feature feature : composition.getFeatures()) {
            if (feature instanceof Road && name.equals(feature.getName())) {
                return (Road) feature;
            }
        }
        return null;
    }
}
