package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.flat.*;
import de.mhus.nimbus.world.generator.flat.hexgrid.*;
import de.mhus.nimbus.world.shared.generator.FlatLevelImageCreator;
import de.mhus.nimbus.world.shared.generator.FlatMaterialImageCreator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Creates composite images showing multiple hex grids together
 * Demonstrates the complete world generation pipeline
 */
@Slf4j
@Disabled // Disabled to prevent automatic test runs; enable for manual testing
public class WorldCompositeImageTest {

    private static final int FLAT_SIZE = 512;
    private static final int OCEAN_LEVEL = 50;
    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        outputDir = Paths.get("target/test-output/world-composite");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());
    }

    @Test
    public void testCompleteWorldWithAllComponents() throws Exception {
        log.info("=== Testing Complete World with All Components ===");

        // Create HexComposition with Features (biomes AND villages)
        HexComposition composition = createCompositionWithVillages();

        // Export input model
        exportInputModel(composition, "complete-world-all-components");

        // Use HexCompositeBuilder for the complete pipeline
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("full-world")
            .seed(99999L)
            .fillGaps(true)
            .oceanBorderRings(1)
            .generateWHexGrids(false)  // We don't need WHexGrid generation for this test
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getFillResult(), "Should have fill result");
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Placed {} biomes creating {} hex grids",
            result.getTotalBiomes(),
            result.getTotalGrids());

        log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        log.info("Composed {} flows with {} total segments",
            result.getFlowCompositionResult().getComposedFlows(),
            result.getFlowCompositionResult().getTotalSegments());

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "complete-world-all-components");

        // Export final generated model
        exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "complete-world-all-components");

        log.info("=== Complete World with All Components Test Completed ===");
    }

    @Test
    public void testSmallWorldWithDifferentBuilders() throws Exception {
        log.info("=== Testing Small World with Different Builders ===");

        // Create a simple 3x3 grid with different builder types
        List<FilledHexGrid> grids = new ArrayList<>();

        // Center: Island
        grids.add(createFilledGridWithBuilder(0, 0, "island", BiomeType.PLAINS));

        // North: Mountains
        grids.add(createFilledGridWithBuilder(0, -1, BiomeType.MOUNTAINS.getBuilderName(), BiomeType.MOUNTAINS));

        // East: Coast
        grids.add(createFilledGridWithBuilder(1, 0, BiomeType.COAST.getBuilderName(), BiomeType.COAST));

        // South: Ocean
        grids.add(createFilledGridWithBuilder(0, 1, BiomeType.OCEAN.getBuilderName(), BiomeType.OCEAN));

        // West: Mountains
        grids.add(createFilledGridWithBuilder(-1, 0, BiomeType.MOUNTAINS.getBuilderName(), BiomeType.MOUNTAINS));

        // Create empty placement result
        BiomePlacementResult placementResult = BiomePlacementResult.builder()
            .placedBiomes(new ArrayList<>())
            .hexGrids(grids.stream().map(FilledHexGrid::getHexGrid).toList())
            .success(true)
            .build();

        HexGridFillResult fillResult = HexGridFillResult.builder()
            .allGrids(grids)
            .placementResult(placementResult)
            .success(true)
            .build();

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Save individual images
        saveIndividualGridImages(flats, fillResult);

        // Create composite
        createCompositeImage(flats, fillResult, "small-world-builders");

        log.info("=== Small World Test Completed ===");
    }

    @Test
    public void testCompleteWorldComposite() throws Exception {
        log.info("=== Testing Complete World Composite Image Generation ===");

        // Create composition with villages and roads
        HexComposition composition = createCompositionWithVillages();

        // Use HexCompositeBuilder for the complete pipeline
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("composite-world")
            .seed(54321L)
            .fillGaps(true)
            .oceanBorderRings(2)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getFillResult(), "Should have fill result");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Placed {} biomes creating {} hex grids",
            result.getTotalBiomes(),
            result.getTotalGrids());

        log.info("Composed {} flows: {} successful, {} failed",
            result.getFlowCompositionResult().getTotalFlows(),
            result.getFlowCompositionResult().getComposedFlows(),
            result.getFlowCompositionResult().getFailedFlows());

        log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "complete-world-composite");

        log.info("=== Composite Image Test Completed ===");
    }

    @Test
    public void testWorldWithVariousMountainBiomes() throws Exception {
        log.info("=== Testing World with Various Mountain Biome Heights ===");

        // Create composition with different mountain heights
        HexComposition composition = createCompositionWithMountains();

        // Export input model
        exportInputModel(composition, "mountain-world-various-heights");

        // Use HexCompositeBuilder for the complete pipeline
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("mountain-world")
            .seed(77777L)
            .fillGaps(true)
            .oceanBorderRings(1)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getFillResult(), "Should have fill result");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Placed {} biomes creating {} hex grids",
            result.getTotalBiomes(),
            result.getTotalGrids());

        log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Verify mountain biomes are present
        long mountainBiomes = fillResult.getPlacementResult().getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome().getType() == BiomeType.MOUNTAINS)
            .count();

        log.info("Found {} mountain biomes", mountainBiomes);
        assertTrue(mountainBiomes >= 4, "Should have at least 4 mountain biomes with different heights");

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "mountain-world-various-heights");

        // Export final generated model
        exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "mountain-world-various-heights");

        log.info("=== Mountain Biomes Test Completed ===");
    }

    @Test
    public void testWorldWithOrganicMountainShapes() throws Exception {
        log.info("=== Testing World with Organic Mountain Shapes (Direction Deviation) ===");

        // Create composition with mountains using different direction deviations
        HexComposition composition = createCompositionWithOrganicMountains();

        // Export input model
        exportInputModel(composition, "mountain-world-organic-shapes");

        // Use HexCompositeBuilder for the complete pipeline
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("organic-mountain-world")
            .seed(88888L)
            .fillGaps(true)
            .oceanBorderRings(1)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getFillResult(), "Should have fill result");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Placed {} biomes creating {} hex grids",
            result.getTotalBiomes(),
            result.getTotalGrids());

        log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "mountain-world-organic-shapes");

        // Export final generated model
        exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "mountain-world-organic-shapes");

        log.info("=== Organic Mountain Shapes Test Completed ===");
    }

    /**
     * Creates HexComposition with organic mountain shapes using direction deviation
     */
    private HexComposition createCompositionWithOrganicMountains() {
        HexComposition composition = HexComposition.builder()
            .name("Organic Mountain World")
            .worldId("organic-mountain-world")
            .features(new ArrayList<>())
            .build();

        // Center: Plains (for reference)
        Biome plains = createBiome("Central Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            2, 3, Direction.N, 0, 0, 0, "origin", 10);
        composition.getFeatures().add(plains);

        // North: Straight Mountains (no deviation)
        MountainBiome straightMountains = new MountainBiome();
        straightMountains.setName("Straight Mountains");
        straightMountains.setType(BiomeType.MOUNTAINS);
        straightMountains.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        straightMountains.setShape(AreaShape.LINE);
        straightMountains.setSizeFrom(8);
        straightMountains.setSizeTo(10);
        straightMountains.setDirectionDeviation(0.0);  // No deviation

        RelativePosition northPos = RelativePosition.builder()
            .direction(Direction.N)
            .distanceFrom(5)
            .distanceTo(8)
            .anchor("origin")
            .priority(9)
            .build();
        straightMountains.setPositions(Arrays.asList(northPos));
        straightMountains.prepareForComposition();
        composition.getFeatures().add(straightMountains);

        // East: Organic Mountains (moderate deviation)
        MountainBiome organicMountains = new MountainBiome();
        organicMountains.setName("Organic Mountains");
        organicMountains.setType(BiomeType.MOUNTAINS);
        organicMountains.setHeight(MountainBiome.MountainHeight.MEDIUM_PEAKS);
        organicMountains.setShape(AreaShape.LINE);
        organicMountains.setSizeFrom(10);
        organicMountains.setSizeTo(12);
        organicMountains.setDirectionDeviation(0.4);  // 40% deviation

        RelativePosition eastPos = RelativePosition.builder()
            .direction(Direction.E)
            .distanceFrom(6)
            .distanceTo(9)
            .anchor("origin")
            .priority(8)
            .build();
        organicMountains.setPositions(Arrays.asList(eastPos));
        organicMountains.prepareForComposition();
        composition.getFeatures().add(organicMountains);

        // South: Wiggly Mountains (high deviation)
        MountainBiome wigglyMountains = new MountainBiome();
        wigglyMountains.setName("Wiggly Mountains");
        wigglyMountains.setType(BiomeType.MOUNTAINS);
        wigglyMountains.setHeight(MountainBiome.MountainHeight.LOW_PEAKS);
        wigglyMountains.setShape(AreaShape.LINE);
        wigglyMountains.setSizeFrom(12);
        wigglyMountains.setSizeTo(15);
        wigglyMountains.setDirectionDeviation(0.8);  // 80% deviation

        RelativePosition southPos = RelativePosition.builder()
            .direction(Direction.S)
            .distanceFrom(6)
            .distanceTo(9)
            .anchor("origin")
            .priority(7)
            .build();
        wigglyMountains.setPositions(Arrays.asList(southPos));
        wigglyMountains.prepareForComposition();
        composition.getFeatures().add(wigglyMountains);

        // West: Left-turning Mountains (asymmetric deviation)
        MountainBiome leftTurningMountains = new MountainBiome();
        leftTurningMountains.setName("Left-turning Mountains");
        leftTurningMountains.setType(BiomeType.MOUNTAINS);
        leftTurningMountains.setHeight(MountainBiome.MountainHeight.MEADOW);
        leftTurningMountains.setShape(AreaShape.LINE);
        leftTurningMountains.setSizeFrom(10);
        leftTurningMountains.setSizeTo(12);
        leftTurningMountains.setDeviationLeft(0.5);   // 50% left
        leftTurningMountains.setDeviationRight(0.1);  // 10% right

        RelativePosition westPos = RelativePosition.builder()
            .direction(Direction.W)
            .distanceFrom(6)
            .distanceTo(9)
            .anchor("origin")
            .priority(6)
            .build();
        leftTurningMountains.setPositions(Arrays.asList(westPos));
        leftTurningMountains.prepareForComposition();
        composition.getFeatures().add(leftTurningMountains);

        return composition;
    }

    /**
     * Creates HexComposition with various mountain biome heights
     */
    private HexComposition createCompositionWithMountains() {
        HexComposition composition = HexComposition.builder()
            .name("Mountain World")
            .worldId("mountain-world")
            .features(new ArrayList<>())
            .build();

        // Center: Plains (for reference and variety)
        Biome plains = createBiome("Central Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            2, 3, Direction.N, 0, 0, 0, "origin", 10);
        composition.getFeatures().add(plains);

        // North: HIGH_PEAKS Mountains (tallest - max ~240 blocks)
        MountainBiome highPeaks = new MountainBiome();
        highPeaks.setName("High Peaks Mountains");
        highPeaks.setType(BiomeType.MOUNTAINS);
        highPeaks.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        highPeaks.setShape(AreaShape.LINE);
        highPeaks.setSizeFrom(4);
        highPeaks.setSizeTo(5);

        RelativePosition highPeaksPos = RelativePosition.builder()
            .direction(Direction.N)
            .distanceFrom(5)
            .distanceTo(8)
            .anchor("origin")
            .priority(9)
            .build();
        highPeaks.setPositions(Arrays.asList(highPeaksPos));
        highPeaks.prepareForComposition();
        composition.getFeatures().add(highPeaks);

        // East: MEDIUM_PEAKS Mountains (medium - max ~200 blocks)
        MountainBiome mediumPeaks = new MountainBiome();
        mediumPeaks.setName("Medium Peaks Mountains");
        mediumPeaks.setType(BiomeType.MOUNTAINS);
        mediumPeaks.setHeight(MountainBiome.MountainHeight.MEDIUM_PEAKS);
        mediumPeaks.setShape(AreaShape.CIRCLE);
        mediumPeaks.setSizeFrom(3);
        mediumPeaks.setSizeTo(4);

        RelativePosition mediumPeaksPos = RelativePosition.builder()
            .direction(Direction.E)
            .distanceFrom(6)
            .distanceTo(9)
            .anchor("origin")
            .priority(8)
            .build();
        mediumPeaks.setPositions(Arrays.asList(mediumPeaksPos));
        mediumPeaks.prepareForComposition();
        composition.getFeatures().add(mediumPeaks);

        // South: LOW_PEAKS Mountains (low - max ~170 blocks)
        MountainBiome lowPeaks = new MountainBiome();
        lowPeaks.setName("Low Peaks Mountains");
        lowPeaks.setType(BiomeType.MOUNTAINS);
        lowPeaks.setHeight(MountainBiome.MountainHeight.LOW_PEAKS);
        lowPeaks.setShape(AreaShape.CIRCLE);
        lowPeaks.setSizeFrom(3);
        lowPeaks.setSizeTo(4);

        RelativePosition lowPeaksPos = RelativePosition.builder()
            .direction(Direction.S)
            .distanceFrom(6)
            .distanceTo(9)
            .anchor("origin")
            .priority(7)
            .build();
        lowPeaks.setPositions(Arrays.asList(lowPeaksPos));
        lowPeaks.prepareForComposition();
        composition.getFeatures().add(lowPeaks);

        // West: MEADOW Mountains (gentle hills - max ~140 blocks)
        MountainBiome meadow = new MountainBiome();
        meadow.setName("Meadow Mountains");
        meadow.setType(BiomeType.MOUNTAINS);
        meadow.setHeight(MountainBiome.MountainHeight.MEADOW);
        meadow.setShape(AreaShape.CIRCLE);
        meadow.setSizeFrom(3);
        meadow.setSizeTo(4);

        RelativePosition meadowPos = RelativePosition.builder()
            .direction(Direction.W)
            .distanceFrom(6)
            .distanceTo(9)
            .anchor("origin")
            .priority(6)
            .build();
        meadow.setPositions(Arrays.asList(meadowPos));
        meadow.prepareForComposition();
        composition.getFeatures().add(meadow);

        // Northeast: Forest (for variety)
        Biome forest = createBiome("Northeast Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            2, 3, Direction.NE, 45, 10, 12, "origin", 5);
        composition.getFeatures().add(forest);

        return composition;
    }

    /**
     * Creates HexComposition with biomes AND villages (unprepared)
     */
    private HexComposition createCompositionWithVillages() {
        HexComposition composition = HexComposition.builder()
            .name("Test World with Villages")
            .worldId("test-world")
            .features(new ArrayList<>())
            .build();

        // Center: Plains with Village
        Biome plains = createBiome("Central Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            3, 4, Direction.N, 0, 0, 0, "origin", 10);
        composition.getFeatures().add(plains);

        // North: Mountains
        composition.getFeatures().add(createBiome("Northern Mountains", BiomeType.MOUNTAINS, AreaShape.LINE,
            3, 4, Direction.N, 0, 5, 8, "origin", 9));

        // East: Forest
        composition.getFeatures().add(createBiome("Eastern Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            2, 3, Direction.E, 90, 5, 8, "origin", 8));

        // Village in center plains
        Village village = Village.builder()
            .buildings(new ArrayList<>())
            .streets(new ArrayList<>())
            .parameters(new HashMap<>())
            .build();
        village.setName("Central Village");
        village.setType(StructureType.HAMLET);

        RelativePosition villagePos = RelativePosition.builder()
            .direction(Direction.N)
            .distanceFrom(0)
            .distanceTo(2)
            .anchor("origin")
            .priority(5)
            .build();

        village.setPositions(Arrays.asList(villagePos));

        // Use VillageDesigner to create village buildings
        VillageTemplate template = VillageTemplateLoader.load("hamlet-medieval");
        VillageDesigner designer = new VillageDesigner();
        VillageDesignResult designResult = designer.design(template, 95);

        // Add village parameters
        HexGridConfig centerGridConfig = designResult.getGridConfigs().get(HexVector2.builder().q(0).r(0).build());
        if (centerGridConfig != null) {
            village.getParameters().put("g_village", centerGridConfig.toVillageParameter());
            village.getParameters().put("g_road", centerGridConfig.toRoadParameter());
        }

        composition.getFeatures().add(village);

        // Add Roads as Flow features connecting biomes
        Road mainRoad = Road.builder()
            .roadType("street")
            .level(95)
            .endPointId("Eastern Forest")
            .build();
        mainRoad.setName("Main Road");
        mainRoad.setFeatureId("main-road");
        mainRoad.setStartPointId("Central Plains");
        mainRoad.setWidthBlocks(5);
        mainRoad.setType(FlowType.ROAD);
        composition.getFeatures().add(mainRoad);

        Road northRoad = Road.builder()
            .roadType("street")
            .level(95)
            .endPointId("Northern Mountains")
            .build();
        northRoad.setName("North Road");
        northRoad.setFeatureId("north-road");
        northRoad.setStartPointId("Central Plains");
        northRoad.setWidthBlocks(4);
        northRoad.setType(FlowType.ROAD);
        composition.getFeatures().add(northRoad);

        // Add River as Flow feature from Mountains to Plains
        River mountainRiver = River.builder()
            .depth(2)
            .level(45)
            .mergeToId("Central Plains")
            .build();
        mountainRiver.setName("Mountain River");
        mountainRiver.setFeatureId("mountain-river");
        mountainRiver.setStartPointId("Northern Mountains");
        mountainRiver.setWidthBlocks(3);
        mountainRiver.setType(FlowType.RIVER);
        composition.getFeatures().add(mountainRiver);

        return composition;
    }

    /**
     * Creates a complete composition
     */
    private HexComposition createComposition() {
        HexComposition composition = new HexComposition();
        List<Biome> biomes = new ArrayList<>();

        // Center: Plains
        biomes.add(createBiome("Central Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            4, 6, Direction.N, 0, 0, 0, "origin", 10));

        // North: Mountains
        biomes.add(createBiome("Northern Mountains", BiomeType.MOUNTAINS, AreaShape.LINE,
            5, 7, Direction.N, 0, 8, 12, "origin", 9));

        // East: Forest
        biomes.add(createBiome("Eastern Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            4, 6, Direction.E, 90, 8, 12, "origin", 8));

        // South: Swamp
        biomes.add(createBiome("Southern Swamp", BiomeType.SWAMP, AreaShape.CIRCLE,
            3, 5, Direction.S, 180, 8, 12, "origin", 7));

        // West: Desert
        biomes.add(createBiome("Western Desert", BiomeType.DESERT, AreaShape.CIRCLE,
            4, 5, Direction.W, 270, 8, 12, "origin", 6));

        composition.setFeatures(new ArrayList<>(biomes));

        return composition;
    }

    /**
     * Builds terrain for a single grid
     */
    private WFlat buildGridTerrain(FilledHexGrid filled) {
        // Create flat with initialized arrays
        byte[] levels = new byte[FLAT_SIZE * FLAT_SIZE];
        byte[] columns = new byte[FLAT_SIZE * FLAT_SIZE];

        // Determine builder type - check if already set, otherwise use biome type
        String builderType = null;
        if (filled.getHexGrid().getParameters() != null) {
            builderType = filled.getHexGrid().getParameters().get("g_builder");
        }

        if (builderType == null || builderType.isEmpty()) {
            if (filled.isFiller()) {
                builderType = switch (filled.getFillerType()) {
                    case OCEAN -> BiomeType.OCEAN.getBuilderName();
                    case LAND -> BiomeType.COAST.getBuilderName();  // Use coast for land filler
                    case COAST -> BiomeType.COAST.getBuilderName();
                };
            } else if (filled.getBiome() != null && filled.getBiome().getBiome() != null) {
                builderType = switch (filled.getBiome().getBiome().getType()) {
                    case MOUNTAINS -> BiomeType.MOUNTAINS.getBuilderName();
                    case FOREST -> BiomeType.COAST.getBuilderName();     // Forest builder not registered yet
                    case DESERT -> BiomeType.COAST.getBuilderName();     // Desert builder not registered yet
                    case SWAMP -> BiomeType.COAST.getBuilderName();      // Swamp builder not registered yet
                    case PLAINS -> BiomeType.ISLAND.getBuilderName();    // Use island for plains
                    case OCEAN -> BiomeType.OCEAN.getBuilderName();
                    case COAST -> BiomeType.COAST.getBuilderName();
                    case ISLAND -> BiomeType.ISLAND.getBuilderName();
                    case VILLAGE -> BiomeType.VILLAGE.getBuilderName();
                    case TOWN -> BiomeType.TOWN.getBuilderName();
                };
            } else {
                builderType = BiomeType.COAST.getBuilderName();  // Default fallback
            }

            // Set g_builder parameter on hexGrid
            if (filled.getHexGrid().getParameters() == null) {
                filled.getHexGrid().setParameters(new HashMap<>());
            }
            filled.getHexGrid().getParameters().put("g_builder", builderType);
        }

        // Initialize with base terrain based on type
        int baseLevel = getBuilderBaseLevel(builderType);
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (byte) baseLevel;
            columns[i] = 0;
        }

        WFlat flat = WFlat.builder()
            .flatId("flat-" + filled.getCoordinate().getQ() + "-" + filled.getCoordinate().getR())
            .worldId("composite-world")
            .layerDataId("test-layer")
            .hexGrid(filled.getCoordinate())
            .sizeX(FLAT_SIZE)
            .sizeZ(FLAT_SIZE)
            .oceanLevel(OCEAN_LEVEL)
            .mountX(FLAT_SIZE / 2)
            .mountZ(FLAT_SIZE / 2)
            .levels(levels)
            .columns(columns)
            .extraBlocks(new HashMap<>())
            .materials(new HashMap<>())
            .unknownProtected(false)
            .borderProtected(false)
            .build();

        // Apply builder pipeline (includes village, road, river builders)
        try {
            HexGridBuilderService builderService = new HexGridBuilderService();

            // Create builder pipeline based on hexGrid parameters
            List<HexGridBuilder> pipeline = builderService.createBuilderPipeline(filled.getHexGrid());

            if (pipeline.isEmpty()) {
                log.warn("No builders in pipeline for grid [{},{}]",
                    filled.getCoordinate().getQ(), filled.getCoordinate().getR());
                return flat;
            }

            // Create context
            BuilderContext context = createContext(flat, filled.getHexGrid());

            // Execute all builders in pipeline
            for (HexGridBuilder builder : pipeline) {
                builder.setContext(context);
                builder.buildFlat();

                // Log important grids
                if (filled.getCoordinate().getQ() == 0 && filled.getCoordinate().getR() == 0) {
                    log.info("Executed builder: {} for grid [0,0]",
                        builder.getClass().getSimpleName());
                }
            }

            // Log important grids
            if (filled.getCoordinate().getQ() == 0 && filled.getCoordinate().getR() == 0) {
                log.info("Completed grid [0,0] with {} builders", pipeline.size());
            }
        } catch (Exception e) {
            log.warn("Failed to build terrain for grid [{},{}]: {}",
                filled.getCoordinate().getQ(), filled.getCoordinate().getR(),
                e.getMessage());
        }

        return flat;
    }

    /**
     * Gets base level for builder type
     */
    private int getBuilderBaseLevel(String builderType) {
        return switch (builderType) {
            case "ocean" -> 40;
            case "coast" -> 48;
            case "island" -> 52;
            case "plains", "forest", "desert", "swamp" -> 55;
            case "mountains" -> 70;
            default -> 50;
        };
    }

    /**
     * Creates builder context with real manipulator service
     */
    private BuilderContext createContext(WFlat flat, WHexGrid hexGrid) {
        // Create real FlatManipulatorService with all necessary manipulators
        List<FlatManipulator> manipulators = List.of(
            new HillyTerrainManipulator(),
            new NormalTerrainManipulator(),
            new FlatTerrainManipulator(),
            new SoftenManipulator(),
            new BorderSmoothManipulator(),
            new IslandsManipulator()
        );
        FlatManipulatorService manipulatorService = new FlatManipulatorService(manipulators);

        WChunkService chunkService = mock(WChunkService.class);
        WWorld world = mock(WWorld.class);

        return BuilderContext.builder()
            .flat(flat)
            .hexGrid(hexGrid)
            .world(world)
            .neighborGrids(new HashMap<>())
            .manipulatorService(manipulatorService)
            .chunkService(chunkService)
            .build();
    }

    /**
     * Saves individual grid images organized by type
     */
    private void saveIndividualGridImages(Map<HexVector2, WFlat> flats,
                                          HexGridFillResult fillResult) throws Exception {
        log.info("Saving individual grid images...");

        // Create subdirectories
        Path biomeDir = outputDir.resolve("biomes");
        Path oceanDir = outputDir.resolve("ocean");
        Path landDir = outputDir.resolve("land");
        Path coastDir = outputDir.resolve("coast");

        Files.createDirectories(biomeDir);
        Files.createDirectories(oceanDir);
        Files.createDirectories(landDir);
        Files.createDirectories(coastDir);

        int savedCount = 0;
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = flats.get(filled.getCoordinate());
            if (flat == null) continue;

            try {
                // Generate images
                FlatLevelImageCreator levelCreator = new FlatLevelImageCreator(flat);
                byte[] levelBytes = levelCreator.create(false);
                BufferedImage levelImage = ImageIO.read(new ByteArrayInputStream(levelBytes));

                FlatMaterialImageCreator materialCreator = new FlatMaterialImageCreator(flat);
                byte[] materialBytes = materialCreator.create(false);
                BufferedImage materialImage = ImageIO.read(new ByteArrayInputStream(materialBytes));

                // Determine directory and filename
                Path targetDir;
                String prefix;

                if (filled.isFiller()) {
                    switch (filled.getFillerType()) {
                        case OCEAN -> {
                            targetDir = oceanDir;
                            prefix = "ocean";
                        }
                        case LAND -> {
                            targetDir = landDir;
                            prefix = "land";
                        }
                        case COAST -> {
                            targetDir = coastDir;
                            prefix = "coast";
                        }
                        default -> {
                            targetDir = outputDir;
                            prefix = "unknown";
                        }
                    }
                } else {
                    targetDir = biomeDir;
                    prefix = filled.getBiome().getBiome().getType().name().toLowerCase();
                }

                String filename = String.format("%s-[%d,%d]",
                    prefix,
                    filled.getCoordinate().getQ(),
                    filled.getCoordinate().getR());

                // Save images
                File levelFile = targetDir.resolve(filename + "-level.png").toFile();
                File materialFile = targetDir.resolve(filename + "-material.png").toFile();

                ImageIO.write(levelImage, "PNG", levelFile);
                ImageIO.write(materialImage, "PNG", materialFile);

                savedCount++;

                if (savedCount <= 5) {
                    log.debug("Saved {} images: {}", prefix, filename);
                }
            } catch (Exception e) {
                log.warn("Failed to save images for grid [{},{}]: {}",
                    filled.getCoordinate().getQ(),
                    filled.getCoordinate().getR(),
                    e.getMessage());
            }
        }

        log.info("Saved {} individual grid images", savedCount);
    }

    /**
     * Creates composite image from all flats using correct HEX geometry
     */
    private void createCompositeImage(Map<HexVector2, WFlat> flats,
                                     HexGridFillResult fillResult,
                                     String name) throws Exception {
        // Use the new HexGridCompositeImageCreator helper class
        HexGridCompositeImageCreator creator = HexGridCompositeImageCreator.builder()
            .flats(flats)
            .flatSize(FLAT_SIZE)
            .outputDirectory(outputDir.toString())
            .imageName(name)
            .drawGridLines(true)
            .build();

        HexGridCompositeImageCreator.CompositeImageResult result = creator.createCompositeImages();

        if (!result.isSuccess()) {
            throw new RuntimeException("Failed to create composite image: " + result.getErrorMessage());
        }

        // Log grid breakdown
        log.info("Grids breakdown:");
        log.info("- Biome grids: {}", fillResult.getPlacementResult().getPlacedBiomes().size());
        log.info("- Ocean filler: {}", fillResult.getOceanFillCount());
        log.info("- Land filler: {}", fillResult.getLandFillCount());
        log.info("- Coast filler: {}", fillResult.getCoastFillCount());
        log.info("- Total: {}", fillResult.getTotalGridCount());
    }

    /**
     * Helper to create FilledHexGrid with specific builder
     */
    private FilledHexGrid createFilledGridWithBuilder(int q, int r, String builderType, BiomeType biomeType) {
        HexVector2 coord = HexVector2.builder().q(q).r(r).build();

        WHexGrid hexGrid = WHexGrid.builder()
            .worldId("test-world")
            .position(q + ":" + r)
            .parameters(new HashMap<>())
            .enabled(true)
            .build();

        hexGrid.getParameters().put("g_builder", builderType);

        // Create Biome
        Biome biome = Biome.builder()
            .type(biomeType)
            .parameters(new HashMap<>())
            .build();
        biome.setName(builderType + "-" + q + "-" + r);
        biome.setShape(AreaShape.CIRCLE);
        biome.setSizeFrom(1);
        biome.setSizeTo(1);
        biome.setCalculatedSizeFrom(1);
        biome.setCalculatedSizeTo(1);
        biome.setPreparedPositions(new ArrayList<>());

        // Wrap in PlacedBiome
        PlacedBiome placedBiome = new PlacedBiome();
        placedBiome.setBiome(biome);
        placedBiome.setCenter(coord);
        placedBiome.setCoordinates(List.of(coord));
        placedBiome.setActualSize(1);

        return FilledHexGrid.builder()
            .coordinate(coord)
            .hexGrid(hexGrid)
            .biome(placedBiome)
            .isFiller(false)
            .build();
    }

    /**
     * Exports input model as JSON
     */
    private void exportInputModel(HexComposition composition, String name) throws Exception {
        File inputFile = outputDir.resolve(name + "-input-model.json").toFile();

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();  // Register JavaTimeModule for Instant serialization
        mapper.writerWithDefaultPrettyPrinter().writeValue(inputFile, composition);

        log.info("Exported input model to: {}", inputFile.getAbsolutePath());
    }

    /**
     * Exports generated model as JSON
     */
    private void exportGeneratedModel(HexGridFillResult fillResult,
                                      FlowComposer.FlowCompositionResult flowResult,
                                      String name) throws Exception {

        // Create output model
        Map<String, Object> outputModel = new HashMap<>();

        // Summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalGrids", fillResult.getAllGrids().size());
        summary.put("biomeGrids", fillResult.getPlacementResult().getPlacedBiomes().size());
        summary.put("oceanFiller", fillResult.getOceanFillCount());
        summary.put("landFiller", fillResult.getLandFillCount());
        summary.put("coastFiller", fillResult.getCoastFillCount());
        summary.put("flowsComposed", flowResult.getComposedFlows());
        summary.put("flowSegments", flowResult.getTotalSegments());
        outputModel.put("summary", summary);

        // Placed biomes
        List<Map<String, Object>> placedBiomes = new ArrayList<>();
        for (PlacedBiome placed : fillResult.getPlacementResult().getPlacedBiomes()) {
            Map<String, Object> biomeInfo = new HashMap<>();
            biomeInfo.put("name", placed.getBiome().getName());
            biomeInfo.put("type", placed.getBiome().getType());
            biomeInfo.put("center", coordinateToString(placed.getCenter()));
            biomeInfo.put("size", placed.getActualSize());

            List<String> coords = new ArrayList<>();
            for (HexVector2 coord : placed.getCoordinates()) {
                coords.add(coordinateToString(coord));
            }
            biomeInfo.put("coordinates", coords);

            placedBiomes.add(biomeInfo);
        }
        outputModel.put("placedBiomes", placedBiomes);

        // All hex grids with parameters
        List<Map<String, Object>> hexGrids = new ArrayList<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            Map<String, Object> gridInfo = new HashMap<>();
            gridInfo.put("coordinate", coordinateToString(filled.getCoordinate()));
            gridInfo.put("position", filled.getHexGrid().getPosition());

            if (filled.isFiller()) {
                gridInfo.put("type", "filler");
                gridInfo.put("fillerType", filled.getFillerType().name());
            } else {
                gridInfo.put("type", "biome");
                gridInfo.put("biomeName", filled.getBiome().getBiome().getName());
                gridInfo.put("biomeType", filled.getBiome().getBiome().getType());
            }

            // Parameters - export exactly as they are in WHexGrid
            if (filled.getHexGrid().getParameters() != null && !filled.getHexGrid().getParameters().isEmpty()) {
                gridInfo.put("parameters", filled.getHexGrid().getParameters());
            }

            hexGrids.add(gridInfo);
        }
        outputModel.put("hexGrids", hexGrids);

        // Flow composition details (Roads and Rivers are now Features)
        Map<String, Object> flowInfo = new HashMap<>();
        flowInfo.put("totalFlows", flowResult.getTotalFlows());
        flowInfo.put("composedFlows", flowResult.getComposedFlows());
        flowInfo.put("failedFlows", flowResult.getFailedFlows());
        flowInfo.put("totalSegments", flowResult.getTotalSegments());
        flowInfo.put("errors", flowResult.getErrors());
        outputModel.put("flows", flowInfo);

        // Write to file
        File outputFile = outputDir.resolve(name + "-generated-model.json").toFile();
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();  // Register JavaTimeModule for Instant serialization
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, outputModel);

        log.info("Exported generated model to: {}", outputFile.getAbsolutePath());
        log.info("Generated model contains {} hex grids", hexGrids.size());
    }

    /**
     * Helper to convert coordinate to string
     */
    private String coordinateToString(HexVector2 coord) {
        return String.format("[%d,%d]", coord.getQ(), coord.getR());
    }

    /**
     * Helper to create biome and prepare it for composition
     */
    private RelativePosition createPosition(Direction direction, int angle,
                                            int distFrom, int distTo,
                                            String anchor, int priority) {
        return RelativePosition.builder()
            .direction(direction)
            .distanceFrom(distFrom)
            .distanceTo(distTo)
            .anchor(anchor)
            .priority(priority)
            .build();
    }

    private Biome createBiome(String name, BiomeType type, AreaShape shape,
                              int sizeFrom, int sizeTo,
                              Direction direction, int angle,
                              int distFrom, int distTo,
                              String anchor, int priority) {
        Biome biome = Biome.builder()
            .type(type)
            .parameters(new HashMap<>())
            .build();
        biome.setName(name);
        biome.setShape(shape);
        biome.setSizeFrom(sizeFrom);
        biome.setSizeTo(sizeTo);

        RelativePosition pos = createPosition(direction, angle, distFrom, distTo, anchor, priority);

        biome.setPositions(Arrays.asList(pos));

        // Prepare biome for tests that bypass HexCompositionPreparer
        biome.prepareForComposition();

        return biome;
    }

    @Test
    public void testBiomeWithLongRoad() throws Exception {
        log.info("=== Testing Single Biome with Long Road ===");

        // Create composition with one biome and a long road
        HexComposition composition = createCompositionWithLongRoad();

        // Export input model
        exportInputModel(composition, "biome-with-long-road");

        // Use HexCompositeBuilder for the complete pipeline
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("road-test-world")
            .seed(11111L)
            .fillGaps(true)
            .oceanBorderRings(1)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getFillResult(), "Should have fill result");
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");

        // Verify road was composed
        assertEquals(1, result.getFlowCompositionResult().getComposedFlows(),
            "Should have composed 1 road");
        assertTrue(result.getFlowCompositionResult().getTotalSegments() >= 4,
            "Road should have at least 4 segments");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Placed {} biomes creating {} hex grids",
            result.getTotalBiomes(),
            result.getTotalGrids());

        log.info("Road: {} segments across {} grids",
            result.getFlowCompositionResult().getTotalSegments(),
            result.getFlowCompositionResult().getComposedFlows());

        log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "biome-with-long-road");

        // Export final generated model
        exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "biome-with-long-road");

        log.info("=== Biome with Long Road Test Completed ===");
    }

    /**
     * Creates HexComposition with a single biome and a long road crossing multiple grids
     */
    private HexComposition createCompositionWithLongRoad() {
        HexComposition composition = HexComposition.builder()
            .name("Road Test World")
            .worldId("road-test-world")
            .features(new ArrayList<>())
            .build();

        // Center: Large Plains biome
        Biome plains = createBiome("Central Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            5, 7, Direction.N, 0, 0, 0, "origin", 10);
        composition.getFeatures().add(plains);

        // East: Forest (road destination)
        Biome forest = createBiome("Eastern Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            2, 3, Direction.E, 90, 6, 8, "origin", 9);
        composition.getFeatures().add(forest);

        // Long Road from Plains to Forest (crossing at least 4-5 grids)
        Road longRoad = Road.builder()
            .roadType("cobblestone")
            .level(95)
            .waypointIds(new ArrayList<>())
            .endPointId("Eastern Forest")
            .build();
        longRoad.setName("Main Highway");
        longRoad.setFeatureId("main-highway");
        longRoad.setStartPointId("Central Plains");
        longRoad.setWidthBlocks(5);
        longRoad.setWidth(FlowWidth.MEDIUM);
        longRoad.setType(FlowType.ROAD);
        composition.getFeatures().add(longRoad);

        return composition;
    }

    /**
     * Tests road through empty space (edge case - ocean should be auto-filled).
     * Creates two distant biomes with a road between them crossing empty space.
     */
    @Test
    public void testRoadThroughEmptySpace() throws Exception {
        log.info("=== Testing Road Through Empty Space (Edge Case) ===");

        HexComposition composition = createCompositionWithDistantBiomes();
        exportInputModel(composition, "road-through-empty-space");

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("empty-space-test-world")
            .seed(99999L)
            .fillGaps(true)
            .oceanBorderRings(0) // No coast rings to force the flow-gap scenario
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getBiomePlacementResult(), "Should have placement result");
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");
        assertNotNull(result.getFillResult(), "Should have fill result");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Composition successful:");
        log.info("- Total biomes: {}", result.getTotalBiomes());
        log.info("- Total flows: {}", result.getTotalFlows());
        log.info("- Flow segments: {}", result.getFlowCompositionResult().getTotalSegments());
        log.info("- Total grids: {}", fillResult.getTotalGridCount());

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }
        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "road-through-empty-space");

        // Export generated model
        exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "road-through-empty-space");

        log.info("=== Road Through Empty Space Test Completed ===");
    }

    /**
     * Tests walls crossing between four biomes (N, S, E, W).
     * Creates two walls: one from N to S, one from E to W.
     */
    @Test
    public void testCrossingWalls() throws Exception {
        log.info("=== Testing Crossing Walls (N-S and E-W) ===");

        HexComposition composition = createCompositionWithCrossingWalls();
        exportInputModel(composition, "crossing-walls");

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("crossing-walls-world")
            .seed(77777L)
            .fillGaps(true)
            .oceanBorderRings(0)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getBiomePlacementResult(), "Should have placement result");
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");
        assertNotNull(result.getFillResult(), "Should have fill result");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Composition successful:");
        log.info("- Total biomes: {}", result.getTotalBiomes());
        log.info("- Total flows: {}", result.getTotalFlows());
        log.info("- Flow segments: {}", result.getFlowCompositionResult().getTotalSegments());
        log.info("- Total grids: {}", fillResult.getTotalGridCount());

        // Verify walls were composed
        assertEquals(2, result.getFlowCompositionResult().getComposedFlows(),
            "Should have composed 2 walls");

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }
        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "crossing-walls");

        // Export generated model
        exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "crossing-walls");

        log.info("=== Crossing Walls Test Completed ===");
    }

    /**
     * Creates composition with 4 biomes in corners and 2 crossing diagonal walls.
     *
     * Biome layout (with different heights for visual distinction):
     * - NW (links oben): HIGH_PEAKS (landLevel=150, landOffset=40, max ~240) - dunkel
     * - NE (rechts oben): MEDIUM_PEAKS (landLevel=120, landOffset=30, max ~200)
     * - SW (links unten): LOW_PEAKS (landLevel=100, landOffset=20, max ~170)
     * - SE (rechts unten): MEADOW (landLevel=80, landOffset=10, max ~140) - hell
     *
     * Walls (diagonal, crossing in the middle forming an X):
     * - Diagonal 1: NW → SE (links oben → rechts unten, stone, height 6)
     * - Diagonal 2: NE → SW (rechts oben → links unten, brick, height 5)
     */
    private HexComposition createCompositionWithCrossingWalls() {
        HexComposition composition = HexComposition.builder()
            .name("Crossing Walls Test")
            .worldId("crossing-walls-test")
            .features(new ArrayList<>())
            .build();

        // NW biome (links oben) - High Mountains (landLevel=150, landOffset=40)
        MountainBiome nwBiome = new MountainBiome();
        nwBiome.setName("Northwest Tower");
        nwBiome.setType(BiomeType.MOUNTAINS);
        nwBiome.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);
        nwBiome.setShape(AreaShape.CIRCLE);
        nwBiome.setSizeFrom(2);
        nwBiome.setSizeTo(3);
        nwBiome.setPositions(List.of(createPosition(Direction.NW, 315, 6, 6, "origin", 8)));
        composition.getFeatures().add(nwBiome);

        // NE biome (rechts oben) - Medium Mountains (landLevel=120, landOffset=30)
        MountainBiome neBiome = new MountainBiome();
        neBiome.setName("Northeast Tower");
        neBiome.setType(BiomeType.MOUNTAINS);
        neBiome.setHeight(MountainBiome.MountainHeight.MEDIUM_PEAKS);
        neBiome.setShape(AreaShape.CIRCLE);
        neBiome.setSizeFrom(2);
        neBiome.setSizeTo(3);
        neBiome.setPositions(List.of(createPosition(Direction.NE, 45, 6, 6, "origin", 7)));
        composition.getFeatures().add(neBiome);

        // SW biome (links unten) - Low Mountains (landLevel=100, landOffset=20)
        MountainBiome swBiome = new MountainBiome();
        swBiome.setName("Southwest Tower");
        swBiome.setType(BiomeType.MOUNTAINS);
        swBiome.setHeight(MountainBiome.MountainHeight.LOW_PEAKS);
        swBiome.setShape(AreaShape.CIRCLE);
        swBiome.setSizeFrom(2);
        swBiome.setSizeTo(3);
        swBiome.setPositions(List.of(createPosition(Direction.SW, 225, 6, 6, "origin", 6)));
        composition.getFeatures().add(swBiome);

        // SE biome (rechts unten) - Meadow (landLevel=80, landOffset=10)
        MountainBiome seBiome = new MountainBiome();
        seBiome.setName("Southeast Tower");
        seBiome.setType(BiomeType.MOUNTAINS);
        seBiome.setHeight(MountainBiome.MountainHeight.MEADOW);
        seBiome.setShape(AreaShape.CIRCLE);
        seBiome.setSizeFrom(2);
        seBiome.setSizeTo(3);
        seBiome.setPositions(List.of(createPosition(Direction.SE, 135, 6, 6, "origin", 5)));
        composition.getFeatures().add(seBiome);

        // Wall from NW to SE (diagonal: links oben → rechts unten)
        Wall diagonal1Wall = Wall.builder()
            .height(6)
            .level(50)
            .material("stone")
            .waypointIds(new ArrayList<>())
            .endPointId("Southeast Tower")
            .build();
        diagonal1Wall.setName("NW-SE Diagonal Wall");
        diagonal1Wall.setFeatureId("diagonal1-wall");
        diagonal1Wall.setStartPointId("Northwest Tower");
        diagonal1Wall.setWidthBlocks(2);
        diagonal1Wall.setWidth(FlowWidth.MEDIUM);
        diagonal1Wall.setType(FlowType.WALL);
        composition.getFeatures().add(diagonal1Wall);

        // Wall from NE to SW (diagonal: rechts oben → links unten, kreuzt diagonal1)
        Wall diagonal2Wall = Wall.builder()
            .height(5)
            .level(50)
            .material("brick")
            .waypointIds(new ArrayList<>())
            .endPointId("Southwest Tower")
            .build();
        diagonal2Wall.setName("NE-SW Diagonal Wall");
        diagonal2Wall.setFeatureId("diagonal2-wall");
        diagonal2Wall.setStartPointId("Northeast Tower");
        diagonal2Wall.setWidthBlocks(2);
        diagonal2Wall.setWidth(FlowWidth.MEDIUM);
        diagonal2Wall.setType(FlowType.WALL);
        composition.getFeatures().add(diagonal2Wall);

        return composition;
    }

    /**
     * Tests a river flowing from mountain to coast with curves.
     */
    @Test
    public void testRiverWithCurves() throws Exception {
        log.info("=== Testing River with Curves ===");

        HexComposition composition = createCompositionWithRiver();
        exportInputModel(composition, "river-curved");

        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("river-test-world")
            .seed(88888L)
            .fillGaps(true)
            .oceanBorderRings(2)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed");
        assertNotNull(result.getBiomePlacementResult(), "Should have placement result");
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");
        assertNotNull(result.getFillResult(), "Should have fill result");

        HexGridFillResult fillResult = result.getFillResult();

        log.info("Composition successful:");
        log.info("- Total biomes: {}", result.getTotalBiomes());
        log.info("- Total flows: {}", result.getTotalFlows());
        log.info("- Flow segments: {}", result.getFlowCompositionResult().getTotalSegments());
        log.info("- Total grids: {}", fillResult.getTotalGridCount());

        // Verify river was composed
        assertEquals(1, result.getFlowCompositionResult().getComposedFlows(),
            "Should have composed 1 river");

        // Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }
        log.info("Built terrain for {} grids", flats.size());

        // Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Create composite image
        createCompositeImage(flats, fillResult, "river-curved");

        // Export generated model
        exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "river-curved");

        log.info("=== River with Curves Test Completed ===");
    }

    /**
     * Creates composition with mountain biome, coast biome, and a curved river.
     * River flows downhill from mountain to coast, then into ocean.
     */
    private HexComposition createCompositionWithRiver() {
        HexComposition composition = HexComposition.builder()
            .name("River Test - Large Landmass")
            .worldId("river-test")
            .features(new ArrayList<>())
            .build();

        // Create a large continuous landmass with overlapping biomes
        // River flows: Mountain (NW) → Plains (Center) → Forest (SE) → Ocean
        // Strategy: Make biomes large and overlapping to ensure connectivity

        // 1. Very large central plains biome (covers most of the area)
        Biome plains = createBiome("Central Plains", BiomeType.PLAINS, AreaShape.CIRCLE,
            15, 18, Direction.N, 0, 0, 0, "origin", 5);
        plains.getParameters().put("g_asl", "75");  // Medium height
        composition.getFeatures().add(plains);

        // 2. Mountain source (NW, overlapping with plains)
        MountainBiome mountain = new MountainBiome();
        mountain.setName("Mountain Source");
        mountain.setType(BiomeType.MOUNTAINS);
        mountain.setHeight(MountainBiome.MountainHeight.HIGH_PEAKS);  // landLevel=150
        mountain.setShape(AreaShape.CIRCLE);
        mountain.setSizeFrom(6);
        mountain.setSizeTo(7);
        // Distance 4 from origin (NW) - overlaps with plains
        mountain.setPositions(List.of(createPosition(Direction.NW, 300, 4, 5, "origin", 10)));
        composition.getFeatures().add(mountain);

        // 3. Forest biome (E/SE of center, overlapping with plains)
        Biome forest = createBiome("Eastern Forest", BiomeType.FOREST, AreaShape.CIRCLE,
            8, 10, Direction.SE, 120, 4, 5, "origin", 8);
        forest.getParameters().put("g_asl", "65");  // Lower than plains, higher than coast
        composition.getFeatures().add(forest);

        // 4. Coast/lowlands at the SE edge (overlapping with forest)
        Biome coast = createBiome("Coastal Lowlands", BiomeType.COAST, AreaShape.CIRCLE,
            5, 6, Direction.SE, 120, 10, 11, "origin", 7);
        coast.getParameters().put("g_asl", "55");  // Just above ocean level
        composition.getFeatures().add(coast);

        // 5. River from mountain to SE (flowing through multiple biomes)
        // River flows downhill: 150 (mountain) → 75 (plains) → 65 (forest) → ocean
        // Note: mergeToId points to Coastal Lowlands, but river will naturally flow to ocean
        // With force=false, river stops when reaching ocean without error
        River river = River.builder()
            .depth(3)
            .level(60)  // Water level - can flow through terrain >= 50 (ocean level)
            .waypointIds(new ArrayList<>())
            .mergeToId("Coastal Lowlands")  // Intended destination (may not reach if ocean comes first)
            .force(false)  // false: stop gracefully at ocean, true: throw error if destination not reached
            .build();
        river.setName("Great River");
        river.setFeatureId("great-river");
        river.setStartPointId("Mountain Source");
        river.setWidthBlocks(20);  // Wide river: 20 blocks
        river.setWidth(FlowWidth.LARGE);
        river.setType(FlowType.RIVER);
        river.setTendRight(DeviationTendency.MODERATE);  // Natural meandering curves
        river.setTendLeft(DeviationTendency.SLIGHT);     // Asymmetric curves
        composition.getFeatures().add(river);

        return composition;
    }

    /**
     * Creates composition with two distant biomes (forcing empty space between them).
     */
    private HexComposition createCompositionWithDistantBiomes() {
        HexComposition composition = HexComposition.builder()
            .name("Distant Biomes Test")
            .worldId("empty-space-test")
            .features(new ArrayList<>())
            .build();

        // West biome: Small island
        Biome westIsland = createBiome("West Island", BiomeType.PLAINS, AreaShape.CIRCLE,
            2, 3, Direction.N, 0, 0, 0, "origin", 8);
        westIsland.getParameters().put("decoupled", "true"); // Prevent auto-ocean-connection
        composition.getFeatures().add(westIsland);

        // East biome: Small island (far away)
        Biome eastIsland = createBiome("East Island", BiomeType.FOREST, AreaShape.CIRCLE,
            2, 3, Direction.E, 0, 20, 0, "origin", 7);
        eastIsland.getParameters().put("decoupled", "true"); // Prevent auto-ocean-connection
        composition.getFeatures().add(eastIsland);

        // Road connecting the islands (will cross empty space)
        Road bridgeRoad = Road.builder()
            .roadType("bridge")
            .level(95)
            .waypointIds(new ArrayList<>())
            .endPointId("East Island")
            .build();
        bridgeRoad.setName("Bridge Road");
        bridgeRoad.setFeatureId("bridge-road");
        bridgeRoad.setStartPointId("West Island");
        bridgeRoad.setWidthBlocks(3);
        bridgeRoad.setWidth(FlowWidth.MEDIUM);
        bridgeRoad.setType(FlowType.ROAD);
        composition.getFeatures().add(bridgeRoad);

        return composition;
    }

    /**
     * Creates a Polygon representing a pointy-top hexagon with 6 vertices.
     * Vertices are positioned at angles: -30°, 30°, 90°, 150°, 210°, 270°
     * This matches the geometry used in HexMathUtil.
     *
     * @param centerX The X coordinate of the hexagon center
     * @param centerZ The Z coordinate of the hexagon center
     * @param gridSize The diameter of the hexagon in blocks
     * @return Polygon with 6 vertices representing the hexagon outline
     */
}
