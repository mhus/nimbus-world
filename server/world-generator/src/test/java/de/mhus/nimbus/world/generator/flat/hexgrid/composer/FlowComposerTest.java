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
 * Tests for FlowComposer integration with BiomeComposer and HexGridGenerator
 */
@Slf4j
public class FlowComposerTest {

    private WHexGridRepository mockRepository;
    private HexGridGenerator hexGridGenerator;
    private FlowComposer flowComposer;
    private BiomeComposer biomeComposer;

    @BeforeEach
    public void setup() {
        // Mock repository
        mockRepository = Mockito.mock(WHexGridRepository.class);

        // Mock repository to return empty for all lookups (simulate no existing grids)
        when(mockRepository.findByWorldIdAndPosition(anyString(), anyString()))
            .thenReturn(Optional.empty());

        // Mock saveAll to return what was passed in
        when(mockRepository.saveAll(any())).thenAnswer(invocation -> {
            List<WHexGrid> grids = invocation.getArgument(0);
            log.info("Mock repository saved {} grids", grids.size());
            return grids;
        });

        hexGridGenerator = new HexGridGenerator(mockRepository);
        flowComposer = new FlowComposer();
        biomeComposer = new BiomeComposer();
    }

    @Test
    public void testSimpleRoadBetweenBiomes() {
        log.info("=== Testing Simple Road Between Biomes ===");

        // 1. Create HexComposition with two biomes and a road
        HexComposition composition = createCompositionWithRoad();
        composition.initialize();

        // 2. Prepare composition
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        assertTrue(preparer.prepare(composition), "Preparation should succeed");

        log.info("Prepared composition with {} biomes and {} roads",
            composition.getBiomes().size(), composition.getRoads().size());

        // 3. Compose biomes
        BiomePlacementResult biomeResult = biomeComposer.compose(
            composition, "test-world", 12345L);

        assertTrue(biomeResult.isSuccess(), "Biome composition should succeed");
        assertEquals(2, biomeResult.getPlacedBiomes().size());

        log.info("Placed {} biomes with {} total hexGrids",
            biomeResult.getPlacedBiomes().size(),
            biomeResult.getHexGrids().size());

        // 4. Compose flows (roads)
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomeResult);

        log.info("Flow composition result: success={}, composed={}/{}, segments={}",
            flowResult.isSuccess(),
            flowResult.getComposedFlows(),
            flowResult.getTotalFlows(),
            flowResult.getTotalSegments());

        // Verify flow composition
        assertEquals(1, flowResult.getTotalFlows(), "Should have 1 flow");
        assertTrue(flowResult.getTotalSegments() > 0, "Should have created segments");

        // 5. Check that FeatureHexGrids have flow segments
        Road road = composition.getRoads().get(0);
        List<FeatureHexGrid> roadHexGrids = road.getHexGrids();

        assertNotNull(roadHexGrids, "Road should have hexGrids");
        assertFalse(roadHexGrids.isEmpty(), "Road hexGrids should not be empty");

        log.info("Road '{}' has {} hexGrids with flow segments",
            road.getName(), roadHexGrids.size());

        // Verify flow segments
        int totalSegments = 0;
        for (FeatureHexGrid hexGrid : roadHexGrids) {
            if (hexGrid.hasFlowSegments()) {
                totalSegments += hexGrid.getFlowSegments().size();
                log.debug("HexGrid at {} has {} flow segments",
                    hexGrid.getCoordinate(),
                    hexGrid.getFlowSegments().size());
            }
        }

        assertTrue(totalSegments > 0, "Should have flow segments in hexGrids");

        // 6. Generate WHexGrids
        HexGridGenerator.GenerationResult genResult = hexGridGenerator.generateHexGrids(composition);

        assertTrue(genResult.isSuccess(), "HexGrid generation should succeed");
        assertTrue(genResult.getCreatedGrids() > 0, "Should have created grids");

        log.info("Generated {} WHexGrids from {} features",
            genResult.getCreatedGrids(),
            genResult.getTotalFeatures());

        // Verify feature status
        assertEquals(FeatureStatus.CREATED, road.getStatus(),
            "Road should have CREATED status");

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testRiverBetweenBiomes() {
        log.info("=== Testing River Between Biomes ===");

        // Create composition with river
        HexComposition composition = createCompositionWithRiver();
        composition.initialize();

        // Prepare
        HexCompositionPreparer preparer = new HexCompositionPreparer();
        assertTrue(preparer.prepare(composition), "Preparation should succeed");

        // Compose biomes
        BiomePlacementResult biomeResult = biomeComposer.compose(
            composition, "test-world", 54321L);
        assertTrue(biomeResult.isSuccess());

        // Compose flows
        FlowComposer.FlowCompositionResult flowResult = flowComposer.composeFlows(
            composition, biomeResult);

        log.info("River composition: success={}, segments={}",
            flowResult.isSuccess(), flowResult.getTotalSegments());

        // Verify river has flow segments
        River river = composition.getRivers().get(0);
        assertNotNull(river.getHexGrids());
        assertFalse(river.getHexGrids().isEmpty());

        log.info("River '{}' has {} hexGrids", river.getName(), river.getHexGrids().size());

        // Generate WHexGrids
        HexGridGenerator.GenerationResult genResult = hexGridGenerator.generateHexGrids(composition);
        assertTrue(genResult.isSuccess());

        log.info("=== River test completed successfully ===");
    }

    /**
     * Creates a composition with two biomes and a road connecting them
     */
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

    /**
     * Creates a composition with two biomes and a river
     */
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
