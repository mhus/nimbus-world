package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
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
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridIndex;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for HexCompositeBuilder - orchestrates complete composition pipeline
 */
@Slf4j
public class HexCompositeBuilderSimpleTest {

    private static final int FLAT_SIZE = 400;
    private static final int OCEAN_LEVEL = 50;

    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        // Output directory for images
        outputDir = Paths.get("target/test-output/hex-composite-simple");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());
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
            mainContinent.getParameters().get("g_asl"),
            mainContinent.getParameters().get("g_offset"));

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

        // Build terrain for all grids in 3 phases
        log.info("Building terrain for all grids in 3 phases...");
        Map<HexVector2, WFlat> flats = new HashMap<>();
        HexGridFillResult fillResult = result.getFillResult();

        if (fillResult != null) {
            var allGrids = fillResult.getAllGrids();
            var index = new HexGridIndex(allGrids.stream().map(g -> g.getHexGrid()).toList());

            // ===== PHASE 1: GROUND - Create all basic terrains =====
            log.info("Phase 1 (GROUND): Creating basic terrain for {} grids", allGrids.size());
            for (FilledHexGrid filled : allGrids) {
                try {
                    WFlat flat = buildGridTerrain(filled, index);
                    flats.put(filled.getCoordinate(), flat);
                } catch (Exception e) {
                    log.warn("Phase 1 failed for grid {}: {}", filled.getCoordinate(), e.getMessage(), e);
                }
            }
            log.info("Phase 1 completed: {}/{} grids created", flats.size(), allGrids.size());

            // ===== PHASE 2: BLENDER - Blend all sides with neighbors =====
            log.info("Phase 2 (BLENDER): Blending sides for {} grids", allGrids.size());
            int blendedCount = 0;
            for (FilledHexGrid filled : allGrids) {
                try {
                    blendGridSides(filled, index, flats);
                    blendedCount++;
                } catch (Exception e) {
                    log.warn("Phase 2 failed for grid {}: {}", filled.getCoordinate(), e.getMessage(), e);
                }
            }
            log.info("Phase 2 completed: {}/{} grids blended", blendedCount, allGrids.size());

            // ===== PHASE 3: TERRAIN - Apply terrain features =====
            log.info("Phase 3 (TERRAIN): Applying terrain features for {} grids", allGrids.size());
            int terrainCount = 0;
            for (FilledHexGrid filled : allGrids) {
                try {
                    applyTerrainFeatures(filled, index, flats);
                    terrainCount++;
                } catch (Exception e) {
                    log.warn("Phase 3 failed for grid {}: {}", filled.getCoordinate(), e.getMessage(), e);
                }
            }
            log.info("Phase 3 completed: {}/{} grids processed", terrainCount, allGrids.size());

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

    private WFlat buildGridTerrain(FilledHexGrid filled, HexGridIndex gridIndex) {
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

        // Generate unique flatId using UUID based on coordinate
        String flatId = "flat-" + java.util.UUID.nameUUIDFromBytes(
            (filled.getCoordinate().getQ() + ":" + filled.getCoordinate().getR()).getBytes()
        );

        WFlat flat = WFlat.builder()
            .flatId(flatId)
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

        // ===== PHASE 1: GROUND - Create basic terrain =====
        log.debug("Phase 1 (GROUND): Building basic terrain for grid [{},{}]",
            filled.getCoordinate().getQ(), filled.getCoordinate().getR());

        try {
            HexGridBuilderService builderService = new HexGridBuilderService();
            List<HexGridBuilder> groundPipeline = builderService.createBuilderPipeline(
                filled.getHexGrid(), HexGridBuilderService.STEP.GROUND);

            if (!groundPipeline.isEmpty()) {
                BuilderContext context = createContext(flat, filled.getHexGrid(), gridIndex, null);
                for (HexGridBuilder builder : groundPipeline) {
                    builder.setContext(context);
                    builder.buildFlat();
                }
            }
        } catch (Exception e) {
            log.warn("Phase 1 (GROUND) failed for grid [{},{}]: {}",
                filled.getCoordinate().getQ(), filled.getCoordinate().getR(),
                e.getMessage(), e);
        }

        return flat;
    }

    /**
     * Phase 2: BLENDER - Blend sides with neighbors
     * Must be called after all flats are created in Phase 1
     */
    private void blendGridSides(FilledHexGrid filled, HexGridIndex gridIndex,
                                Map<HexVector2, WFlat> allFlats) {
        WFlat flat = allFlats.get(filled.getCoordinate());
        if (flat == null) {
            log.warn("Flat not found for grid [{},{}] in Phase 2",
                filled.getCoordinate().getQ(), filled.getCoordinate().getR());
            return;
        }

        log.debug("Phase 2 (BLENDER): Blending sides for grid [{},{}]",
            filled.getCoordinate().getQ(), filled.getCoordinate().getR());

        // Set neighbor flat IDs as parameters
        WHexGrid hexGrid = filled.getHexGrid();
        if (hexGrid.getParameters() == null) {
            hexGrid.setParameters(new HashMap<>());
        }

        for (WHexGrid.SIDE side : WHexGrid.SIDE.values()) {
            HexVector2 neighborPos = HexMathUtil.getNeighborPosition(filled.getCoordinate(), side);
            WFlat neighborFlat = allFlats.get(neighborPos);
            if (neighborFlat != null) {
                // Set parameter for SideBlenderBuilder
                String paramKey = "g_side_flat_" + side.name().toLowerCase();
                hexGrid.getParameters().put(paramKey, neighborFlat.getFlatId());
                log.trace("Set {} = {} for grid [{},{}]",
                    paramKey, neighborFlat.getFlatId(),
                    filled.getCoordinate().getQ(), filled.getCoordinate().getR());
            }
        }

        // Set blend width (optional, default is 20)
        hexGrid.getParameters().put("g_edge_blend_width", "15");

        // Apply blender pipeline
        try {
            HexGridBuilderService builderService = new HexGridBuilderService();
            List<HexGridBuilder> blenderPipeline = builderService.createBuilderPipeline(
                hexGrid, HexGridBuilderService.STEP.BLENDER);

            if (!blenderPipeline.isEmpty()) {
                BuilderContext context = createContext(flat, hexGrid, gridIndex, allFlats);
                for (HexGridBuilder builder : blenderPipeline) {
                    builder.setContext(context);
                    builder.buildFlat();
                }
            }
        } catch (Exception e) {
            log.warn("Phase 2 (BLENDER) failed for grid [{},{}]: {}",
                filled.getCoordinate().getQ(), filled.getCoordinate().getR(),
                e.getMessage(), e);
        }
    }

    /**
     * Phase 3: TERRAIN - Apply terrain features (rivers, roads, etc.)
     */
    private void applyTerrainFeatures(FilledHexGrid filled, HexGridIndex gridIndex,
                                     Map<HexVector2, WFlat> allFlats) {
        WFlat flat = allFlats.get(filled.getCoordinate());
        if (flat == null) {
            log.warn("Flat not found for grid [{},{}] in Phase 3",
                filled.getCoordinate().getQ(), filled.getCoordinate().getR());
            return;
        }

        log.debug("Phase 3 (TERRAIN): Applying terrain features for grid [{},{}]",
            filled.getCoordinate().getQ(), filled.getCoordinate().getR());

        try {
            HexGridBuilderService builderService = new HexGridBuilderService();
            List<HexGridBuilder> terrainPipeline = builderService.createBuilderPipeline(
                filled.getHexGrid(), HexGridBuilderService.STEP.TERRAIN);

            if (!terrainPipeline.isEmpty()) {
                BuilderContext context = createContext(flat, filled.getHexGrid(), gridIndex, allFlats);
                for (HexGridBuilder builder : terrainPipeline) {
                    builder.setContext(context);
                    builder.buildFlat();
                }
            }
        } catch (Exception e) {
            log.warn("Phase 3 (TERRAIN) failed for grid [{},{}]: {}",
                filled.getCoordinate().getQ(), filled.getCoordinate().getR(),
                e.getMessage(), e);
        }
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

    private BuilderContext createContext(WFlat flat, WHexGrid hexGrid, HexGridIndex gridIndex,
                                        Map<HexVector2, WFlat> allFlats) {
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
        WWorld world = WWorld.builder().build();
        world.setNoiseSeed(1474);
        world.setNoiseFrequency(0.5);

        Map<WHexGrid.SIDE, WHexGrid> neighbors = collectNeighbors(hexGrid.getPosition(), gridIndex);

        // Mock WFlatService for Phase 2 (BLENDER)
        de.mhus.nimbus.world.shared.generator.WFlatService flatService = null;
        if (allFlats != null) {
            flatService = mock(de.mhus.nimbus.world.shared.generator.WFlatService.class);
            // Setup mock to return flats by flatId
            when(flatService.findByWorldAndFlatId(any(), any())).thenAnswer(invocation -> {
                String flatId = invocation.getArgument(1);
                return allFlats.values().stream()
                    .filter(f -> f.getFlatId().equals(flatId))
                    .findFirst()
                    .orElse(null);
            });
        }

        return BuilderContext.builder()
            .flat(flat)
            .hexGrid(hexGrid)
            .world(world)
            .neighborGrids(neighbors)
            .manipulatorService(manipulatorService)
            .chunkService(chunkService)
            .flatService(flatService)
            .build();
    }

    private Map<WHexGrid.SIDE, WHexGrid> collectNeighbors(String position, HexGridIndex gridIndex) {
        var result = new HashMap<WHexGrid.SIDE, WHexGrid>();
        for (WHexGrid.SIDE nabor : WHexGrid.SIDE.values()) {
            HexVector2 naborPosition = HexMathUtil.getNeighborPosition(TypeUtil.parseHexCoord(position), nabor);
            var naborHex = gridIndex.getGrid(naborPosition);
            if (naborHex != null)
                result.put(nabor, naborHex);
        }
        return result;
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
}
