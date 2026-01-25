package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for closed loop Walls (ring around a point)
 */
@Slf4j
public class ClosedLoopWallTest {

    @Test
    public void testClosedLoopWallAroundPoint() {
        log.info("=== Testing Closed Loop Wall Around Point ===");

        // Create composition with biome, point, and closed loop wall
        HexComposition composition = createCompositionWithClosedLoopWall();

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
        assertEquals(1, pointResult.getComposedPoints(), "Should compose the point");
        log.info("Placed {} points", pointResult.getComposedPoints());

        // Compose flows (walls)
        FlowComposer flowComposer = new FlowComposer();
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomePlacementResult);
        assertTrue(flowResult.isSuccess(), "Flow composition should succeed");
        assertEquals(1, flowResult.getComposedFlows(), "Should compose the wall");
        log.info("Composed {} flows with {} total segments",
            flowResult.getComposedFlows(), flowResult.getTotalSegments());

        // Verify wall is closed loop
        Wall wall = findWall(composition, "city-wall");
        assertNotNull(wall, "Should find wall");
        assertTrue(wall.isClosedLoop(), "Wall should be closed loop");
        assertNotNull(wall.getRoute(), "Wall should have route");

        // AreaSize.SMALL has from=1, so expect 6 * 1 = 6 segments
        int expectedMinSegments = 6 * 1;  // 6 * radius for hex ring
        assertTrue(wall.getRoute().size() >= expectedMinSegments,
            "Wall should have at least " + expectedMinSegments + " segments, got " + wall.getRoute().size());

        log.info("Wall has {} segments forming a closed loop", wall.getRoute().size());

        // Verify segments form a closed loop
        // First segment should connect from last to first
        FeatureHexGrid firstGrid = wall.getHexGrids().get(0);
        List<FlowSegment> firstSegments = firstGrid.getFlowSegmentsByType(FlowType.WALL);
        assertFalse(firstSegments.isEmpty(), "First grid should have wall segments");

        FlowSegment firstSegment = firstSegments.get(0);
        assertNotNull(firstSegment.getFromSide(), "First segment should have fromSide (from last)");
        assertNotNull(firstSegment.getToSide(), "First segment should have toSide (to next)");
        log.info("First segment: from {} to {}", firstSegment.getFromSide(), firstSegment.getToSide());

        // Last segment should connect to first
        FeatureHexGrid lastGrid = wall.getHexGrids().get(wall.getHexGrids().size() - 1);
        List<FlowSegment> lastSegments = lastGrid.getFlowSegmentsByType(FlowType.WALL);
        assertFalse(lastSegments.isEmpty(), "Last grid should have wall segments");

        FlowSegment lastSegment = lastSegments.get(0);
        assertNotNull(lastSegment.getFromSide(), "Last segment should have fromSide (from prev)");
        assertNotNull(lastSegment.getToSide(), "Last segment should have toSide (to first)");
        log.info("Last segment: from {} to {}", lastSegment.getFromSide(), lastSegment.getToSide());

        log.info("=== Closed Loop Wall Test Completed ===");
    }

    @Test
    public void testClosedLoopWithDifferentRadius() {
        log.info("=== Testing Closed Loop With Different Radius ===");

        HexComposition composition = new HexComposition();
        composition.setName("radius-test");

        List<Feature> features = new ArrayList<>();

        // Biome: Large plains
        PlainsBiome plains = new PlainsBiome();
        plains.setName("plains");
        plains.setType(BiomeType.PLAINS);
        plains.setSize(AreaSize.LARGE);
        plains.setShape(AreaShape.CIRCLE);
        plains.initialize();
        features.add(plains);

        // Point: City center
        Point city = new Point();
        city.setName("city-center");
        city.setFeatureId("city-center");
        city.setStatus(FeatureStatus.NEW);
        city.setSnap(SnapConfig.builder()
            .mode(SnapMode.INSIDE)
            .target("plains")
            .build());
        features.add(city);

        // Wall with radius 3
        Wall wall = Wall.builder()
            .material("stone")
            .height(10)
            .level(100)
            .build();
        wall.setName("outer-wall");
        wall.setFeatureId("outer-wall");
        wall.setStatus(FeatureStatus.NEW);
        wall.setType(FlowType.WALL);
        wall.setStartPointId("city-center");
        wall.setEndPointId("city-center");
        wall.setSizeFrom(3);  // Radius 3
        wall.setSizeTo(3);
        wall.setWidthBlocks(2);
        wall.initialize();
        features.add(wall);

        composition.setFeatures(features);

        // Prepare and compose
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        preparer.prepare(composition);

        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult biomePlacementResult = biomeComposer.compose(composition, "test-world", 54321L);

        PointComposer pointComposer = new PointComposer();
        pointComposer.composePoints(composition, biomePlacementResult);

        FlowComposer flowComposer = new FlowComposer();
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomePlacementResult);

        assertTrue(flowResult.isSuccess());

        // Radius 3 should give 6 * 3 = 18 segments
        int expectedSegments = 6 * 3;
        assertEquals(expectedSegments, wall.getRoute().size(),
            "Wall with radius 3 should have " + expectedSegments + " segments");

        log.info("Wall with radius 3 has {} segments", wall.getRoute().size());
        log.info("=== Radius Test Completed ===");
    }

    /**
     * Creates a composition with a point and a closed loop wall around it
     */
    private HexComposition createCompositionWithClosedLoopWall() {
        HexComposition composition = new HexComposition();
        composition.setName("wall-test");

        List<Feature> features = new ArrayList<>();

        // Biome: Large plains
        PlainsBiome plains = new PlainsBiome();
        plains.setName("plains");
        plains.setType(BiomeType.PLAINS);
        plains.setSize(AreaSize.LARGE);
        plains.setShape(AreaShape.CIRCLE);
        plains.initialize();
        features.add(plains);

        // Point: City center
        Point city = new Point();
        city.setName("city-center");
        city.setFeatureId("city-center");
        city.setStatus(FeatureStatus.NEW);
        city.setSnap(SnapConfig.builder()
            .mode(SnapMode.INSIDE)
            .target("plains")
            .build());
        features.add(city);

        // Wall around the city (closed loop)
        Wall wall = Wall.builder()
            .material("stone")
            .height(10)
            .level(100)
            .build();
        wall.setName("city-wall");
        wall.setFeatureId("city-wall");
        wall.setStatus(FeatureStatus.NEW);
        wall.setType(FlowType.WALL);
        wall.setStartPointId("city-center");
        wall.setEndPointId("city-center");  // Same as start = closed loop
        wall.setSize(AreaSize.SMALL);  // Radius 2
        wall.setShapeHint("RING");
        wall.setWidthBlocks(2);
        wall.initialize();
        features.add(wall);

        composition.setFeatures(features);
        return composition;
    }

    /**
     * Finds a Wall by name in the composition
     */
    private Wall findWall(HexComposition composition, String name) {
        if (composition.getFeatures() == null) return null;

        for (Feature feature : composition.getFeatures()) {
            if (feature instanceof Wall && name.equals(feature.getName())) {
                return (Wall) feature;
            }
        }
        return null;
    }
}
