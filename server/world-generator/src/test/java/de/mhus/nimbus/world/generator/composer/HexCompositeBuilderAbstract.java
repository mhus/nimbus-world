package de.mhus.nimbus.world.generator.composer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.build.CompositionResult;
import de.mhus.nimbus.world.generator.composer.biome.Continent;
import de.mhus.nimbus.world.generator.composer.build.MapFlatProvider;
import de.mhus.nimbus.world.generator.composer.image.CrossOverlay;
import de.mhus.nimbus.world.generator.composer.flow.FlowComposer;
import de.mhus.nimbus.world.generator.composer.build.HexCompositeBuilder;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.build.HexGridCompositeImageCreator;
import de.mhus.nimbus.world.generator.composer.filler.HexGridFillResult;
import de.mhus.nimbus.world.generator.composer.point.Point;
import de.mhus.nimbus.world.generator.composer.image.TextOverlay;
import de.mhus.nimbus.world.generator.composer.town.TownDebugOverlayHelper;
import de.mhus.nimbus.world.generator.flat.manipulator.BorderSmoothManipulator;
import de.mhus.nimbus.world.generator.flat.FlatManipulator;
import de.mhus.nimbus.world.generator.flat.FlatManipulatorService;
import de.mhus.nimbus.world.generator.flat.manipulator.FlatTerrainManipulator;
import de.mhus.nimbus.world.generator.flat.manipulator.HillyTerrainManipulator;
import de.mhus.nimbus.world.generator.flat.manipulator.IslandsManipulator;
import de.mhus.nimbus.world.generator.flat.manipulator.NormalTerrainManipulator;
import de.mhus.nimbus.world.generator.flat.manipulator.SoftenManipulator;
import de.mhus.nimbus.world.generator.flat.hexgrid.BuilderContext;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilder;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilderService;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridIndex;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.generated.types.WorldInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;

import java.awt.*;
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
 * Tests for HexCompositeBuilder - orchestrates complete composition pipeline.
 * <p>
 * This test uses the same architecture as Day3Generation workflow:
 * <ol>
 *   <li>HexCompositeBuilder.compose() - Enrichment with fillGaps and oceanBorderRings (like ApplyTranslatedInstructionJobExecutor)</li>
 *   <li>CREATE ALL - Initialize WFlats (like FlatHexGridEmptyCreateJobExecutor)</li>
 *   <li>GROUND - Build basic terrain using HexGridBuilderService.STEP.GROUND (like FlatManipulateJobExecutor with step=GROUND)</li>
 *   <li>BLENDER - Blend edges using HexGridBuilderService.STEP.BLENDER (like FlatManipulateJobExecutor with step=BLENDER)</li>
 *   <li>TERRAIN - Apply features using HexGridBuilderService.STEP.TERRAIN (like FlatManipulateJobExecutor with step=TERRAIN)</li>
 * </ol>
 * <p>
 * The test uses HexGridBuilderService directly (like HexGridManipulator does) without DB service dependencies,
 * making it a fast unit test that still validates the production code paths.
 */
@Slf4j
public abstract class HexCompositeBuilderAbstract {

    private static final int HEX_GRID_SIZE = 400;  // hexGridSize from world.publicData
    private static final int SEA_LEVEL = 50;
    private static final int GROUND_LEVEL = 20;

    private Path outputDir;
    private de.mhus.nimbus.world.generator.flat.FlatCreateService flatCreateService;

    @BeforeEach
    public void setup() throws Exception {
        // Output directory for images
        outputDir = Paths.get("target/test-output/hex-composite-simple");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());

        // Setup FlatCreateService with mocked dependencies
        setupFlatCreateService();
    }

    public CompositionResult composite(String name) throws Exception {
        log.info("=== Testing %s System ===".formatted(name));

        // Load composition from JSON file
        File jsonFile = new File("src/test/resources/%s.json".formatted(name));
        assertTrue(jsonFile.exists(), "Continent test JSON file '%s' should exist".formatted(name));

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
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

        // Create test world with publicData for hexGridSize
        WWorld testWorld = new WWorld();
        testWorld.setWorldId("middle-earth");  // Must match the worldId in WFlats!
        testWorld.setNoiseSeed(1474);
        testWorld.setNoiseFrequency(0.5);
        testWorld.setSeaLevel(SEA_LEVEL);
        testWorld.setGroundLevel(GROUND_LEVEL);
        WorldInfo publicData = new WorldInfo();
        publicData.setHexGridSize(HEX_GRID_SIZE);  // 400 - FlatCreateService calculates actual flat size
        publicData.setChunkSize(32);

        testWorld.setPublicData(publicData);


        // Use HexCompositeBuilder for the complete pipeline
        log.info("Starting composition pipeline...");
        CompositionResult result = HexCompositeBuilder.builder()
            .composition(composition)
            .worldId("continent-test-%s".formatted(name))
            .world(testWorld)
            .seed(42L)  // Consistent seed for reproducible results
            .fillGaps(true)
            .oceanBorderRings(2)
            .build()
            .compose();

        // Verify successful
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

        assertNotNull(result.getFillResult(), "Fill result should not be null");

        // Build terrain for all grids using HexGridBuilderService pipeline (like Day3Generation)
        log.info("Building terrain for all grids using production pipeline...");
        Map<String, WFlat> flats = new HashMap<>();
        Map<String, WHexGrid> grids = new HashMap<>();
        HexGridFillResult fillResult = result.getFillResult();
        fillResult.setFlats(flats);

        // Create WHexGrids from Central Registry (compose() no longer creates them)
        var allGrids = HexCompositeBuilder.createWHexGridsFromRegistry(composition,
            "continent-test-%s".formatted(name));
        result.setWHexGrids(allGrids); // Store for individual test assertions
        var index = new HexGridIndex(allGrids);

        // ===== PHASE 1: CREATE ALL - Initialize all WFlats with base terrain =====
        log.info("Phase CREATE ALL: Initializing {} grids", allGrids.size());
        for (WHexGrid hexGrid : allGrids) {
            if (hexGrid.getParameters() == null) {
                hexGrid.setParameters(new HashMap<>());
            }
            de.mhus.nimbus.generated.types.HexVector2 coord = hexGrid.getPublicData().getPosition();
            grids.put(coord.getQ() + "_" + coord.getR(), hexGrid);
        }
        for (WHexGrid hexGrid : allGrids) {
            try {
                WFlat flat = initializeFlat(hexGrid);
                de.mhus.nimbus.generated.types.HexVector2 coord = hexGrid.getPublicData().getPosition();
                flats.put("genesis_" + coord.getQ() + "_" + coord.getR(), flat);
            } catch (Exception e) {
                de.mhus.nimbus.generated.types.HexVector2 coord = hexGrid.getPublicData().getPosition();
                log.warn("CREATE failed for grid {}: {}", coord, e.getMessage(), e);
            }
        }
        WFlatService flatService = mock(WFlatService.class);
        when(flatService.findByWorldAndFlatId(any(), any())).thenAnswer(invocation -> {
            String worldId = invocation.getArgument(0);
            String flatId = invocation.getArgument(1);
            return flats.get(flatId);
        });
        when(flatService.update(any())).thenAnswer(invocation -> {
            WFlat updatedFlat = invocation.getArgument(0);
            // do nothing flats.put(updatedFlat.getFlatId(), updatedFlat);
            return updatedFlat;
        });

        WHexGridService hexGridService = mock(WHexGridService.class);
        when(hexGridService.findByWorldIdAndPosition(any(), any())).thenAnswer(invocation -> {
            String worldId = invocation.getArgument(0);
            HexVector2 coord = invocation.getArgument(1);
            return grids.get(coord.getQ() + "_" + coord.getR());
        });


        log.info("Phase CREATE ALL completed: {}/{} grids created", flats.size(), allGrids.size());

        // ===== PHASE 2: GROUND - Execute GROUND builder pipeline for all grids =====
        log.info("Phase GROUND: Building basic terrain for {} grids", allGrids.size());
        int groundCount = executePhaseForAllGrids(allGrids, flats, grids, flatService, hexGridService, index, HexGridBuilderService.STEP.GROUND, "GROUND", testWorld);
        log.info("Phase GROUND completed: {}/{} grids processed", groundCount, allGrids.size());

        // ===== PHASE 3: BLENDER - Execute BLENDER pipeline for all grids =====
        log.info("Phase BLENDER: Blending edges for {} grids", flats.size());
        setupBlenderParameters(flats, grids);  // Add edge_flat parameters
        int blenderCount = executePhaseForAllGrids(allGrids, flats, grids, flatService, hexGridService, index, HexGridBuilderService.STEP.BLENDER, "BLENDER", testWorld);
        log.info("Phase BLENDER completed: {}/{} grids processed", blenderCount, flats.size());

        // ===== PHASE 4: TERRAIN - Execute TERRAIN pipeline for all grids =====
        log.info("Phase TERRAIN: Applying terrain features for {} grids", allGrids.size());
        int terrainCount = executePhaseForAllGrids(allGrids, flats, grids, flatService, hexGridService, index, HexGridBuilderService.STEP.TERRAIN, "TERRAIN", testWorld);
        log.info("Phase TERRAIN completed: {}/{} grids processed", terrainCount, allGrids.size());

        // Create composite image
        log.info("Creating %s composite image...".formatted(name));
        createCompositeImage(flats, allGrids, fillResult, composition, "continent-test-%s".formatted(name));

        // Export generated model
        exportGeneratedModel(result, "continent-test-%s".formatted(name));

        // Export the processed input composition model
        log.info("Registry size before export: {}", composition.getFeatureHexGridRegistry() != null ? composition.getFeatureHexGridRegistry().size() : "NULL");
        // Convert registry Map to List for export (Jackson has issues with Map<String, FeatureHexGrid>)
        if (composition.getFeatureHexGridRegistry() != null) {
            composition.setFeatureHexGrids(new ArrayList<>(composition.getFeatureHexGridRegistry().values()));
        }
        exportInputComposition(composition, "simple-continent-test-%s".formatted(name));

        log.info("=== Simple Content Test %s Completed ===".formatted(name));
        log.info("Images saved to: {}", outputDir.toAbsolutePath());

        return result;
    }

    // ============= Helper Methods =============

    /**
     * Initialize a WFlat for a WHexGrid using production FlatCreateService.
     * This uses the exact same code as FlatHexGridEmptyCreateJobExecutor.
     * - Positions inside the HexGrid: level 0, material 255 (NOT_SET_MUTABLE)
     * - Positions outside the HexGrid (corners): level 0, material 0 (NOT_SET)
     * - unknownProtected = true
     */
    private WFlat initializeFlat(WHexGrid hexGrid) {
        de.mhus.nimbus.generated.types.HexVector2 coord = hexGrid.getPublicData().getPosition();

        // Generate flatId using hex coordinates (like Day3Generation does)
        String flatId = "genesis_" + coord.getQ() + "_" + coord.getR();

        // Use production FlatCreateService to create the flat
        WFlat flat = flatCreateService.createEmptyHexGridFlat(
            "middle-earth",
            "ground",
            flatId,
            coord.getQ(),
            coord.getR(),
            null,  // title
            null   // description
        );

        log.debug("Created flat using FlatCreateService for hex [{},{}]: flatId={}, size={}x{}, mount=({},{})",
            coord.getQ(), coord.getR(),
            flat.getFlatId(), flat.getSizeX(), flat.getSizeZ(), flat.getMountX(), flat.getMountZ());

        return flat;
    }

    /**
     * Execute a specific pipeline phase (GROUND, BLENDER, TERRAIN) for all grids.
     * This replicates what HexGridManipulator does when called via FlatManipulateJobExecutor.
     *
     * @return Number of grids successfully processed
     */
    private int executePhaseForAllGrids(List<WHexGrid> allGrids,
                                        Map<String, WFlat> flats,
                                        Map<String, WHexGrid> grids,
                                        WFlatService flatService,
                                        WHexGridService hexGridService,
                                        HexGridIndex gridIndex,
                                        HexGridBuilderService.STEP step,
                                        String phaseName,
                                        WWorld world) {
        int successCount = 0;
        HexGridBuilderService builderService = new HexGridBuilderService();

        // Iterate over flats map to avoid processing duplicates
        for (Map.Entry<String, WFlat> entry : flats.entrySet()) {
            String flatKey = entry.getKey();
            WFlat flat = entry.getValue();

            // Get hex coordinate from flat
            HexVector2 coord = flat.getHexGrid();
            if (coord == null) {
                log.warn("{} phase: Flat {} has no hex coordinate", phaseName, flatKey);
                continue;
            }

            // Get WHexGrid for this coordinate
            String gridKey = coord.getQ() + "_" + coord.getR();
            WHexGrid hexGrid = grids.get(gridKey);
            if (hexGrid == null) {
                log.warn("{} phase: WHexGrid not found for coordinate [{},{}]", phaseName, coord.getQ(), coord.getR());
                continue;
            }

            try {
                // Create builder pipeline for this step (like HexGridManipulator)
                List<HexGridBuilder> pipeline = builderService.createBuilderPipeline(hexGrid, step);

                if (!pipeline.isEmpty()) {
                    // Debug: Log pipeline builders and parameters for first grid in BLENDER phase
                    if (step == HexGridBuilderService.STEP.BLENDER && successCount == 0) {
                        log.info("BLENDER pipeline for first grid [{},{}]: {} builders",
                            coord.getQ(), coord.getR(),
                            pipeline.stream().map(b -> b.getClass().getSimpleName()).toList());

                        // Log edge_flat parameters
                        Map<String, String> params = hexGrid.getParameters();
                        log.info("Grid [{},{}] edge_flat parameters: {}",
                            coord.getQ(), coord.getR(),
                            params.entrySet().stream()
                                .filter(e -> e.getKey().contains("edge_flat"))
                                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    }

                    // Create context with all necessary dependencies
                    BuilderContext context = createContext(flat, hexGrid, gridIndex, flatService, hexGridService, world);

                    // Execute all builders in pipeline
                    for (HexGridBuilder builder : pipeline) {
                        builder.setContext(context);
                        builder.buildFlat();
                    }

                    log.debug("{} phase completed for grid [{},{}] with {} builders",
                        phaseName, coord.getQ(), coord.getR(),
                        pipeline.size());
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("{} phase failed for grid [{},{}]: {}",
                    phaseName, coord.getQ(), coord.getR(),
                    e.getMessage(), e);
            }
        }

        return successCount;
    }

    /**
     * Setup blender parameters for all grids.
     * Sets edge_flat parameters to reference neighbor flats, and configures blend parameters.
     * This replicates what GenerateHexGridFromCompositeJobExecutor does.
     */
    private void setupBlenderParameters(Map<String, WFlat> flats, Map<String, WHexGrid> grids) {
        int totalNeighbors = 0;
        // Iterate over grids map to avoid processing duplicates
        for (Map.Entry<String, WHexGrid> entry : grids.entrySet()) {
            WHexGrid hexGrid = entry.getValue();
            if (hexGrid.getParameters() == null) {
                hexGrid.setParameters(new HashMap<>());
            }

            // Get coordinate from hexGrid
            HexVector2 coord = TypeUtil.parseHexCoord(hexGrid.getPosition());

            // Set neighbor flat IDs for each side
            int neighborsForThisGrid = 0;
            for (WHexGrid.EDGE side : WHexGrid.EDGE.values()) {
                HexVector2 neighborPos = HexMathUtil.getNeighborPosition(coord, side);
                String neighborFlatKey = "genesis_" + neighborPos.getQ() + "_" + neighborPos.getR();
                WFlat neighborFlat = flats.get(neighborFlatKey);
                if (neighborFlat != null) {
                    String paramKey = "g_edge_flat_" + side.name().toLowerCase();
                    hexGrid.getParameters().put(paramKey, neighborFlat.getFlatId());
                    neighborsForThisGrid++;
                }
            }
            if (neighborsForThisGrid > 0) {
                totalNeighbors += neighborsForThisGrid;
            }

        }
        log.info("Setup blender parameters: {} neighbor edges configured for {} grids", totalNeighbors, grids.size());
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
                                        WFlatService flatService, WHexGridService hexGridService, WWorld world) {
        // WHexGrid is already properly configured from central FeatureHexGrid registry

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

        Map<WHexGrid.EDGE, WHexGrid> neighbors = collectNeighbors(hexGrid.getPosition(), gridIndex);

        // Create HexGridBuilderService
        HexGridBuilderService builderService = new HexGridBuilderService();

        return BuilderContext.builder()
            .flat(flat)
            .hexGrid(hexGrid)
            .world(world)
            .neighborGrids(neighbors)
            .manipulatorService(manipulatorService)
            .chunkService(chunkService)
            .flatService(flatService)
            .builderService(builderService)
            .build();
    }

    private Map<WHexGrid.EDGE, WHexGrid> collectNeighbors(String position, HexGridIndex gridIndex) {
        var result = new HashMap<WHexGrid.EDGE, WHexGrid>();
        for (WHexGrid.EDGE nabor : WHexGrid.EDGE.values()) {
            HexVector2 naborPosition = HexMathUtil.getNeighborPosition(TypeUtil.parseHexCoord(position), nabor);
            var naborHex = gridIndex.getGrid(naborPosition);
            if (naborHex != null)
                result.put(nabor, naborHex);
        }
        return result;
    }

    /**
     * Adds text overlays showing coordinates and biome names for all grids.
     */
    private void addCoordinateTextOverlays(HexGridCompositeImageCreator creator, List<WHexGrid> allGrids, int hexGridSize) {
        // Build map of coordinate to biome name using WHexGrid list
        Map<String, String> coordToBiomeName = new HashMap<>();
        if (allGrids != null) {
            for (WHexGrid hexGrid : allGrids) {
                if (hexGrid == null) continue;
                HexVector2 coord = hexGrid.getPublicData().getPosition();
                String coordKey = coord.getQ() + "," + coord.getR();
                String biomeName = null;

                // Get biome name from parameters
                if (hexGrid.getParameters() != null) {
                    biomeName = hexGrid.getParameters().get("biomeName");

                    // If it's a filler, use fillerType
                    if (biomeName == null && "true".equals(hexGrid.getParameters().get("filler"))) {
                        String fillerType = hexGrid.getParameters().get("fillerType");
                        if (fillerType != null) {
                            biomeName = fillerType.toLowerCase();
                        }
                    }
                }

                if (biomeName != null) {
                    coordToBiomeName.put(coordKey, biomeName);
                }
            }
        }

        for (WHexGrid hexGrid : allGrids) {
            if (hexGrid == null || hexGrid.getPublicData() == null) continue;

            HexVector2 coord = hexGrid.getPublicData().getPosition();
            String coordText = coord.getQ() + "," + coord.getR();

            // Calculate center position of hex grid in world coordinates
            double[] hexCenter = HexMathUtil.hexToCartesian(coord, hexGridSize);
            int centerX = (int) Math.floor(hexCenter[0]);
            int centerY = (int) Math.floor(hexCenter[1]);

            // Create coordinate text overlay centered on grid (white color, scale 3)
            int coordTextWidth = coordText.length() * (5 + 1) * 3; // Approximate width
            TextOverlay coordOverlay = new TextOverlay(coordText, centerX - coordTextWidth/2, centerY - 15, Color.WHITE, 3);
            creator.addOverlay(coordOverlay);

            // Add biome name below coordinates if available
            String coordKey = coord.getQ() + "," + coord.getR();
            String biomeName = coordToBiomeName.get(coordKey);
            if (biomeName != null) {
                int biomeTextWidth = biomeName.length() * (5 + 1) * 2; // Scale 2 for biome name
                TextOverlay biomeOverlay = new TextOverlay(biomeName, centerX - biomeTextWidth/2, centerY + 5, Color.CYAN, 2);
                creator.addOverlay(biomeOverlay);
            }
        }
    }

    /**
     * Adds overlays for all composed points showing their positions and names.
     */
    private void addPointOverlays(HexGridCompositeImageCreator creator, HexComposition composition,
                                 Map<HexVector2, WFlat> flats, int hexGridSize) {
        if (composition == null || composition.getFeatures() == null) {
            return;
        }

        // Collect all points from composition
        List<Point> points = composition.getFeatures().stream()
            .filter(f -> f instanceof Point)
            .map(f -> (Point) f)
            .filter(p -> p.getPointComposed() != null && p.getPointComposed().getGridCoordinate() != null)
            .toList();

        log.info("Adding overlays for {} composed points", points.size());

        for (Point point : points) {
            Point.PointComposed composed = point.getPointComposed();
            HexVector2 gridCoord = composed.getGridCoordinate();

            // Get WFlat for this grid
            WFlat flat = flats.get(gridCoord);
            if (flat == null) {
                log.warn("No WFlat found for point '{}' at grid [{},{}]",
                    point.getName(), gridCoord.getQ(), gridCoord.getR());
                continue;
            }

            // Convert HexLocal position to absolute world coordinates
            int[] worldCoords = getPointWorldCoordinates(composed, flat.getSizeX(), flat.getSizeZ(), gridCoord, hexGridSize);
            if (worldCoords == null) {
                log.warn("Could not calculate world coordinates for point '{}'", point.getName());
                continue;
            }

            int worldX = worldCoords[0];
            int worldZ = worldCoords[1];

            // Add CrossOverlay at point position (red color, size 20, thickness 3)
            creator.addOverlay(new CrossOverlay(worldX, worldZ, 20, Color.RED, 3.0f));

            // Add TextOverlay with point name (yellow color, scale 3)
            String pointName = point.getName() != null ? point.getName() : "point";
            int textWidth = pointName.length() * (5 + 1) * 3;
            TextOverlay textOverlay = new TextOverlay(pointName, worldX - textWidth/2, worldZ - 25, Color.YELLOW, 3);
            creator.addOverlay(textOverlay);

            log.debug("Added overlay for point '{}' at world coords ({}, {})", pointName, worldX, worldZ);
        }
    }

    /**
     * Adds village slot overlays (cross + slot name) to the composite image creator.
     * Extracts WHexGrids from flats and uses VillageDebugOverlayHelper to create overlays.
     */
    private void addVillageSlotOverlays(HexGridCompositeImageCreator creator, List<WHexGrid> allGrids, int hexGridSize) {
        if (allGrids == null) {
            return;
        }

        // Create a map by coordinate from WHexGrid list
        Map<HexVector2, WHexGrid> hexGrids = new HashMap<>();
        for (WHexGrid hexGrid : allGrids) {
            if (hexGrid != null && hexGrid.getPublicData() != null) {
                HexVector2 coord = hexGrid.getPublicData().getPosition();
                hexGrids.put(coord, hexGrid);
            }
        }

        // Use VillageDebugOverlayHelper to add village slot overlays
        // Use hexGridSize like HexGridCompositeImageJobExecutor does
        TownDebugOverlayHelper.addVillageSlotOverlaysFromHexGrids(creator, hexGrids, hexGridSize);
    }

    /**
     * Calculates world coordinates for a point from its HexLocal position.
     * Similar to RiverBuilder.getEndpointCoordinate().
     */
    private int[] getPointWorldCoordinates(Point.PointComposed composed, int flatSizeX, int flatSizeZ,
                                           HexVector2 gridCoord, int hexGridSize) {
        // Get position string - either from HexLocalPosition or HexLocalEdgeVector
        String positionString = null;
        if (composed.getHexLocalPosition() != null) {
            positionString = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(composed.getHexLocalPosition());
        } else if (composed.getHexLocalEdgeVector() != null) {
            positionString = de.mhus.nimbus.world.shared.util.HexLocalUtil.toString(composed.getHexLocalEdgeVector());
        }

        if (positionString == null || positionString.isBlank()) {
            log.warn("No position string found for point");
            return null;
        }

        // Use HexLocalUtil to convert position to relative coordinates
        de.mhus.nimbus.generated.types.Vector2Int relativePos =
            de.mhus.nimbus.world.shared.util.HexLocalUtil.toHexgridLocalCenter(positionString, hexGridSize);

        // Convert to absolute local coordinates within the flat
        int lx = flatSizeX / 2 + relativePos.getX();
        int lz = flatSizeZ / 2 + relativePos.getZ();

        // Convert to absolute world coordinates
        double[] hexCenter = HexMathUtil.hexToCartesian(gridCoord, hexGridSize);
        int mountX = (int) Math.floor(hexCenter[0] - hexGridSize / 2.0);
        int mountZ = (int) Math.floor(hexCenter[1] - hexGridSize / 2.0);

        int worldX = mountX + lx;
        int worldZ = mountZ + lz;

        return new int[]{worldX, worldZ};
    }

    private void createCompositeImage(Map<String, WFlat> flats,
                                     List<WHexGrid> allGrids,
                                     HexGridFillResult fillResult,
                                     HexComposition composition,
                                     String name) throws Exception {
        // Convert flats map from String keys to HexVector2 keys for HexGridCompositeImageCreator
        Map<HexVector2, WFlat> flatsByCoord = new HashMap<>();
        for (Map.Entry<String, WFlat> entry : flats.entrySet()) {
            WFlat flat = entry.getValue();
            if (flat.getHexGrid() != null) {
                flatsByCoord.put(flat.getHexGrid(), flat);
            }
        }

        // Use the HexGridCompositeImageCreator helper class with builder pattern
        HexGridCompositeImageCreator creator = HexGridCompositeImageCreator.builder()
            .flatProvider(new MapFlatProvider(flatsByCoord))
            .hexGridSize(HEX_GRID_SIZE)  // Use HEX_GRID_SIZE (400)
            .outputDirectory(outputDir.toString())
            .imageName(name)
            .drawGridLines(false)  // Disable grid lines to see organic blending better
            .build();

        // Add coordinate and biome name text overlays for all grids
        addCoordinateTextOverlays(creator, allGrids, HEX_GRID_SIZE);

        // Add point overlays (cross + name)
        addPointOverlays(creator, composition, flatsByCoord, HEX_GRID_SIZE);

        // Add village slot overlays (cross + slot name)
        addVillageSlotOverlays(creator, allGrids, HEX_GRID_SIZE);

        // Add debug overlays for grid 0;0
        // addDebugOverlaysForGrid00(creator, flatsByCoord);

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

    private void exportGeneratedModel(CompositionResult result,
                                     String name) throws Exception {
        File outputFile = outputDir.resolve(name + "-generated-model.json").toFile();

        HexGridFillResult fillResult = result.getFillResult();
        FlowComposer.FlowCompositionResult flowResult = result.getFlowCompositionResult();

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
        for (WHexGrid hexGrid : result.getWHexGrids()) {
            Map<String, Object> gridInfo = new HashMap<>();
            HexVector2 coord = hexGrid.getPublicData().getPosition();
            gridInfo.put("coordinate", coord.getQ() + "," + coord.getR());

            boolean isFiller = "true".equals(hexGrid.getParameters().get("filler"));
            gridInfo.put("isFiller", isFiller);
            if (isFiller) {
                String fillerType = hexGrid.getParameters().get("fillerType");
                if (fillerType != null) {
                    gridInfo.put("fillerType", fillerType);
                }
            }

            String biomeName = hexGrid.getParameters().get("biomeName");
            if (biomeName != null) {
                gridInfo.put("biome", biomeName);
                String biomeType = hexGrid.getParameters().get("biomeType");
                if (biomeType != null) {
                    gridInfo.put("biomeType", biomeType);
                }
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

    /**
     * Setup FlatCreateService with mocked dependencies.
     */
    private void setupFlatCreateService() {
        // Create test world with publicData for hexGridSize
        WWorld testWorld = new WWorld();
        testWorld.setWorldId("middle-earth");
        WorldInfo publicData = new WorldInfo();
        publicData.setHexGridSize(HEX_GRID_SIZE);  // 400 - FlatCreateService calculates actual flat size
        publicData.setChunkSize(32);  // Standard chunk size
        testWorld.setPublicData(publicData);
        testWorld.setSeaLevel(SEA_LEVEL);
        testWorld.setGroundLevel(GROUND_LEVEL);
        testWorld.setSeaBlockType("n:water");

        // Mock WWorldService
        de.mhus.nimbus.world.shared.world.WWorldService worldService = mock(de.mhus.nimbus.world.shared.world.WWorldService.class);
        when(worldService.getByWorldId("middle-earth")).thenReturn(java.util.Optional.of(testWorld));

        // Mock WFlatService (just return the flat that was passed in)
        de.mhus.nimbus.world.shared.generator.WFlatService flatService = mock(de.mhus.nimbus.world.shared.generator.WFlatService.class);
        when(flatService.create(any(WFlat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock WLayerService
        de.mhus.nimbus.world.shared.layer.WLayerService layerService = mock(de.mhus.nimbus.world.shared.layer.WLayerService.class);
        de.mhus.nimbus.world.shared.layer.WLayer testLayer = de.mhus.nimbus.world.shared.layer.WLayer.builder()
            .worldId("middle-earth")
            .name("ground")
            .layerDataId("test-layer")
            .layerType(de.mhus.nimbus.world.shared.layer.LayerType.GROUND)
            .build();
        when(layerService.findByWorldIdAndName("middle-earth", "ground")).thenReturn(java.util.Optional.of(testLayer));

        // Mock WChunkService (not used in createEmptyHexGridFlat)
        de.mhus.nimbus.world.shared.world.WChunkService chunkService = mock(de.mhus.nimbus.world.shared.world.WChunkService.class);

        // Create FlatCreateService with mocked dependencies
        flatCreateService = new de.mhus.nimbus.world.generator.flat.FlatCreateService(
            worldService,
            flatService,
            layerService,
            chunkService
        );

        log.info("FlatCreateService initialized with mocked dependencies");
    }
}
