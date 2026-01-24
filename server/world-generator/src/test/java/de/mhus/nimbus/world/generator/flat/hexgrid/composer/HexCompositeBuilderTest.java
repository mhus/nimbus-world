package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for HexCompositeBuilder - orchestrates complete composition pipeline
 */
@Slf4j
public class HexCompositeBuilderTest {

    private WHexGridRepository mockRepository;

    @BeforeEach
    public void setup() {
        // Mock repository
        mockRepository = Mockito.mock(WHexGridRepository.class);

        // Mock repository to return empty for all lookups
        when(mockRepository.findByWorldIdAndPosition(anyString(), anyString()))
            .thenReturn(Optional.empty());

        // Mock saveAll to return what was passed in
        when(mockRepository.saveAll(any())).thenAnswer(invocation -> {
            List<WHexGrid> grids = invocation.getArgument(0);
            log.info("Mock repository saved {} grids", grids.size());
            return grids;
        });
    }

    @Test
    public void testSimpleCompositionWithRoad() {
        log.info("=== Testing Simple Composition with Road using Builder ===");

        // Create composition with two biomes and a road
        HexComposition composition = createCompositionWithRoad();

        // Use builder to orchestrate entire pipeline
        HexCompositeBuilder.CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(12345L)
            .fillGaps(true)
            .oceanBorderRings(1)
            .generateWHexGrids(false)  // Don't generate WHexGrids in this test
            .build()
            .compose();

        // Verify result
        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNull(result.getErrorMessage(), "Should have no error message");
        assertTrue(result.getWarnings().isEmpty(), "Should have no warnings");

        // Verify biome placement
        assertNotNull(result.getBiomePlacementResult(), "Should have biome placement result");
        assertEquals(2, result.getTotalBiomes(), "Should have 2 biomes");
        assertEquals(4, result.getTotalGrids(), "Should have 4 initial grids (2 per biome)");

        // Verify filling
        assertNotNull(result.getFillResult(), "Should have fill result");
        assertTrue(result.getFilledGrids() > 4, "Should have filled grids around biomes");

        // Verify flow composition
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");
        assertEquals(1, result.getTotalFlows(), "Should have 1 flow (road)");
        assertTrue(result.getFlowCompositionResult().getTotalSegments() > 0, "Should have road segments");

        // Verify that road has RoadConfigParts on Area grids
        Road road = composition.getRoads().get(0);
        assertNotNull(road.getHexGrids(), "Road should have hexGrids");
        assertFalse(road.getHexGrids().isEmpty(), "Road should have flow grids");

        log.info("=== Composition Result ===");
        log.info("Biomes: {}", result.getTotalBiomes());
        log.info("Initial grids: {}", result.getTotalGrids());
        log.info("Filled grids: {}", result.getFilledGrids());
        log.info("Flows: {}", result.getTotalFlows());
        log.info("Flow segments: {}", result.getFlowCompositionResult().getTotalSegments());
        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testCompositionWithRiverAndFilling() {
        log.info("=== Testing Composition with River and Filling ===");

        // Create composition with biomes and river
        HexComposition composition = createCompositionWithRiver();

        // Use builder with filling enabled
        HexCompositeBuilder.CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("river-world")
            .seed(54321L)
            .fillGaps(true)
            .oceanBorderRings(2)  // More border rings
            .build()
            .compose();

        // Verify success
        assertTrue(result.isSuccess(), "Composition should succeed");

        // Verify river composition
        assertEquals(1, result.getTotalFlows(), "Should have 1 flow (river)");
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");

        // Verify filling with ocean border
        assertNotNull(result.getFillResult(), "Should have fill result");
        HexGridFillResult fillResult = result.getFillResult();
        assertTrue(fillResult.getOceanFillCount() > 0, "Should have ocean grids");
        assertTrue(fillResult.getTotalGridCount() > result.getTotalGrids(),
            "Total grids should increase after filling");

        log.info("Filled grids: Ocean={}, Land={}, Coast={}",
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());
        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testCompositionWithoutFilling() {
        log.info("=== Testing Composition without Filling ===");

        HexComposition composition = createCompositionWithRoad();

        // Disable filling
        HexCompositeBuilder.CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("no-fill-world")
            .seed(99999L)
            .fillGaps(false)  // Disable filling
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");

        // Verify no filling happened
        assertNull(result.getFillResult(), "Should have no fill result");
        assertEquals(0, result.getFilledGrids(), "Should have 0 filled grids");

        // But biomes and flows should still work
        assertEquals(2, result.getTotalBiomes(), "Should have 2 biomes");
        assertEquals(1, result.getTotalFlows(), "Should have 1 flow");

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testCompositionValidation() {
        log.info("=== Testing Composition Validation ===");

        // Test null composition
        HexCompositeBuilder.CompositionResult result1 = HexCompositeBuilder.builder()
            .composition(null)
            .worldId("test-world")
            .build()
            .compose();

        assertFalse(result1.isSuccess(), "Null composition should fail");
        assertEquals("Composition is null", result1.getErrorMessage());

        // Test null worldId
        HexComposition composition = createCompositionWithRoad();
        HexCompositeBuilder.CompositionResult result2 = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId(null)
            .build()
            .compose();

        assertFalse(result2.isSuccess(), "Null worldId should fail");
        assertEquals("WorldId is required", result2.getErrorMessage());

        // Test blank worldId
        HexCompositeBuilder.CompositionResult result3 = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("")
            .build()
            .compose();

        assertFalse(result3.isSuccess(), "Blank worldId should fail");
        assertEquals("WorldId is required", result3.getErrorMessage());

        log.info("=== Test completed successfully ===");
    }

    // ============= Helper Methods =============

    private HexComposition createCompositionWithRoad() {
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("road-test")
            .features(new ArrayList<>())
            .build();

        // Biome 1: Forest at origin
        Biome forest = Biome.builder()
            .type(BiomeType.FOREST)
            .build();
        forest.setName("forest");
        forest.setTitle("Test Forest");
        forest.setShape(AreaShape.CIRCLE);
        forest.setSize(AreaSize.SMALL);
        forest.setPositions(List.of(createOriginPosition()));

        // Biome 2: Mountains to the north
        Biome mountains = Biome.builder()
            .type(BiomeType.MOUNTAINS)
            .build();
        mountains.setName("mountains");
        mountains.setTitle("Test Mountains");
        mountains.setShape(AreaShape.CIRCLE);
        mountains.setSize(AreaSize.SMALL);
        mountains.setPositions(List.of(createNorthPosition("origin", 5, 7)));

        // Road connecting forest to mountains
        Road road = Road.builder()
            .waypointIds(new ArrayList<>())
            .roadType("cobblestone")
            .level(95)
            .build();
        road.setName("main-road");
        road.setTitle("Main Road");
        road.setStartPointId("forest");
        road.setEndPointId("mountains");
        road.setWidth(FlowWidth.MEDIUM);

        composition.getFeatures().add(forest);
        composition.getFeatures().add(mountains);
        composition.getFeatures().add(road);

        return composition;
    }

    private HexComposition createCompositionWithRiver() {
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("river-test")
            .features(new ArrayList<>())
            .build();

        // Biome 1: Plains at origin
        Biome plains = Biome.builder()
            .type(BiomeType.PLAINS)
            .build();
        plains.setName("plains");
        plains.setTitle("Test Plains");
        plains.setShape(AreaShape.CIRCLE);
        plains.setSize(AreaSize.SMALL);
        plains.setPositions(List.of(createOriginPosition()));

        // Biome 2: Swamp to the south
        Biome swamp = Biome.builder()
            .type(BiomeType.SWAMP)
            .build();
        swamp.setName("swamp");
        swamp.setTitle("Test Swamp");
        swamp.setShape(AreaShape.CIRCLE);
        swamp.setSize(AreaSize.SMALL);
        swamp.setPositions(List.of(createSouthPosition("origin", 5, 7)));

        // River from plains to swamp
        River river = River.builder()
            .waypointIds(new ArrayList<>())
            .depth(3)
            .level(50)
            .build();
        river.setName("main-river");
        river.setTitle("Main River");
        river.setStartPointId("plains");
        river.setMergeToId("swamp");
        river.setWidth(FlowWidth.MEDIUM);

        composition.getFeatures().add(plains);
        composition.getFeatures().add(swamp);
        composition.getFeatures().add(river);

        return composition;
    }

    private RelativePosition createOriginPosition() {
        RelativePosition pos = new RelativePosition();
        pos.setAnchor("origin");
        pos.setDirection(Direction.N);
        pos.setDistanceFrom(0);
        pos.setDistanceTo(0);
        pos.setPriority(10);
        return pos;
    }

    private RelativePosition createNorthPosition(String anchor, int distFrom, int distTo) {
        RelativePosition pos = new RelativePosition();
        pos.setAnchor(anchor);
        pos.setDirection(Direction.N);
        pos.setDistanceFrom(distFrom);
        pos.setDistanceTo(distTo);
        pos.setPriority(8);
        return pos;
    }

    private RelativePosition createSouthPosition(String anchor, int distFrom, int distTo) {
        RelativePosition pos = new RelativePosition();
        pos.setAnchor(anchor);
        pos.setDirection(Direction.S);
        pos.setDistanceFrom(distFrom);
        pos.setDistanceTo(distTo);
        pos.setPriority(8);
        return pos;
    }
}
