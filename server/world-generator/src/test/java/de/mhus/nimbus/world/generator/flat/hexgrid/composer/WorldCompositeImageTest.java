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
            village.getParameters().put("village", centerGridConfig.toVillageParameter());
            village.getParameters().put("road", centerGridConfig.toRoadParameter());
        }

        composition.getFeatures().add(village);

        // Add Roads as Flow features
        Road mainRoad = Road.builder()
            .roadType("street")
            .level(95)
            .build();
        mainRoad.setName("Main Road");
        mainRoad.setFeatureId("main-road");
        mainRoad.setWidthBlocks(5);
        mainRoad.setStartPoint(HexVector2.builder().q(0).r(0).build());
        mainRoad.setEndPoint(HexVector2.builder().q(1).r(0).build());
        mainRoad.setType(FlowType.ROAD);
        composition.getFeatures().add(mainRoad);

        Road northRoad = Road.builder()
            .roadType("street")
            .level(95)
            .build();
        northRoad.setName("North Road");
        northRoad.setFeatureId("north-road");
        northRoad.setWidthBlocks(4);
        northRoad.setStartPoint(HexVector2.builder().q(0).r(0).build());
        northRoad.setEndPoint(HexVector2.builder().q(0).r(-1).build());
        northRoad.setType(FlowType.ROAD);
        composition.getFeatures().add(northRoad);

        // Add River as Flow feature
        River mountainRiver = River.builder()
            .depth(2)
            .level(45)
            .build();
        mountainRiver.setName("Mountain River");
        mountainRiver.setFeatureId("mountain-river");
        mountainRiver.setWidthBlocks(3);
        mountainRiver.setStartPoint(HexVector2.builder().q(0).r(-2).build());
        mountainRiver.setEndPoint(HexVector2.builder().q(0).r(-1).build());
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
        // Calculate bounds and find cartesian extents
        int minQ = Integer.MAX_VALUE, maxQ = Integer.MIN_VALUE;
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;

        for (HexVector2 coord : flats.keySet()) {
            minQ = Math.min(minQ, coord.getQ());
            maxQ = Math.max(maxQ, coord.getQ());
            minR = Math.min(minR, coord.getR());
            maxR = Math.max(maxR, coord.getR());
        }

        int gridWidth = maxQ - minQ + 1;
        int gridHeight = maxR - minR + 1;

        log.info("Creating HEX composite: {}x{} grids, bounds q=[{},{}] r=[{},{}]",
            gridWidth, gridHeight, minQ, maxQ, minR, maxR);

        // Calculate cartesian bounds using HexMathUtil
        double cartMinX = Double.MAX_VALUE, cartMaxX = Double.MIN_VALUE;
        double cartMinZ = Double.MAX_VALUE, cartMaxZ = Double.MIN_VALUE;

        for (HexVector2 coord : flats.keySet()) {
            double[] cartesian = HexMathUtil.hexToCartesian(coord, FLAT_SIZE);
            double halfSize = FLAT_SIZE / 2.0;

            cartMinX = Math.min(cartMinX, cartesian[0] - halfSize);
            cartMaxX = Math.max(cartMaxX, cartesian[0] + halfSize);
            cartMinZ = Math.min(cartMinZ, cartesian[1] - halfSize);
            cartMaxZ = Math.max(cartMaxZ, cartesian[1] + halfSize);
        }

        int imageWidth = (int) Math.ceil(cartMaxX - cartMinX);
        int imageHeight = (int) Math.ceil(cartMaxZ - cartMinZ);

        log.info("HEX composite cartesian bounds: x=[{},{}] z=[{},{}], image size={}x{}",
            (int)cartMinX, (int)cartMaxX, (int)cartMinZ, (int)cartMaxZ, imageWidth, imageHeight);

        BufferedImage levelImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage materialImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D levelG = levelImage.createGraphics();
        Graphics2D materialG = materialImage.createGraphics();

        // Black background
        levelG.setColor(Color.BLACK);
        levelG.fillRect(0, 0, imageWidth, imageHeight);
        materialG.setColor(Color.BLACK);
        materialG.fillRect(0, 0, imageWidth, imageHeight);

        // Render each flat with HEX geometry
        int renderedCount = 0;
        for (Map.Entry<HexVector2, WFlat> entry : flats.entrySet()) {
            HexVector2 coord = entry.getKey();
            WFlat flat = entry.getValue();

            // Calculate cartesian center position
            double[] cartesian = HexMathUtil.hexToCartesian(coord, FLAT_SIZE);
            double hexCenterX = cartesian[0] - cartMinX;
            double hexCenterZ = cartesian[1] - cartMinZ;

            try {
                // Create flat images
                FlatLevelImageCreator levelCreator = new FlatLevelImageCreator(flat);
                byte[] levelBytes = levelCreator.create(false);
                BufferedImage flatLevelImage = ImageIO.read(new ByteArrayInputStream(levelBytes));

                FlatMaterialImageCreator materialCreator = new FlatMaterialImageCreator(flat);
                byte[] materialBytes = materialCreator.create(false);
                BufferedImage flatMaterialImage = ImageIO.read(new ByteArrayInputStream(materialBytes));

                // Render only pixels that are inside the hexagon
                int halfSize = FLAT_SIZE / 2;
                int startX = Math.max(0, (int)(hexCenterX - halfSize));
                int endX = Math.min(imageWidth, (int)(hexCenterX + halfSize));
                int startZ = Math.max(0, (int)(hexCenterZ - halfSize));
                int endZ = Math.min(imageHeight, (int)(hexCenterZ + halfSize));

                for (int z = startZ; z < endZ; z++) {
                    for (int x = startX; x < endX; x++) {
                        // Check if this pixel is inside the hexagon
                        if (HexMathUtil.isPointInHex(x, z, hexCenterX, hexCenterZ, FLAT_SIZE)) {
                            // Calculate source pixel coordinates in flat image
                            int flatX = (int)(x - hexCenterX + halfSize);
                            int flatZ = (int)(z - hexCenterZ + halfSize);

                            if (flatX >= 0 && flatX < FLAT_SIZE && flatZ >= 0 && flatZ < FLAT_SIZE) {
                                // Copy pixel from flat image to composite
                                int levelPixel = flatLevelImage.getRGB(flatX, flatZ);
                                int materialPixel = flatMaterialImage.getRGB(flatX, flatZ);

                                levelImage.setRGB(x, z, levelPixel);
                                materialImage.setRGB(x, z, materialPixel);
                            }
                        }
                    }
                }

                renderedCount++;
            } catch (Exception e) {
                log.warn("Failed to render grid [{},{}]: {}", coord.getQ(), coord.getR(), e.getMessage());
            }
        }

        log.info("Rendered {} of {} grids with HEX geometry", renderedCount, flats.size());

        // Draw hexagon grid lines
        levelG.setColor(new Color(255, 255, 255, 60));
        materialG.setColor(new Color(255, 255, 255, 60));
        levelG.setStroke(new BasicStroke(2));
        materialG.setStroke(new BasicStroke(2));

        for (HexVector2 coord : flats.keySet()) {
            double[] cartesian = HexMathUtil.hexToCartesian(coord, FLAT_SIZE);
            double hexCenterX = cartesian[0] - cartMinX;
            double hexCenterZ = cartesian[1] - cartMinZ;

            // Draw hexagon outline
            Polygon hexagon = createHexagonPolygon(hexCenterX, hexCenterZ, FLAT_SIZE);
            levelG.draw(hexagon);
            materialG.draw(hexagon);
        }

        levelG.dispose();
        materialG.dispose();

        // Save images
        File levelFile = outputDir.resolve(name + "-level.png").toFile();
        File materialFile = outputDir.resolve(name + "-material.png").toFile();

        ImageIO.write(levelImage, "PNG", levelFile);
        ImageIO.write(materialImage, "PNG", materialFile);

        log.info("Saved composite level image: {} ({}x{} pixels)",
            levelFile.getAbsolutePath(), imageWidth, imageHeight);
        log.info("Saved composite material image: {} ({}x{} pixels)",
            materialFile.getAbsolutePath(), imageWidth, imageHeight);

        // Also save individual grid info
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

        RelativePosition pos = RelativePosition.builder()
            .direction(direction)
            .distanceFrom(distFrom)
            .distanceTo(distTo)
            .anchor(anchor)
            .priority(priority)
            .build();

        biome.setPositions(Arrays.asList(pos));

        // Prepare biome for tests that bypass HexCompositionPreparer
        biome.prepareForComposition();

        return biome;
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
    private Polygon createHexagonPolygon(double centerX, double centerZ, int gridSize) {
        double radius = gridSize / 2.0;
        int[] xPoints = new int[6];
        int[] zPoints = new int[6];

        // Create 6 vertices at 60-degree intervals, starting at -30 degrees
        // This creates a pointy-top hexagon (point facing up)
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            xPoints[i] = (int) Math.round(centerX + radius * Math.cos(angle));
            zPoints[i] = (int) Math.round(centerZ + radius * Math.sin(angle));
        }

        return new Polygon(xPoints, zPoints, 6);
    }
}
