package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertEquals("150", mountain.getParameters().get("g_asl"),
            "HIGH_PEAKS should have landLevel=150");
        assertEquals("40", mountain.getParameters().get("g_offset"),
            "HIGH_PEAKS should have landOffset=40");

        // Should also have base parameters from BiomeType
        assertEquals("30", mountain.getParameters().get("g_offset"),
            "Should have g_offset from BiomeType");
        assertEquals("0.8", mountain.getParameters().get("g_roughness"),
            "Should have g_roughness from BiomeType");

        log.info("HIGH_PEAKS: landLevel={}, landOffset={}, max height=~240",
            mountain.getParameters().get("g_asl"),
            mountain.getParameters().get("g_offset"));

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

        assertEquals("120", mountain.getParameters().get("g_asl"),
            "MEDIUM_PEAKS should have landLevel=120");
        assertEquals("30", mountain.getParameters().get("g_offset"),
            "MEDIUM_PEAKS should have landOffset=30");

        log.info("MEDIUM_PEAKS: landLevel={}, landOffset={}, max height=~200",
            mountain.getParameters().get("g_asl"),
            mountain.getParameters().get("g_offset"));

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

        assertEquals("100", mountain.getParameters().get("g_asl"),
            "LOW_PEAKS should have landLevel=100");
        assertEquals("20", mountain.getParameters().get("g_offset"),
            "LOW_PEAKS should have landOffset=20");

        log.info("LOW_PEAKS: landLevel={}, landOffset={}, max height=~170",
            mountain.getParameters().get("g_asl"),
            mountain.getParameters().get("g_offset"));

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

        assertEquals("80", mountain.getParameters().get("g_asl"),
            "MEADOW should have landLevel=80");
        assertEquals("10", mountain.getParameters().get("g_offset"),
            "MEADOW should have landOffset=10");

        log.info("MEADOW: landLevel={}, landOffset={}, max height=~140",
            mountain.getParameters().get("g_asl"),
            mountain.getParameters().get("g_offset"));

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

        assertEquals("120", mountain.getParameters().get("g_asl"),
            "Default should have MEDIUM_PEAKS landLevel=120");
        assertEquals("30", mountain.getParameters().get("g_offset"),
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
        assertEquals("150", highPeaks.getParameters().get("g_asl"),
            "HIGH_PEAKS should have landLevel=150 after initialize");

        log.info("Mountain in composition initialized correctly with landLevel={}",
            highPeaks.getParameters().get("g_asl"));

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

    @Test
    public void testMountainRidgeConfiguration() throws Exception {
        log.info("=== Testing Mountain Ridge Configuration ===");

        // Create a LINE-shaped mountain to ensure connected grids
        MountainBiome mountain = new MountainBiome();
        mountain.setName("ridge-test-mountain");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        mountain.setShape(AreaShape.LINE);
        mountain.setSizeFrom(5);
        mountain.setSizeTo(5);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        // Create composition
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("ridge-test")
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

        // Find the mountain biome in the result
        PlacedBiome placedMountain = result.getBiomePlacementResult().getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getName().equals("ridge-test-mountain"))
            .findFirst()
            .orElseThrow();

        // Get the MountainBiome instance
        MountainBiome placedMountainBiome = (MountainBiome) placedMountain.getBiome();

        // Verify ridge configuration was applied
        List<FeatureHexGrid> hexGrids = placedMountainBiome.getHexGrids();
        assertNotNull(hexGrids, "HexGrids should be configured");
        assertTrue(hexGrids.size() >= 3, "Should have at least 3 grids for LINE shape");

        log.info("Mountain has {} grids", hexGrids.size());

        // Expected ridge level: landLevel + landOffset + ridgeOffset
        // HIGH_PEAKS: 150 + 40 + 20 = 210 (relative level, without oceanLevel)
        int expectedRidgeLevel = 150 + 40 + 20;

        // Count grids with ridge configuration
        int gridsWithRidge = 0;
        int totalRidgeEntries = 0;

        for (FeatureHexGrid hexGrid : hexGrids) {
            String ridgeParam = hexGrid.getParameters().get("ridge");

            if (ridgeParam != null && !ridgeParam.isEmpty()) {
                gridsWithRidge++;

                // Parse ridge JSON
                ObjectMapper mapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                List<java.util.Map<String, Object>> ridgeEntries =
                    mapper.readValue(ridgeParam, List.class);

                assertNotNull(ridgeEntries, "Ridge should be a list");
                assertFalse(ridgeEntries.isEmpty(), "Ridge list should not be empty");

                totalRidgeEntries += ridgeEntries.size();

                log.info("Grid {} has {} ridge entries: {}",
                    hexGrid.getCoordinate().getQ() + ":" + hexGrid.getCoordinate().getR(),
                    ridgeEntries.size(),
                    ridgeParam);

                // Verify each ridge entry has side and level
                for (java.util.Map<String, Object> entry : ridgeEntries) {
                    assertTrue(entry.containsKey("side"), "Ridge entry should have 'side'");
                    assertTrue(entry.containsKey("level"), "Ridge entry should have 'level'");

                    String side = (String) entry.get("side");
                    int level = ((Number) entry.get("level")).intValue();

                    assertNotNull(side, "Side should not be null");
                    assertEquals(expectedRidgeLevel, level,
                        "Ridge level should be " + expectedRidgeLevel);

                    // Verify side is a valid SIDE value
                    assertTrue(
                        side.equals("NORTH_EAST") || side.equals("EAST") ||
                        side.equals("SOUTH_EAST") || side.equals("SOUTH_WEST") ||
                        side.equals("WEST") || side.equals("NORTH_WEST"),
                        "Side should be a valid SIDE enum value: " + side);
                }
            }
        }

        log.info("Grids with ridge configuration: {}/{}", gridsWithRidge, hexGrids.size());
        log.info("Total ridge entries: {}", totalRidgeEntries);

        // For a LINE of 5 grids, most should have ridge configuration (except possibly end grids)
        assertTrue(gridsWithRidge >= 3,
            "At least 3 grids should have ridge configuration in a LINE of 5");
        assertTrue(totalRidgeEntries >= 6,
            "Should have at least 6 total ridge entries (2 per middle grid)");

        log.info("=== Mountain Ridge Configuration Test Completed ===");
    }

    @Test
    public void testMountainFillerSystem() throws Exception {
        log.info("=== Testing Mountain Filler System ===");

        // Create a HIGH_PEAKS mountain
        MountainBiome mountain = new MountainBiome();
        mountain.setName("filler-test-mountain");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        mountain.setShape(AreaShape.CIRCLE);
        mountain.setSize(AreaSize.SMALL);
        mountain.setPositions(java.util.List.of(createOriginPosition()));

        // Create composition
        HexComposition composition = HexComposition.builder()
            .worldId("test-world")
            .name("filler-test")
            .features(new ArrayList<>())
            .build();
        composition.getFeatures().add(mountain);

        // Compose with HexCompositeBuilder WITH fillGaps enabled
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("test-world")
            .seed(12345L)
            .fillGaps(true)  // Enable fillers!
            .oceanBorderRings(1)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");

        // Check fill result
        HexGridFillResult fillResult = result.getFillResult();
        assertNotNull(fillResult, "Fill result should exist");
        assertTrue(fillResult.isSuccess(), "Filling should succeed");

        log.info("Filling stats: total={}, land={}, coast={}",
            fillResult.getTotalGridCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Should have added mountain slopes (MEDIUM_PEAKS around HIGH_PEAKS)
        assertTrue(fillResult.getLandFillCount() > 0,
            "Should have added mountain slope grids");

        // Should have added coast grids
        assertTrue(fillResult.getCoastFillCount() > 0,
            "Should have added coast grids");

        // Check for filler grids in placement result
        BiomePlacementResult placementResult = result.getBiomePlacementResult();
        long fillerCount = placementResult.getHexGrids().stream()
            .filter(g -> "true".equals(g.getParameters().get("filler")))
            .count();

        log.info("Found {} filler grids", fillerCount);
        assertTrue(fillerCount > 0, "Should have filler grids");

        // Check for mountain filler grids
        long mountainFillerCount = placementResult.getHexGrids().stream()
            .filter(g -> "mountain".equals(g.getParameters().get("fillerType")))
            .count();

        log.info("Found {} mountain filler grids", mountainFillerCount);
        assertTrue(mountainFillerCount > 0, "Should have mountain slope grids");

        // Check for coast filler grids
        long coastFillerCount = placementResult.getHexGrids().stream()
            .filter(g -> "coast".equals(g.getParameters().get("fillerType")))
            .count();

        log.info("Found {} coast filler grids", coastFillerCount);
        assertTrue(coastFillerCount > 0, "Should have coast grids");

        log.info("=== Mountain Filler System Test Completed ===");
    }
}
