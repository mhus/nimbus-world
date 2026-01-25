package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.generator.flat.*;
import de.mhus.nimbus.world.generator.flat.hexgrid.*;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MountainBiome with different height configurations
 */
@Slf4j
public class MountainBiomeTest {

    @Test
    public void testHighPeaksMountain() {
        log.info("=== Testing HIGH_PEAKS Mountain ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("high-peaks");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);

        mountain.applyDefaults();

        assertNotNull(mountain.getParameters(), "Parameters should be set");
        assertEquals("150", mountain.getParameters().get("landLevel"),
            "HIGH_PEAKS should have landLevel=150");
        assertEquals("40", mountain.getParameters().get("landOffset"),
            "HIGH_PEAKS should have landOffset=40");

        // Should also have base parameters from BiomeType
        assertEquals("30", mountain.getParameters().get("g_offset"),
            "Should have g_offset from BiomeType");
        assertEquals("0.8", mountain.getParameters().get("g_roughness"),
            "Should have g_roughness from BiomeType");

        log.info("HIGH_PEAKS: landLevel={}, landOffset={}, max height=~240",
            mountain.getParameters().get("landLevel"),
            mountain.getParameters().get("landOffset"));

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testMediumPeaksMountain() {
        log.info("=== Testing MEDIUM_PEAKS Mountain ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("medium-peaks");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.MEDIUM_PEAKS);

        mountain.applyDefaults();

        assertEquals("120", mountain.getParameters().get("landLevel"),
            "MEDIUM_PEAKS should have landLevel=120");
        assertEquals("30", mountain.getParameters().get("landOffset"),
            "MEDIUM_PEAKS should have landOffset=30");

        log.info("MEDIUM_PEAKS: landLevel={}, landOffset={}, max height=~200",
            mountain.getParameters().get("landLevel"),
            mountain.getParameters().get("landOffset"));

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testLowPeaksMountain() {
        log.info("=== Testing LOW_PEAKS Mountain ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("low-peaks");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.LOW_PEAKS);

        mountain.applyDefaults();

        assertEquals("100", mountain.getParameters().get("landLevel"),
            "LOW_PEAKS should have landLevel=100");
        assertEquals("20", mountain.getParameters().get("landOffset"),
            "LOW_PEAKS should have landOffset=20");

        log.info("LOW_PEAKS: landLevel={}, landOffset={}, max height=~170",
            mountain.getParameters().get("landLevel"),
            mountain.getParameters().get("landOffset"));

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testMeadowMountain() {
        log.info("=== Testing MEADOW Mountain ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("meadow");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.MEADOW);

        mountain.applyDefaults();

        assertEquals("80", mountain.getParameters().get("landLevel"),
            "MEADOW should have landLevel=80");
        assertEquals("10", mountain.getParameters().get("landOffset"),
            "MEADOW should have landOffset=10");

        log.info("MEADOW: landLevel={}, landOffset={}, max height=~140",
            mountain.getParameters().get("landLevel"),
            mountain.getParameters().get("landOffset"));

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testMountainDefaultHeight() {
        log.info("=== Testing Mountain with default height (no height specified) ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("default-mountain");
        mountain.setType(BiomeType.MOUNTAINS);
        // DON'T set height - should default to MEDIUM_PEAKS

        mountain.applyDefaults();

        assertNotNull(mountain.getHeight(), "Height should be defaulted");
        assertEquals(MountainBiome.MountainHeight.MEDIUM_PEAKS, mountain.getHeight(),
            "Should default to MEDIUM_PEAKS");

        assertEquals("120", mountain.getParameters().get("landLevel"),
            "Default should have MEDIUM_PEAKS landLevel=120");
        assertEquals("30", mountain.getParameters().get("landOffset"),
            "Default should have MEDIUM_PEAKS landOffset=30");

        log.info("Default mountain uses MEDIUM_PEAKS");
        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testMountainInComposition() {
        log.info("=== Testing Mountain in HexComposition ===");

        // Create composition with HIGH_PEAKS mountain
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("mountain-test")
            .features(new ArrayList<>())
            .build();

        MountainBiome highPeaks = new MountainBiome();
        highPeaks.setName("high-peaks");
        highPeaks.setType(BiomeType.MOUNTAINS);
        highPeaks.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        highPeaks.setShape(AreaShape.CIRCLE);
        highPeaks.setSize(AreaSize.SMALL);
        highPeaks.setPositions(java.util.List.of(createOriginPosition()));

        composition.getFeatures().add(highPeaks);

        // Initialize should apply defaults
        composition.initialize();

        // Verify parameters were applied
        assertNotNull(highPeaks.getParameters(), "Parameters should be set after initialize");
        assertEquals("150", highPeaks.getParameters().get("landLevel"),
            "HIGH_PEAKS should have landLevel=150 after initialize");

        log.info("Mountain in composition initialized correctly with landLevel={}",
            highPeaks.getParameters().get("landLevel"));

        log.info("=== Test completed successfully ===");
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

    @Test
    public void testHighPeaksMountainTerrainGeneration() throws Exception {
        log.info("=== Testing HIGH_PEAKS Mountain Terrain Generation ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("high-peaks-terrain-test");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        mountain.setShape(AreaShape.CIRCLE);
        mountain.setSize(AreaSize.SMALL);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        // Create composition
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("high-peaks-test")
            .features(new ArrayList<>())
            .build();
        composition.getFeatures().add(mountain);

        // Compose with HexCompositeBuilder
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(12345L)
            .fillGaps(false)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");

        // Get biome placement result
        BiomePlacementResult placementResult = result.getBiomePlacementResult();
        assertNotNull(placementResult, "Should have placement result");

        // Find the mountain biome
        PlacedBiome placedMountain = placementResult.getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("high-peaks-terrain-test"))
            .findFirst()
            .orElseThrow();

        // Get center coordinate
        de.mhus.nimbus.generated.types.HexVector2 center = placedMountain.getCenter();

        // Find the WHexGrid for center
        de.mhus.nimbus.world.shared.world.WHexGrid hexGrid = placementResult.getHexGrids().stream()
            .filter(g -> g.getPosition().equals(center.getQ() + ":" + center.getR()))
            .findFirst()
            .orElseThrow();

        // Create FilledHexGrid wrapper
        FilledHexGrid centerGrid = FilledHexGrid.builder()
            .coordinate(center)
            .hexGrid(hexGrid)
            .biome(placedMountain)
            .isFiller(false)
            .build();

        WFlat flat = buildAndVerifyMountainTerrain(
            centerGrid,
            MountainBiome.MountainHeight.HIGH_PEAKS,
            50, // oceanLevel
            10  // tolerance
        );

        assertNotNull(flat, "Flat should be generated");
        log.info("=== HIGH_PEAKS Terrain Test Completed ===");
    }

    @Test
    public void testMediumPeaksMountainTerrainGeneration() throws Exception {
        log.info("=== Testing MEDIUM_PEAKS Mountain Terrain Generation ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("medium-peaks-terrain-test");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.MEDIUM_PEAKS);
        mountain.setShape(AreaShape.CIRCLE);
        mountain.setSize(AreaSize.SMALL);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("medium-peaks-test")
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

        assertTrue(result.isSuccess());

        BiomePlacementResult placementResult = result.getBiomePlacementResult();
        PlacedBiome placedMountain = placementResult.getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("medium-peaks-terrain-test"))
            .findFirst()
            .orElseThrow();

        de.mhus.nimbus.generated.types.HexVector2 center = placedMountain.getCenter();
        de.mhus.nimbus.world.shared.world.WHexGrid hexGrid = placementResult.getHexGrids().stream()
            .filter(g -> g.getPosition().equals(center.getQ() + ":" + center.getR()))
            .findFirst()
            .orElseThrow();

        FilledHexGrid centerGrid = FilledHexGrid.builder()
            .coordinate(center)
            .hexGrid(hexGrid)
            .biome(placedMountain)
            .isFiller(false)
            .build();

        buildAndVerifyMountainTerrain(centerGrid, MountainBiome.MountainHeight.MEDIUM_PEAKS, 50, 10);

        log.info("=== MEDIUM_PEAKS Terrain Test Completed ===");
    }

    @Test
    public void testLowPeaksMountainTerrainGeneration() throws Exception {
        log.info("=== Testing LOW_PEAKS Mountain Terrain Generation ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("low-peaks-terrain-test");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.LOW_PEAKS);
        mountain.setShape(AreaShape.CIRCLE);
        mountain.setSize(AreaSize.SMALL);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("low-peaks-test")
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

        assertTrue(result.isSuccess());

        BiomePlacementResult placementResult = result.getBiomePlacementResult();
        PlacedBiome placedMountain = placementResult.getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("low-peaks-terrain-test"))
            .findFirst()
            .orElseThrow();

        de.mhus.nimbus.generated.types.HexVector2 center = placedMountain.getCenter();
        de.mhus.nimbus.world.shared.world.WHexGrid hexGrid = placementResult.getHexGrids().stream()
            .filter(g -> g.getPosition().equals(center.getQ() + ":" + center.getR()))
            .findFirst()
            .orElseThrow();

        FilledHexGrid centerGrid = FilledHexGrid.builder()
            .coordinate(center)
            .hexGrid(hexGrid)
            .biome(placedMountain)
            .isFiller(false)
            .build();

        buildAndVerifyMountainTerrain(centerGrid, MountainBiome.MountainHeight.LOW_PEAKS, 50, 10);

        log.info("=== LOW_PEAKS Terrain Test Completed ===");
    }

    @Test
    public void testMeadowMountainTerrainGeneration() throws Exception {
        log.info("=== Testing MEADOW Mountain Terrain Generation ===");

        MountainBiome mountain = new MountainBiome();
        mountain.setName("meadow-terrain-test");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.MEADOW);
        mountain.setShape(AreaShape.CIRCLE);
        mountain.setSize(AreaSize.SMALL);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("meadow-test")
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

        assertTrue(result.isSuccess());

        BiomePlacementResult placementResult = result.getBiomePlacementResult();
        PlacedBiome placedMountain = placementResult.getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("meadow-terrain-test"))
            .findFirst()
            .orElseThrow();

        de.mhus.nimbus.generated.types.HexVector2 center = placedMountain.getCenter();
        de.mhus.nimbus.world.shared.world.WHexGrid hexGrid = placementResult.getHexGrids().stream()
            .filter(g -> g.getPosition().equals(center.getQ() + ":" + center.getR()))
            .findFirst()
            .orElseThrow();

        FilledHexGrid centerGrid = FilledHexGrid.builder()
            .coordinate(center)
            .hexGrid(hexGrid)
            .biome(placedMountain)
            .isFiller(false)
            .build();

        buildAndVerifyMountainTerrain(centerGrid, MountainBiome.MountainHeight.MEADOW, 50, 10);

        log.info("=== MEADOW Terrain Test Completed ===");
    }

    /**
     * Builds terrain for a mountain grid and verifies the height levels are within expected range.
     *
     * The terrain generator uses:
     * - baseHeight = oceanLevel + landLevel
     * - hillHeight = landOffset
     * - Result: baseHeight ± hillHeight
     *
     * So for HIGH_PEAKS (landLevel=150, landOffset=40, oceanLevel=50):
     * - baseHeight = 200
     * - hillHeight = 40
     * - Expected range: 160-240
     */
    private de.mhus.nimbus.world.shared.generator.WFlat buildAndVerifyMountainTerrain(
            FilledHexGrid filled,
            MountainBiome.MountainHeight height,
            int oceanLevel,
            int tolerance) {

        // Calculate expected min/max levels based on how HillyTerrainManipulator works
        // Min = baseHeight - hillHeight = (oceanLevel + landLevel) - landOffset
        // Max = baseHeight + hillHeight = (oceanLevel + landLevel) + landOffset
        int expectedMinLevel = oceanLevel + height.getLandLevel() - height.getLandOffset() - tolerance;
        int expectedMaxLevel = oceanLevel + height.getLandLevel() + height.getLandOffset() + tolerance;

        log.info("Expected terrain levels for {}: min={}, max={} (oceanLevel={}, landLevel={}, landOffset={}, tolerance={})",
            height, expectedMinLevel, expectedMaxLevel, oceanLevel, height.getLandLevel(), height.getLandOffset(), tolerance);

        // Create flat with initialized arrays
        int flatSize = 512;
        byte[] levels = new byte[flatSize * flatSize];
        byte[] columns = new byte[flatSize * flatSize];

        for (int i = 0; i < levels.length; i++) {
            levels[i] = (byte) 70; // Base mountain level
            columns[i] = 0;
        }

        de.mhus.nimbus.world.shared.generator.WFlat flat = de.mhus.nimbus.world.shared.generator.WFlat.builder()
            .flatId("flat-test")
            .worldId("test-world")
            .layerDataId("test-layer")
            .hexGrid(filled.getCoordinate())
            .sizeX(flatSize)
            .sizeZ(flatSize)
            .oceanLevel(oceanLevel)
            .mountX(flatSize / 2)
            .mountZ(flatSize / 2)
            .levels(levels)
            .columns(columns)
            .extraBlocks(new java.util.HashMap<>())
            .materials(new java.util.HashMap<>())
            .unknownProtected(false)
            .borderProtected(false)
            .build();

        // Build terrain using builder pipeline
        try {
            de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilderService builderService =
                new de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilderService();

            java.util.List<de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilder> pipeline =
                builderService.createBuilderPipeline(filled.getHexGrid());

            de.mhus.nimbus.world.generator.flat.FlatManipulatorService manipulatorService =
                new de.mhus.nimbus.world.generator.flat.FlatManipulatorService(
                    java.util.List.of(
                        new de.mhus.nimbus.world.generator.flat.HillyTerrainManipulator(),
                        new de.mhus.nimbus.world.generator.flat.NormalTerrainManipulator(),
                        new de.mhus.nimbus.world.generator.flat.FlatTerrainManipulator(),
                        new de.mhus.nimbus.world.generator.flat.SoftenManipulator(),
                        new de.mhus.nimbus.world.generator.flat.BorderSmoothManipulator(),
                        new de.mhus.nimbus.world.generator.flat.IslandsManipulator()
                    )
                );

            de.mhus.nimbus.world.shared.world.WWorld world = new de.mhus.nimbus.world.shared.world.WWorld();
            world.setOceanLevel(oceanLevel);

            de.mhus.nimbus.world.generator.flat.hexgrid.BuilderContext context =
                de.mhus.nimbus.world.generator.flat.hexgrid.BuilderContext.builder()
                    .flat(flat)
                    .hexGrid(filled.getHexGrid())
                    .world(world)
                    .neighborGrids(new java.util.HashMap<>())
                    .manipulatorService(manipulatorService)
                    .chunkService(null)
                    .build();

            // Execute pipeline
            for (de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilder builder : pipeline) {
                builder.setContext(context);
                builder.buildFlat();
            }

            // Analyze generated terrain levels
            int minLevel = Integer.MAX_VALUE;
            int maxLevel = Integer.MIN_VALUE;
            int countBelowMin = 0;
            int countAboveMax = 0;

            for (byte level : flat.getLevels()) {
                int intLevel = Byte.toUnsignedInt(level);
                minLevel = Math.min(minLevel, intLevel);
                maxLevel = Math.max(maxLevel, intLevel);

                if (intLevel < expectedMinLevel) {
                    countBelowMin++;
                }
                if (intLevel > expectedMaxLevel) {
                    countAboveMax++;
                }
            }

            log.info("Generated terrain statistics for {}:", height);
            log.info("  Actual min level: {}", minLevel);
            log.info("  Actual max level: {}", maxLevel);
            log.info("  Pixels below min ({}): {} ({} %)", expectedMinLevel, countBelowMin,
                (countBelowMin * 100.0) / flat.getLevels().length);
            log.info("  Pixels above max ({}): {} ({} %)", expectedMaxLevel, countAboveMax,
                (countAboveMax * 100.0) / flat.getLevels().length);

            // Assertions - allow reasonable variance from noise and edge blending
            // Min/Max should be within ±10 blocks of expected
            assertTrue(minLevel >= expectedMinLevel - 10,
                String.format("%s: Minimum level %d is too far below expected minimum %d",
                    height, minLevel, expectedMinLevel));
            assertTrue(maxLevel <= expectedMaxLevel + 10,
                String.format("%s: Maximum level %d is too far above expected maximum %d",
                    height, maxLevel, expectedMaxLevel));

            // Allow up to 40% of pixels outside expected range (due to terrain noise and edge effects)
            double percentOutliers = (countBelowMin + countAboveMax) * 100.0 / flat.getLevels().length;
            assertTrue(percentOutliers < 40.0,
                String.format("%s: Too many outliers (%.2f%%), should be less than 40%%",
                    height, percentOutliers));

        } catch (Exception e) {
            log.error("Failed to build terrain", e);
            fail("Terrain generation failed: " + e.getMessage());
        }

        return flat;
    }
}
