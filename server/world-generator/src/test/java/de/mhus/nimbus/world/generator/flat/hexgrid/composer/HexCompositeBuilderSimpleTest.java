package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.flat.BorderSmoothManipulator;
import de.mhus.nimbus.world.generator.flat.FlatManipulator;
import de.mhus.nimbus.world.generator.flat.FlatManipulatorService;
import de.mhus.nimbus.world.generator.flat.FlatTerrainManipulator;
import de.mhus.nimbus.world.generator.flat.HillyTerrainManipulator;
import de.mhus.nimbus.world.generator.flat.IslandsManipulator;
import de.mhus.nimbus.world.generator.flat.NormalTerrainManipulator;
import de.mhus.nimbus.world.generator.flat.SoftenManipulator;
import de.mhus.nimbus.world.generator.flat.hexgrid.BuilderContext;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilder;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilderService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for HexCompositeBuilder - orchestrates complete composition pipeline
 */
@Slf4j
public class HexCompositeBuilderSimpleTest {

    private static final int FLAT_SIZE = 512;
    private static final int OCEAN_LEVEL = 50;

    private WHexGridRepository mockRepository;
    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        // Mock repository
        mockRepository = Mockito.mock(WHexGridRepository.class);

        // Output directory for images
        outputDir = Paths.get("target/test-output/hex-composite-simple");
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
    public void testSimpleContinent() throws Exception {
        simpleContinentTest("first");
    }

    public CompositionResult simpleContinentTest(String name) throws Exception {
        log.info("=== Testing %s System ===".formatted(name));

        // Load composition from JSON file
        File jsonFile = new File("src/test/resources/simple-test-%s.json".formatted(name));
        assertTrue(jsonFile.exists(), "Continent test JSON file '%s' should exist".formatted(name));

        ObjectMapper mapper = new ObjectMapper();
        HexComposition composition = mapper.readValue(jsonFile, HexComposition.class);

        assertNotNull(composition, "Composition %s should be loaded".formatted(name));
        log.info("Loaded continent test composition with {} features", composition.getFeatures().size());

        // Verify continent definitions
        assertNotNull(composition.getContinents(), "Should have continent definitions: %s".formatted(name));
        assertFalse(composition.getContinents().isEmpty(), "Should have at least one continent: %s".formatted(name));

        Continent mainContinent = composition.getContinents().get(0);
        log.info("Continent: {} (type={}, landLevel={}, landOffset={})",
            mainContinent.getName(), mainContinent.getBiomeType(),
            mainContinent.getParameters().get("landLevel"),
            mainContinent.getParameters().get("landOffset"));

        // Use HexCompositeBuilder for the complete pipeline
        log.info("Starting composition pipeline...");
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("continent-test-%s".formatted(name))
            .seed(42L)  // Consistent seed for reproducible results
            .fillGaps(true)
            .oceanBorderRings(2)
            .generateWHexGrids(false)  // Don't generate WHexGrids for this test
            .build()
            .compose();

        // Verify success
        assertTrue(result.isSuccess(), "Composition should succeed: " + result.getErrorMessage());
        assertNull(result.getErrorMessage(), "Should have no error message");

        // Log statistics
        log.info("=== Composition %s Statistics ===".formatted(name));
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
            log.info("Creating %s composite image...".formatted(name));
            createCompositeImage(flats, fillResult, "continent-test-%s".formatted(name));

            // Export generated model
            exportGeneratedModel(fillResult, result.getFlowCompositionResult(), "continent-test-%s".formatted(name));
        }

        // Export the processed input composition model
        exportInputComposition(composition, "simple-continent-test-%s".formatted(name));

        log.info("=== Simple Content Test %s Completed ===".formatted(name));
        log.info("Images saved to: {}", outputDir.toAbsolutePath());

        return result;
    }

    // ============= Helper Methods =============

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

        // Apply builder pipeline
        try {
            HexGridBuilderService builderService = new HexGridBuilderService();
            List<HexGridBuilder> pipeline = builderService.createBuilderPipeline(filled.getHexGrid());

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
