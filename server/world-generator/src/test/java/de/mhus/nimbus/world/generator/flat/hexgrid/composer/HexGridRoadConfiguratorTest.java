package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HexGridRoadConfigurator
 */
@Slf4j
public class HexGridRoadConfiguratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testRoadConfigurationOnBiomeGrids() throws Exception {
        log.info("=== Testing Road Configuration on Biome Grids ===");

        // Create composition with biome and road
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("road-config-test")
            .features(new ArrayList<>())
            .build();

        // Create a biome at [0,0]
        Biome plains = Biome.builder()
            .type(BiomeType.PLAINS)
            .build();
        plains.setName("plains");
        plains.setTitle("Test Plains");
        plains.setShape(AreaShape.CIRCLE);
        plains.setSize(AreaSize.SMALL);

        // Add FeatureHexGrid to biome at [0,0]
        FeatureHexGrid biomeGrid = FeatureHexGrid.builder()
            .coordinate(HexVector2.builder().q(0).r(0).build())
            .name("plains-0-0")
            .build();
        plains.addHexGrid(biomeGrid);

        composition.getFeatures().add(plains);

        // Create a road crossing the biome
        Road road = Road.builder()
            .roadType("street")
            .level(95)
            .build();
        road.setName("test-road");
        road.setFeatureId("test-road-1");
        road.setWidthBlocks(3);
        road.setType(FlowType.ROAD);

        // Add FeatureHexGrid to road at [0,0] with FlowSegment
        FeatureHexGrid roadGrid = FeatureHexGrid.builder()
            .coordinate(HexVector2.builder().q(0).r(0).build())
            .name("road-0-0")
            .build();

        FlowSegment segment = FlowSegment.builder()
            .flowType(FlowType.ROAD)
            .fromSide(de.mhus.nimbus.world.shared.world.WHexGrid.SIDE.WEST)
            .toSide(de.mhus.nimbus.world.shared.world.WHexGrid.SIDE.EAST)
            .width(3)
            .level(95)
            .type("street")
            .flowFeatureId("test-road-1")
            .build();

        roadGrid.addFlowSegment(segment);
        road.addHexGrid(roadGrid);

        composition.getFeatures().add(road);

        // Convert FlowSegments to RoadConfigParts and add to biome grid
        // (This simulates what FlowComposer.convertFlowSegmentsToRoadConfigParts does)
        RoadConfigPart routePart1 = RoadConfigPart.createRouteSidePart(
            segment.getFromSide(),
            segment.getWidth(),
            segment.getLevel(),
            segment.getType()
        );
        biomeGrid.addRoadConfigPart(routePart1);

        if (segment.getToSide() != null && !segment.getToSide().equals(segment.getFromSide())) {
            RoadConfigPart routePart2 = RoadConfigPart.createRouteSidePart(
                segment.getToSide(),
                segment.getWidth(),
                segment.getLevel(),
                segment.getType()
            );
            biomeGrid.addRoadConfigPart(routePart2);
        }

        // Initialize composition
        composition.initialize();

        // Configure roads using HexGridRoadConfigurator
        HexGridRoadConfigurator configurator = new HexGridRoadConfigurator();
        HexGridRoadConfigurator.RoadConfigurationResult result = configurator.configureRoads(composition);

        log.info("Configuration result: configured={}/{}, segments={}",
            result.getConfiguredGrids(), result.getTotalGrids(), result.getTotalSegments());

        // Verify results
        assertTrue(result.isSuccess(), "Configuration should succeed");
        assertEquals(1, result.getTotalGrids(), "Should have 1 Area grid");
        assertEquals(1, result.getConfiguredGrids(), "Should configure 1 grid");
        assertEquals(2, result.getTotalSegments(), "Should have 2 RoadConfigParts (WEST + EAST)");

        // Check that road parameter was added to biome grid
        String roadParam = biomeGrid.getParameters().get("road");
        assertNotNull(roadParam, "Biome grid should have road parameter");
        log.info("Road parameter: {}", roadParam);

        // Parse and validate road JSON
        Map<String, Object> roadConfig = objectMapper.readValue(roadParam, Map.class);
        assertNotNull(roadConfig.get("level"), "Road config should have level");
        assertNotNull(roadConfig.get("route"), "Road config should have route");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> route = (java.util.List<Map<String, Object>>) roadConfig.get("route");
        assertEquals(2, route.size(), "Route should have 2 entries (WEST + EAST)");

        Map<String, Object> firstRoute = route.get(0);
        assertEquals("WEST", firstRoute.get("side"), "First route should be WEST");
        assertEquals(3, firstRoute.get("width"), "Width should be 3");
        assertEquals("street", firstRoute.get("type"), "Type should be street");

        Map<String, Object> secondRoute = route.get(1);
        assertEquals("EAST", secondRoute.get("side"), "Second route should be EAST");
        assertEquals(3, secondRoute.get("width"), "Width should be 3");
        assertEquals("street", secondRoute.get("type"), "Type should be street");

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testRiverConfigurationOnBiomeGrids() throws Exception {
        log.info("=== Testing River Configuration on Biome Grids ===");

        // Create composition with biome and river
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("river-config-test")
            .features(new ArrayList<>())
            .build();

        // Create a biome at [0,0]
        Biome forest = Biome.builder()
            .type(BiomeType.FOREST)
            .build();
        forest.setName("forest");
        forest.setTitle("Test Forest");
        forest.setShape(AreaShape.CIRCLE);
        forest.setSize(AreaSize.SMALL);

        // Add FeatureHexGrid to biome at [0,0]
        FeatureHexGrid biomeGrid = FeatureHexGrid.builder()
            .coordinate(HexVector2.builder().q(0).r(0).build())
            .name("forest-0-0")
            .build();
        forest.addHexGrid(biomeGrid);

        composition.getFeatures().add(forest);

        // Create a river crossing the biome
        River river = River.builder()
            .depth(2)
            .level(50)
            .build();
        river.setName("test-river");
        river.setFeatureId("test-river-1");
        river.setWidthBlocks(5);
        river.setType(FlowType.RIVER);

        // Add FeatureHexGrid to river at [0,0] with FlowSegments
        FeatureHexGrid riverGrid = FeatureHexGrid.builder()
            .coordinate(HexVector2.builder().q(0).r(0).build())
            .name("river-0-0")
            .build();

        FlowSegment segment = FlowSegment.builder()
            .flowType(FlowType.RIVER)
            .fromSide(de.mhus.nimbus.world.shared.world.WHexGrid.SIDE.NORTH_WEST)
            .toSide(de.mhus.nimbus.world.shared.world.WHexGrid.SIDE.SOUTH_WEST)
            .width(5)
            .depth(2)
            .level(50)
            .flowFeatureId("test-river-1")
            .build();

        riverGrid.addFlowSegment(segment);
        river.addHexGrid(riverGrid);

        composition.getFeatures().add(river);

        // Convert FlowSegments to RiverConfigParts and add to biome grid
        // (This simulates what FlowComposer.convertFlowSegmentsToRiverConfigParts does)
        RiverConfigPart fromPart = RiverConfigPart.createFromPart(
            segment.getFromSide(),
            segment.getWidth(),
            segment.getDepth(),
            segment.getLevel(),
            segment.getFlowFeatureId()
        );
        biomeGrid.addRiverConfigPart(fromPart);

        RiverConfigPart toPart = RiverConfigPart.createToPart(
            segment.getToSide(),
            segment.getWidth(),
            segment.getDepth(),
            segment.getLevel(),
            segment.getFlowFeatureId()
        );
        biomeGrid.addRiverConfigPart(toPart);

        // Initialize composition
        composition.initialize();

        // Configure rivers using HexGridRoadConfigurator
        HexGridRoadConfigurator configurator = new HexGridRoadConfigurator();
        HexGridRoadConfigurator.RoadConfigurationResult result = configurator.configureRoads(composition);

        log.info("Configuration result: configured={}/{}, segments={}",
            result.getConfiguredGrids(), result.getTotalGrids(), result.getTotalSegments());

        // Verify results
        assertTrue(result.isSuccess(), "Configuration should succeed");
        assertEquals(1, result.getTotalGrids(), "Should have 1 Area grid");
        assertEquals(1, result.getConfiguredGrids(), "Should configure 1 grid");

        // Check that river parameter was added to biome grid
        String riverParam = biomeGrid.getParameters().get("river");
        assertNotNull(riverParam, "Biome grid should have river parameter");
        log.info("River parameter: {}", riverParam);

        // Parse and validate river JSON
        Map<String, Object> riverConfig = objectMapper.readValue(riverParam, Map.class);
        assertNotNull(riverConfig.get("from"), "River config should have from");
        assertNotNull(riverConfig.get("to"), "River config should have to");
        assertNotNull(riverConfig.get("groupId"), "River config should have groupId");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> from = (java.util.List<Map<String, Object>>) riverConfig.get("from");
        assertFalse(from.isEmpty(), "From should not be empty");

        Map<String, Object> firstFrom = from.get(0);
        assertEquals("NORTH_WEST", firstFrom.get("side"), "First from should be NORTH_WEST");
        assertEquals(5, firstFrom.get("width"), "Width should be 5");
        assertEquals(2, firstFrom.get("depth"), "Depth should be 2");

        log.info("=== Test completed successfully ===");
    }
}
