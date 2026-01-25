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
    }

    @Test
    public void testSimpleRoadBetweenBiomes() {
        log.info("=== Testing Simple Road Between Biomes ===");

        // Create HexComposition with two biomes and a road
        HexComposition composition = createCompositionWithRoad();

        // Use HexCompositeBuilder to orchestrate the complete pipeline
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(12345L)
            .fillGaps(false)  // No gap filling for this test
            .repository(mockRepository)
            .generateWHexGrids(true)  // Generate WHexGrids in this test
            .build()
            .compose();

        // Verify composition success
        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNull(result.getErrorMessage(), "Should have no error message");

        // Verify biome placement
        assertEquals(2, result.getTotalBiomes(), "Should have 2 biomes");
        assertEquals(4, result.getTotalGrids(), "Should have 4 grids");

        // Verify flow composition
        assertEquals(1, result.getTotalFlows(), "Should have 1 flow");
        assertTrue(result.getFlowCompositionResult().getTotalSegments() > 0,
            "Should have created segments");

        log.info("Flow composition result: success={}, composed={}/{}, segments={}",
            result.getFlowCompositionResult().isSuccess(),
            result.getFlowCompositionResult().getComposedFlows(),
            result.getFlowCompositionResult().getTotalFlows(),
            result.getFlowCompositionResult().getTotalSegments());

        // Check that FeatureHexGrids have flow segments
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

        // Verify WHexGrid generation
        assertNotNull(result.getGenerationResult(), "Should have generation result");
        assertTrue(result.getGenerationResult().isSuccess(), "HexGrid generation should succeed");
        assertTrue(result.getGeneratedWHexGrids() > 0, "Should have created grids");

        log.info("Generated {} WHexGrids from {} features",
            result.getGeneratedWHexGrids(),
            result.getGenerationResult().getTotalFeatures());

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

        // Use HexCompositeBuilder to orchestrate the complete pipeline
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(54321L)
            .fillGaps(false)  // No gap filling for this test
            .repository(mockRepository)
            .generateWHexGrids(true)  // Generate WHexGrids in this test
            .build()
            .compose();

        // Verify composition success
        assertTrue(result.isSuccess(), "Composition should succeed");

        log.info("River composition: success={}, segments={}",
            result.getFlowCompositionResult().isSuccess(),
            result.getFlowCompositionResult().getTotalSegments());

        // Verify river has flow segments
        River river = composition.getRivers().get(0);
        assertNotNull(river.getHexGrids());
        assertFalse(river.getHexGrids().isEmpty());

        log.info("River '{}' has {} hexGrids", river.getName(), river.getHexGrids().size());

        // Verify WHexGrid generation
        assertTrue(result.getGenerationResult().isSuccess(), "HexGrid generation should succeed");

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
