package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for direction deviation in LINE-shaped biomes.
 * Direction deviation creates more organic, natural shapes instead of perfectly straight lines.
 */
@Slf4j
public class DirectionDeviationTest {

    @Test
    public void testStraightMountainLine() {
        log.info("=== Testing Straight Mountain Line (no deviation) ===");

        // Create mountain with no deviation (perfectly straight)
        MountainBiome mountain = new MountainBiome();
        mountain.setName("straight-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(8);
        mountain.setSizeTo(10);
        mountain.setDirectionDeviation(0.0);  // No deviation
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("straight-line-test")
            .features(new ArrayList<>())
            .build();
        composition.getFeatures().add(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(12345L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");

        // Verify mountain was placed
        PlacedBiome placedMountain = result.getBiomePlacementResult().getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("straight-mountains"))
            .findFirst()
            .orElseThrow();

        log.info("Placed straight mountain with {} hexes", placedMountain.getActualSize());
        assertTrue(placedMountain.getActualSize() >= 8, "Should have at least 8 hexes");
        assertTrue(placedMountain.getActualSize() <= 10, "Should have at most 10 hexes");

        log.info("Coordinates: {}", placedMountain.getCoordinates());
        log.info("=== Straight Line Test Completed ===");
    }

    @Test
    public void testOrganicMountainLine() {
        log.info("=== Testing Organic Mountain Line (with deviation) ===");

        // Create mountain with moderate deviation (more natural)
        MountainBiome mountain = new MountainBiome();
        mountain.setName("organic-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(8);
        mountain.setSizeTo(10);
        mountain.setDirectionDeviation(0.4);  // 40% chance to deviate at each step
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("organic-line-test")
            .features(new ArrayList<>())
            .build();
        composition.getFeatures().add(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(54321L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");

        PlacedBiome placedMountain = result.getBiomePlacementResult().getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("organic-mountains"))
            .findFirst()
            .orElseThrow();

        log.info("Placed organic mountain with {} hexes", placedMountain.getActualSize());
        assertTrue(placedMountain.getActualSize() >= 8, "Should have at least 8 hexes");

        log.info("Coordinates: {}", placedMountain.getCoordinates());
        log.info("=== Organic Line Test Completed ===");
    }

    @Test
    public void testAsymmetricDeviation() {
        log.info("=== Testing Asymmetric Deviation (prefer left turns) ===");

        // Create mountain that prefers turning left
        MountainBiome mountain = new MountainBiome();
        mountain.setName("left-turning-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.MEDIUM_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(10);
        mountain.setSizeTo(12);
        mountain.setDeviationLeft(0.5);   // 50% chance to turn left
        mountain.setDeviationRight(0.1);  // 10% chance to turn right
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("asymmetric-test")
            .features(new ArrayList<>())
            .build();
        composition.getFeatures().add(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(99999L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");

        PlacedBiome placedMountain = result.getBiomePlacementResult().getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("left-turning-mountains"))
            .findFirst()
            .orElseThrow();

        log.info("Placed left-turning mountain with {} hexes", placedMountain.getActualSize());
        log.info("Coordinates: {}", placedMountain.getCoordinates());
        log.info("=== Asymmetric Deviation Test Completed ===");
    }

    @Test
    public void testHighDeviation() {
        log.info("=== Testing High Deviation (very wiggly) ===");

        // Create mountain with high deviation (very wiggly)
        MountainBiome mountain = new MountainBiome();
        mountain.setName("wiggly-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.LOW_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(12);
        mountain.setSizeTo(15);
        mountain.setDirectionDeviation(0.8);  // 80% chance to deviate
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("wiggly-test")
            .features(new ArrayList<>())
            .build();
        composition.getFeatures().add(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(77777L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");

        PlacedBiome placedMountain = result.getBiomePlacementResult().getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("wiggly-mountains"))
            .findFirst()
            .orElseThrow();

        log.info("Placed wiggly mountain with {} hexes", placedMountain.getActualSize());
        log.info("Coordinates: {}", placedMountain.getCoordinates());
        log.info("=== High Deviation Test Completed ===");
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
}
