package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.flat.*;
import de.mhus.nimbus.world.generator.flat.hexgrid.*;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for HexCompositeBuilder - orchestrates complete composition pipeline
 */
@Slf4j
public class HexCompositeBuilderTest {

    private static final int FLAT_SIZE = 512;
    private static final int OCEAN_LEVEL = 50;

    private WHexGridRepository mockRepository;
    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        // Mock repository
        mockRepository = Mockito.mock(WHexGridRepository.class);

        // Output directory for images
        outputDir = Paths.get("target/test-output/hex-composite-builder");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());

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
        CompositionResult result = HexCompositeBuilder.builder()
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
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("river-world")
            .seed(54321L)
            .fillGaps(true)
            .oceanBorderRings(2)  // More border rings
            .build()
            .compose();

        // Verify successful
        assertTrue(result.isSuccess(), "Composition should succeed");

        // Verify river composition
        assertEquals(1, result.getTotalFlows(), "Should have 1 flow (river)");
        assertNotNull(result.getFlowCompositionResult(), "Should have flow result");

        // Verify filling with coast border and ocean connections
        assertNotNull(result.getFillResult(), "Should have fill result");
        HexGridFillResult fillResult = result.getFillResult();
        // Ocean grids may or may not exist (depends on if regions need connecting)
        assertTrue(fillResult.getCoastFillCount() > 0, "Should have coast grids");
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
        CompositionResult result = HexCompositeBuilder.builder()
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
        CompositionResult result1 = HexCompositeBuilder.builder()
            .composition(null)
            .worldId("test-world")
            .build()
            .compose();

        assertFalse(result1.isSuccess(), "Null composition should fail");
        assertEquals("Composition is null", result1.getErrorMessage());

        // Test null worldId
        HexComposition composition = createCompositionWithRoad();
        CompositionResult result2 = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId(null)
            .build()
            .compose();

        assertFalse(result2.isSuccess(), "Null worldId should fail");
        assertEquals("WorldId is required", result2.getErrorMessage());

        // Test blank worldId
        CompositionResult result3 = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("")
            .build()
            .compose();

        assertFalse(result3.isSuccess(), "Blank worldId should fail");
        assertEquals("WorldId is required", result3.getErrorMessage());

        log.info("=== Test completed successfully ===");
    }

    @Test
    public void testCompositionBuildFeatureInterface() {
        log.info("=== Testing BuildFeature Interface on HexComposition ===");

        // Create composition
        HexComposition composition = createCompositionWithRoad();

        // Use the new BuildFeature interface: composition.build(context)
        BuildContext context = BuildContext.builder()
            .worldId("build-feature-test")
            .seed(777L)
            .fillGaps(true)
            .oceanBorderRings(1)
            .generateWHexGrids(false)
            .build();

        // Call build() on the composition (implements BuildFeature)
        CompositionResult result = composition.build(context);

        // Verify successful
        assertTrue(result.isSuccess(), "Build should succeed");
        assertNull(result.getErrorMessage(), "Should have no error");

        // Verify results
        assertEquals(2, result.getTotalBiomes(), "Should have 2 biomes");
        assertEquals(1, result.getTotalFlows(), "Should have 1 flow");
        assertTrue(result.getFilledGrids() > 0, "Should have filled grids");

        log.info("Built composition with {} biomes, {} flows, {} total grids",
            result.getTotalBiomes(),
            result.getTotalFlows(),
            result.getFilledGrids());

        // Test simplified BuildContext.of() factory
        BuildContext simpleContext = BuildContext.of("simple-world", 999L);
        CompositionResult result2 = composition.build(simpleContext);

        assertTrue(result2.isSuccess(), "Build with simple context should succeed");

        log.info("=== Test completed successfully ===");
    }

    @Test
    @Disabled // Disabled by default due to long runtime, currently no propper results
    public void testMiddleEarthComposition() throws Exception {
        log.info("=== Testing Middle-earth Composition ===");

        // Load composition from JSON file
        File jsonFile = new File("src/test/resources/mittelerde-draft-1.json");
        assertTrue(jsonFile.exists(), "Middle-earth JSON file should exist");

        ObjectMapper mapper = new ObjectMapper();
        HexComposition composition = mapper.readValue(jsonFile, HexComposition.class);

        assertNotNull(composition, "Composition should be loaded");
        assertEquals("Middle-earth (Spec-aligned)", composition.getName());
        assertEquals("me-spec-003", composition.getWorldId());
        log.info("Loaded Middle-earth composition with {} features", composition.getFeatures().size());

        // Use HexCompositeBuilder for the complete pipeline
        log.info("Starting composition pipeline...");
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("middle-earth")
            .seed(42L)  // Consistent seed for reproducible results
            .fillGaps(true)
            .oceanBorderRings(2)
            .generateWHexGrids(false)  // Don't generate WHexGrids for this test
            .build()
            .compose();

        // Note: mittelerde-draft-1.json is complex and some biomes may fail to place
        // We're mainly testing that the composition runs and ContinentFiller works
        if (!result.isSuccess()) {
            log.warn("Composition had issues: {}", result.getErrorMessage());
            log.warn("Warnings: {}", result.getWarnings());
        }

        // Log statistics
        log.info("=== Composition Statistics ===");
        log.info("Biomes placed: {}", result.getTotalBiomes());
        log.info("Initial hex grids: {}", result.getTotalGrids());
        log.info("Points placed: {}", countPoints(composition));

        if (result.getFillResult() != null) {
            HexGridFillResult fillResult = result.getFillResult();
            log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
                fillResult.getTotalGridCount(),
                fillResult.getOceanFillCount(),
                fillResult.getLandFillCount(),
                fillResult.getCoastFillCount());
        }

        if (result.getFlowCompositionResult() != null) {
            FlowComposer.FlowCompositionResult flowResult = result.getFlowCompositionResult();
            log.info("Flows composed: {}/{} (failed: {})",
                flowResult.getComposedFlows(),
                flowResult.getTotalFlows(),
                flowResult.getFailedFlows());
            log.info("Total flow segments: {}", flowResult.getTotalSegments());

            if (!flowResult.getErrors().isEmpty()) {
                log.warn("Flow composition errors: {}", flowResult.getErrors());
            }
        }

        // Build terrain for all grids
        log.info("Building terrain for all grids...");
        Map<HexVector2, WFlat> flats = new HashMap<>();
        HexGridFillResult fillResult = result.getFillResult();

        if (fillResult != null) {
            for (FilledHexGrid filled : fillResult.getAllGrids()) {
                try {
                    WFlat flat = buildGridTerrain(filled);
                    flats.put(filled.getCoordinate(), flat);
                } catch (Exception e) {
                    log.warn("Failed to build terrain for grid {}: {}", filled.getCoordinate(), e.getMessage());
                }
            }
            log.info("Built terrain for {}/{} grids", flats.size(), fillResult.getAllGrids().size());

            // Create composite image
            log.info("Creating composite image...");
            createCompositeImage(flats, fillResult, "middle-earth");

            // Export generated model
            exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "middle-earth");
        }

        // Export the processed input composition model
        exportInputComposition(composition, "middle-earth");

        log.info("=== Middle-earth Composition Test Completed ===");
        log.info("Images saved to: {}", outputDir.toAbsolutePath());
    }

    @Test
    public void testMiddleEarthWithContinents() throws Exception {
        log.info("=== Testing Continent System ===");

        // Load composition from JSON file
        File jsonFile = new File("src/test/resources/continent-test.json");
        assertTrue(jsonFile.exists(), "Continent test JSON file should exist");

        ObjectMapper mapper = new ObjectMapper();
        HexComposition composition = mapper.readValue(jsonFile, HexComposition.class);

        assertNotNull(composition, "Composition should be loaded");
        assertEquals("Continent Test World", composition.getName());
        assertEquals("continent-test-001", composition.getWorldId());
        log.info("Loaded continent test composition with {} features", composition.getFeatures().size());

        // Verify continent definitions
        assertNotNull(composition.getContinents(), "Should have continent definitions");
        assertFalse(composition.getContinents().isEmpty(), "Should have at least one continent");

        Continent mainContinent = composition.getContinents().get(0);
        assertEquals("main-continent", mainContinent.getContinentId());
        assertEquals("Main Continent", mainContinent.getName());
        assertEquals(BiomeType.MOUNTAINS, mainContinent.getBiomeType());
        assertEquals("80", mainContinent.getParameters().get("g_asl"), "Should use MEADOW landLevel");
        // TODO check this: assertEquals("10", mainContinent.getParameters().get("g_offset"), "Should use MEADOW landOffset");
        log.info("Continent: {} (type={}, landLevel={}, landOffset={})",
            mainContinent.getName(), mainContinent.getBiomeType(),
            mainContinent.getParameters().get("g_asl"),
            mainContinent.getParameters().get("g_offset"));

        // Count features with continentId
        long featuresWithContinentId = composition.getFeatures().stream()
            .filter(f -> f instanceof Area)
            .map(f -> (Area) f)
            .filter(a -> a.getContinentId() != null)
            .count();
        log.info("Features with continentId: {}", featuresWithContinentId);
        assertTrue(featuresWithContinentId > 0, "Should have features with continentId");

        // Use HexCompositeBuilder for the complete pipeline
        log.info("Starting composition pipeline...");
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("continent-test")
            .seed(42L)  // Consistent seed for reproducible results
            .fillGaps(true)
            .oceanBorderRings(2)
            .generateWHexGrids(false)  // Don't generate WHexGrids for this test
            .build()
            .compose();

        // Verify successful
        assertTrue(result.isSuccess(), "Composition should succeed: " + result.getErrorMessage());
        assertNull(result.getErrorMessage(), "Should have no error message");

        // Log statistics
        log.info("=== Composition Statistics ===");
        log.info("Biomes placed: {}", result.getTotalBiomes());
        log.info("Initial hex grids: {}", result.getTotalGrids());

        if (result.getFillResult() != null) {
            HexGridFillResult fillResult = result.getFillResult();
            log.info("Total grids after filling: {} (Mountain: {}, Lowland: {}, Continent: {}, Coast: {}, Ocean: {})",
                fillResult.getTotalGridCount(),
                fillResult.getMountainFillCount(),
                fillResult.getLandFillCount(),
                fillResult.getContinentFillCount(),
                fillResult.getCoastFillCount(),
                fillResult.getOceanFillCount());

            // Verify continent filler added grids
            assertTrue(fillResult.getContinentFillCount() > 0,
                "ContinentFiller should have added grids to connect biomes");
            log.info("SUCCESS: ContinentFiller added {} grids", fillResult.getContinentFillCount());

            // Verify continent filler biomes
            long continentFillerBiomes = fillResult.getAllGrids().stream()
                .filter(grid -> grid.getHexGrid() != null && grid.getHexGrid().getParameters() != null)
                .filter(grid -> "true".equals(grid.getHexGrid().getParameters().get("continentFiller")))
                .count();
            log.info("Continent filler biomes created: {}", continentFillerBiomes);
            assertTrue(continentFillerBiomes > 0, "Should have continent filler biomes");
        }

        if (result.getFlowCompositionResult() != null) {
            FlowComposer.FlowCompositionResult flowResult = result.getFlowCompositionResult();
            log.info("Flows composed: {}/{} (failed: {})",
                flowResult.getComposedFlows(),
                flowResult.getTotalFlows(),
                flowResult.getFailedFlows());
            log.info("Total flow segments: {}", flowResult.getTotalSegments());

            if (!flowResult.getErrors().isEmpty()) {
                log.warn("Flow composition errors: {}", flowResult.getErrors());
            }
        }

        // Build terrain for all grids
        log.info("Building terrain for all grids...");
        Map<HexVector2, WFlat> flats = new HashMap<>();
        HexGridFillResult fillResult = result.getFillResult();

        if (fillResult != null) {
            for (FilledHexGrid filled : fillResult.getAllGrids()) {
                try {
                    WFlat flat = buildGridTerrain(filled);
                    flats.put(filled.getCoordinate(), flat);
                } catch (Exception e) {
                    log.warn("Failed to build terrain for grid {}: {}", filled.getCoordinate(), e.getMessage());
                }
            }
            log.info("Built terrain for {}/{} grids", flats.size(), fillResult.getAllGrids().size());

            // Create composite image
            log.info("Creating composite image...");
            createCompositeImage(flats, fillResult, "continent-test");

            // Export generated model
            exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "continent-test");
        }

        log.info("=== Continent System Test Completed ===");
        log.info("Images saved to: {}", outputDir.toAbsolutePath());
    }

    // ============= Helper Methods =============

    private int countPoints(HexComposition composition) {
        if (composition.getFeatures() == null) return 0;
        return (int) composition.getFeatures().stream()
            .filter(f -> f instanceof Point)
            .count();
    }

    private void exportInputModel(HexComposition composition, String name) throws Exception {
        File inputFile = outputDir.resolve(name + "-input-model.json").toFile();

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(inputFile, composition);

        log.info("Exported input model to: {}", inputFile.getAbsolutePath());
    }

    private WFlat buildGridTerrain(FilledHexGrid filled) {
        // Initialize level and column arrays
        byte[] levels = new byte[FLAT_SIZE * FLAT_SIZE];
        byte[] columns = new byte[FLAT_SIZE * FLAT_SIZE];

        // Determine builder type
        String builderType = "island";  // Default
        if (filled.getHexGrid() != null && filled.getHexGrid().getParameters() != null) {
            builderType = filled.getHexGrid().getParameters().get("g_builder");
            if (builderType == null) {
                builderType = "island";
            }
        } else if (filled.isFiller()) {
            builderType = switch (filled.getFillerType()) {
                case OCEAN -> BiomeType.OCEAN.getBuilderName();
                case LAND -> BiomeType.ISLAND.getBuilderName();
                case COAST -> BiomeType.COAST.getBuilderName();
                default -> BiomeType.COAST.getBuilderName();
            };
        } else if (filled.getBiome() != null && filled.getBiome().getBiome() != null) {
            builderType = switch (filled.getBiome().getBiome().getType()) {
                case MOUNTAINS -> BiomeType.MOUNTAINS.getBuilderName();
                case FOREST -> BiomeType.COAST.getBuilderName();
                case DESERT -> BiomeType.COAST.getBuilderName();
                case SWAMP -> BiomeType.COAST.getBuilderName();
                case PLAINS -> BiomeType.ISLAND.getBuilderName();
                case OCEAN -> BiomeType.OCEAN.getBuilderName();
                case COAST -> BiomeType.COAST.getBuilderName();
                case ISLAND -> BiomeType.ISLAND.getBuilderName();
                case VILLAGE -> BiomeType.VILLAGE.getBuilderName();
                case TOWN -> BiomeType.TOWN.getBuilderName();
            };
        } else {
            builderType = BiomeType.COAST.getBuilderName();
        }

        // Set g_builder parameter on hexGrid
        if (filled.getHexGrid().getParameters() == null) {
            filled.getHexGrid().setParameters(new HashMap<>());
        }
        filled.getHexGrid().getParameters().put("g_builder", builderType);

        // Initialize with base terrain based on type
        int baseLevel = getBuilderBaseLevel(builderType);
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (byte) baseLevel;
            columns[i] = 0;
        }

        WFlat flat = WFlat.builder()
            .flatId("flat-" + filled.getCoordinate().getQ() + "-" + filled.getCoordinate().getR())
            .worldId("middle-earth")
            .layerDataId("test-layer")
            .hexGrid(filled.getCoordinate())
            .sizeX(FLAT_SIZE)
            .sizeZ(FLAT_SIZE)
            .seaLevel(OCEAN_LEVEL)
            .mountX(FLAT_SIZE / 2)
            .mountZ(FLAT_SIZE / 2)
            .levels(levels)
            .columns(columns)
            .extraBlocks(new HashMap<>())
            .materials(new HashMap<>())
            .unknownProtected(false)
            .borderProtected(false)
            .build();

        // Apply builder pipeline
        try {
            HexGridBuilderService builderService = new HexGridBuilderService();
            List<HexGridBuilder> pipeline = builderService.createBuilderPipeline(filled.getHexGrid(), HexGridBuilderService.STEP.ALL);

            if (pipeline.isEmpty()) {
                log.warn("No builders in pipeline for grid [{},{}]",
                    filled.getCoordinate().getQ(), filled.getCoordinate().getR());
                return flat;
            }

            BuilderContext context = createContext(flat, filled.getHexGrid());

            for (HexGridBuilder builder : pipeline) {
                builder.setContext(context);
                builder.buildFlat();
            }
        } catch (Exception e) {
            log.warn("Failed to build terrain for grid [{},{}]: {}",
                filled.getCoordinate().getQ(), filled.getCoordinate().getR(),
                e.getMessage());
        }

        return flat;
    }

    private int getBuilderBaseLevel(String builderType) {
        return switch (builderType) {
            case "ocean" -> 40;
            case "coast" -> 48;
            case "island" -> 52;
            case "plains", "forest", "desert", "swamp" -> 55;
            case "mountain" -> 70;
            default -> 50;
        };
    }

    private BuilderContext createContext(WFlat flat, WHexGrid hexGrid) {
        // WHexGrid is already properly configured from FilledHexGrid

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

    private void createCompositeImage(Map<HexVector2, WFlat> flats,
                                     HexGridFillResult fillResult,
                                     String name) throws Exception {
        // Use the HexGridCompositeImageCreator helper class with builder pattern
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

    private void exportGeneratedModel(HexGridFillResult fillResult,
                                     FlowComposer.FlowCompositionResult flowResult,
                                     String name) throws Exception {
        File outputFile = outputDir.resolve(name + "-generated-model.json").toFile();

        Map<String, Object> model = new HashMap<>();
        model.put("totalGrids", fillResult.getTotalGridCount());
        model.put("oceanGrids", fillResult.getOceanFillCount());
        model.put("landGrids", fillResult.getLandFillCount());
        model.put("coastGrids", fillResult.getCoastFillCount());

        if (flowResult != null) {
            model.put("totalFlows", flowResult.getTotalFlows());
            model.put("composedFlows", flowResult.getComposedFlows());
            model.put("flowSegments", flowResult.getTotalSegments());
        }

        // Add grid list
        List<Map<String, Object>> grids = new ArrayList<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            Map<String, Object> gridInfo = new HashMap<>();
            gridInfo.put("coordinate", filled.getCoordinate().getQ() + "," + filled.getCoordinate().getR());
            gridInfo.put("isFiller", filled.isFiller());
            if (filled.isFiller()) {
                gridInfo.put("fillerType", filled.getFillerType().name());
            }
            if (filled.getBiome() != null && filled.getBiome().getBiome() != null) {
                gridInfo.put("biome", filled.getBiome().getBiome().getName());
                gridInfo.put("biomeType", filled.getBiome().getBiome().getType().name());
            }
            grids.add(gridInfo);
        }
        model.put("grids", grids);

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, model);

        log.info("Exported generated model to: {}", outputFile.getAbsolutePath());
    }

    private void exportInputComposition(HexComposition composition, String name) throws Exception {
        File outputFile = outputDir.resolve(name + "-input-composition.json").toFile();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, composition);

        log.info("Exported input composition to: {}", outputFile.getAbsolutePath());
    }

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

    @Test
    public void testContinentFilling() throws Exception {
        log.info("=== Testing Continent Filling ===");

        // Load composition from JSON file
        File jsonFile = new File("src/test/resources/continent-test.json");
        assertTrue(jsonFile.exists(), "Continent test JSON file should exist");

        ObjectMapper mapper = new ObjectMapper();
        HexComposition composition = mapper.readValue(jsonFile, HexComposition.class);

        assertNotNull(composition, "Composition should be loaded");
        assertNotNull(composition.getContinents(), "Continents should be defined");
        assertEquals(1, composition.getContinents().size(), "Should have 1 continent");
        assertEquals("main-continent", composition.getContinents().get(0).getContinentId());

        // Use HexCompositeBuilder for the complete pipeline
        log.info("Starting composition pipeline...");
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("continent-test")
            .seed(12345L)
            .fillGaps(true)
            .oceanBorderRings(2)
            .generateWHexGrids(false)
            .build()
            .compose();

        assertTrue(result.isSuccess(), "Composition should succeed: " + result.getErrorMessage());
        assertNotNull(result.getFillResult(), "Fill result should exist");

        // Check that continent filler was used
        BiomePlacementResult placementResult = result.getFillResult().getPlacementResult();
        assertNotNull(placementResult, "Placement result should exist");
        assertTrue(placementResult.getPlacedBiomes().size() > 3,
            "Should have more than the 3 original biomes (includes continent filler)");

        // Check for continent filler biome
        boolean foundContinentFiller = false;
        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            if (placed.getBiome().getName().startsWith("continent-filler-")) {
                foundContinentFiller = true;
                log.info("Found continent filler: {} with {} grids",
                    placed.getBiome().getName(), placed.getCoordinates().size());

                // Verify it's marked as continent filler
                Map<String, String> params = placed.getBiome().getParameters();
                assertNotNull(params, "Parameters should exist");
                assertEquals("true", params.get("continentFiller"), "Should be marked as continent filler");

                // Check continentId is set on biome object
                assertEquals("main-continent", placed.getBiome().getContinentId(), "Biome should have correct continent ID");

                // Also check it's in parameters
                assertEquals("main-continent", params.get("continentId"), "Parameters should have correct continent ID");
            }
        }

        assertTrue(foundContinentFiller, "Should have created continent filler biome");

        log.info("=== Continent Filling Test Completed ===");
    }
}
