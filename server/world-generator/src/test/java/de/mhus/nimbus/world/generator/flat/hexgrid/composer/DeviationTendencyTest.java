package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeviationTendency enum in LINE-shaped biomes.
 * Tests the user-friendly enum-based configuration instead of numeric values.
 */
@Slf4j
public class DeviationTendencyTest {

    @Test
    public void testTendencyNone() {
        log.info("=== Testing NONE Tendency (straight line) ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("straight-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(8);
        mountain.setSizeTo(10);
        mountain.setTendLeft(DeviationTendency.NONE);
        mountain.setTendRight(DeviationTendency.NONE);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = createComposition(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(12345L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess());

        PlacedBiome placed = getPlacedMountain(result, "straight-mountains");
        log.info("NONE: Placed with {} hexes", placed.getActualSize());
        log.info("NONE: Coordinates: {}", placed.getCoordinates());

        assertEquals(0.0, mountain.getEffectiveDeviationLeft());
        assertEquals(0.0, mountain.getEffectiveDeviationRight());

        log.info("=== NONE Tendency Test Completed ===");
    }

    @Test
    public void testTendencySlight() {
        log.info("=== Testing SLIGHT Tendency (subtle curves) ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("slight-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.MEDIUM_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(10);
        mountain.setSizeTo(12);
        mountain.setTendLeft(DeviationTendency.SLIGHT);
        mountain.setTendRight(DeviationTendency.SLIGHT);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = createComposition(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(22222L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess());

        PlacedBiome placed = getPlacedMountain(result, "slight-mountains");
        log.info("SLIGHT: Placed with {} hexes", placed.getActualSize());
        log.info("SLIGHT: Coordinates: {}", placed.getCoordinates());

        assertEquals(0.2, mountain.getEffectiveDeviationLeft(), 0.01);
        assertEquals(0.2, mountain.getEffectiveDeviationRight(), 0.01);

        log.info("=== SLIGHT Tendency Test Completed ===");
    }

    @Test
    public void testTendencyModerate() {
        log.info("=== Testing MODERATE Tendency (natural curves) ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("moderate-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.LOW_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(10);
        mountain.setSizeTo(12);
        mountain.setTendLeft(DeviationTendency.MODERATE);
        mountain.setTendRight(DeviationTendency.MODERATE);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = createComposition(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(33333L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess());

        PlacedBiome placed = getPlacedMountain(result, "moderate-mountains");
        log.info("MODERATE: Placed with {} hexes", placed.getActualSize());
        log.info("MODERATE: Coordinates: {}", placed.getCoordinates());

        assertEquals(0.4, mountain.getEffectiveDeviationLeft(), 0.01);
        assertEquals(0.4, mountain.getEffectiveDeviationRight(), 0.01);

        log.info("=== MODERATE Tendency Test Completed ===");
    }

    @Test
    public void testTendencyStrong() {
        log.info("=== Testing STRONG Tendency (pronounced curves) ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("strong-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.MEADOW);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(12);
        mountain.setSizeTo(15);
        mountain.setTendLeft(DeviationTendency.STRONG);
        mountain.setTendRight(DeviationTendency.STRONG);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = createComposition(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(44444L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess());

        PlacedBiome placed = getPlacedMountain(result, "strong-mountains");
        log.info("STRONG: Placed with {} hexes", placed.getActualSize());
        log.info("STRONG: Coordinates: {}", placed.getCoordinates());

        assertEquals(0.6, mountain.getEffectiveDeviationLeft(), 0.01);
        assertEquals(0.6, mountain.getEffectiveDeviationRight(), 0.01);

        log.info("=== STRONG Tendency Test Completed ===");
    }

    @Test
    public void testAsymmetricTendency() {
        log.info("=== Testing Asymmetric Tendency (prefer left) ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("asymmetric-mountains");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(10);
        mountain.setSizeTo(12);
        mountain.setTendLeft(DeviationTendency.STRONG);   // 60% left
        mountain.setTendRight(DeviationTendency.SLIGHT);  // 20% right
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = createComposition(mountain);

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(55555L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess());

        PlacedBiome placed = getPlacedMountain(result, "asymmetric-mountains");
        log.info("ASYMMETRIC: Placed with {} hexes", placed.getActualSize());
        log.info("ASYMMETRIC: Coordinates: {}", placed.getCoordinates());

        assertEquals(0.6, mountain.getEffectiveDeviationLeft(), 0.01);
        assertEquals(0.2, mountain.getEffectiveDeviationRight(), 0.01);

        log.info("=== Asymmetric Tendency Test Completed ===");
    }

    @Test
    public void testBackwardCompatibility() {
        log.info("=== Testing Backward Compatibility with deprecated parameters ===");

        // Test with deprecated directionDeviation
        MountainBiome mountain1 = new MountainBiome();
        mountain1.setDirectionDeviation(0.8);
        assertEquals(0.4, mountain1.getEffectiveDeviationLeft(), 0.01, "directionDeviation should split 50/50");
        assertEquals(0.4, mountain1.getEffectiveDeviationRight(), 0.01, "directionDeviation should split 50/50");

        // Test with deprecated deviationLeft/Right
        MountainBiome mountain2 = new MountainBiome();
        mountain2.setDeviationLeft(0.5);
        mountain2.setDeviationRight(0.3);
        assertEquals(0.5, mountain2.getEffectiveDeviationLeft(), 0.01);
        assertEquals(0.3, mountain2.getEffectiveDeviationRight(), 0.01);

        // Test priority: tendLeft/Right should override deprecated values
        MountainBiome mountain3 = new MountainBiome();
        mountain3.setTendLeft(DeviationTendency.MODERATE);
        mountain3.setDeviationLeft(0.9);  // Should be ignored
        mountain3.setDirectionDeviation(0.8);  // Should be ignored
        assertEquals(0.4, mountain3.getEffectiveDeviationLeft(), 0.01, "tendLeft should have priority");

        log.info("=== Backward Compatibility Test Completed ===");
    }

    private HexComposition createComposition(MountainBiome mountain) {
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("tendency-test")
            .features(new ArrayList<>())
            .build();
        composition.getFeatures().add(mountain);
        return composition;
    }

    private PlacedBiome getPlacedMountain(CompositionResult result, String name) {
        return result.getBiomePlacementResult().getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals(name))
            .findFirst()
            .orElseThrow();
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
