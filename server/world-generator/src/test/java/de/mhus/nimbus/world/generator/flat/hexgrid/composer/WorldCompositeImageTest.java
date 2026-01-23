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

        // Step 1: Create composition with biomes AND villages
        PreparedHexComposition composition = createCompositionWithVillages();

        // Export input model
        exportInputModel(composition);

        // Step 2: Place biomes
        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult placementResult = biomeComposer.compose(composition, "full-world", 99999L);

        assertTrue(placementResult.isSuccess());
        log.info("Placed {} biomes creating {} hex grids",
            placementResult.getPlacedBiomes().size(),
            placementResult.getHexGrids().size());

        // Step 3: Fill gaps
        HexGridFiller filler = new HexGridFiller();
        HexGridFillResult fillResult = filler.fill(placementResult, "full-world", 1);

        assertTrue(fillResult.isSuccess());
        log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Step 3b: Apply village parameters to center grid AFTER filling
        if (!composition.getVillages().isEmpty()) {
            PreparedVillage village = composition.getVillages().get(0);
            if (village.getParameters() != null) {
                // Find center plains grid [0,0] and add village parameters
                for (FilledHexGrid filled : fillResult.getAllGrids()) {
                    if (filled.getCoordinate().getQ() == 0 && filled.getCoordinate().getR() == 0) {
                        WHexGrid grid = filled.getHexGrid();
                        log.info("Adding village parameters to grid [0,0] AFTER filling");

                        if (grid.getParameters() == null) {
                            grid.setParameters(new HashMap<>());
                        }

                        // Set g_builder first (required for pipeline)
                        if (!grid.getParameters().containsKey("g_builder")) {
                            grid.getParameters().put("g_builder", "island");
                        }

                        // Then add village parameters
                        grid.getParameters().putAll(village.getParameters());

                        log.info("Village & road parameters added: g_builder={}, village={}, road={}",
                            grid.getParameters().get("g_builder"),
                            grid.getParameters().containsKey("village"),
                            grid.getParameters().containsKey("road"));
                        break;
                    }
                }
            }
        }

        // Step 4: Add roads and rivers
        List<RoadConnection> roadConnections = createRoadConnections();
        List<RiverConnection> riverConnections = createRiverConnections();

        RoadAndRiverConnector connector = new RoadAndRiverConnector();
        ConnectionResult connectionResult = connector.connect(fillResult, roadConnections, riverConnections);

        assertTrue(connectionResult.isSuccess());
        log.info("Applied {} roads and {} rivers",
            connectionResult.getRoadsApplied(),
            connectionResult.getRiversApplied());

        // Update fillResult with connected grids
        List<FilledHexGrid> updatedGrids = new ArrayList<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WHexGrid updatedGrid = connectionResult.getHexGrids().stream()
                .filter(g -> g.getPosition().equals(filled.getHexGrid().getPosition()))
                .findFirst()
                .orElse(filled.getHexGrid());

            updatedGrids.add(FilledHexGrid.builder()
                .coordinate(filled.getCoordinate())
                .hexGrid(updatedGrid)
                .biome(filled.getBiome())
                .isFiller(filled.isFiller())
                .fillerType(filled.getFillerType())
                .build());
        }

        fillResult = HexGridFillResult.builder()
            .allGrids(updatedGrids)
            .placementResult(fillResult.getPlacementResult())
            .oceanFillCount(fillResult.getOceanFillCount())
            .landFillCount(fillResult.getLandFillCount())
            .coastFillCount(fillResult.getCoastFillCount())
            .success(true)
            .build();

        // Step 5: Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();
        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Step 6: Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Step 7: Create composite image
        createCompositeImage(flats, fillResult, "complete-world-all-components");

        // Step 8: Export final generated model
        exportGeneratedModel(fillResult, connectionResult, roadConnections, riverConnections);

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
        grids.add(createFilledGridWithBuilder(0, -1, "mountains", BiomeType.MOUNTAINS));

        // East: Coast
        grids.add(createFilledGridWithBuilder(1, 0, "coast", BiomeType.COAST));

        // South: Ocean
        grids.add(createFilledGridWithBuilder(0, 1, "ocean", BiomeType.OCEAN));

        // West: Mountains
        grids.add(createFilledGridWithBuilder(-1, 0, "mountains", BiomeType.MOUNTAINS));

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

        // Step 1: Create composition
        PreparedHexComposition composition = createComposition();

        // Step 2: Place biomes
        BiomeComposer biomeComposer = new BiomeComposer();
        BiomePlacementResult placementResult = biomeComposer.compose(composition, "composite-world", 54321L);

        assertTrue(placementResult.isSuccess());
        log.info("Placed {} biomes creating {} hex grids",
            placementResult.getPlacedBiomes().size(),
            placementResult.getHexGrids().size());

        // Step 3: Fill gaps
        HexGridFiller filler = new HexGridFiller();
        HexGridFillResult fillResult = filler.fill(placementResult, "composite-world", 2);

        assertTrue(fillResult.isSuccess());
        log.info("Total grids after filling: {} (Ocean: {}, Land: {}, Coast: {})",
            fillResult.getTotalGridCount(),
            fillResult.getOceanFillCount(),
            fillResult.getLandFillCount(),
            fillResult.getCoastFillCount());

        // Step 4: Build terrain for all grids
        Map<HexVector2, WFlat> flats = new HashMap<>();

        for (FilledHexGrid filled : fillResult.getAllGrids()) {
            WFlat flat = buildGridTerrain(filled);
            flats.put(filled.getCoordinate(), flat);
        }

        log.info("Built terrain for {} grids", flats.size());

        // Step 5: Save individual grid images
        saveIndividualGridImages(flats, fillResult);

        // Step 6: Create composite image
        createCompositeImage(flats, fillResult, "complete-world-composite");

        log.info("=== Composite Image Test Completed ===");
    }

    /**
     * Creates composition with biomes AND villages
     */
    private PreparedHexComposition createCompositionWithVillages() {
        PreparedHexComposition composition = new PreparedHexComposition();
        List<PreparedBiome> biomes = new ArrayList<>();

        // Center: Plains with Village
        PreparedBiome plains = createBiome("Central Plains", BiomeType.PLAINS, BiomeShape.CIRCLE,
            3, 4, Direction.N, 0, 0, 0, "origin", 10);
        biomes.add(plains);

        // North: Mountains
        biomes.add(createBiome("Northern Mountains", BiomeType.MOUNTAINS, BiomeShape.LINE,
            3, 4, Direction.N, 0, 5, 8, "origin", 9));

        // East: Forest
        biomes.add(createBiome("Eastern Forest", BiomeType.FOREST, BiomeShape.CIRCLE,
            2, 3, Direction.E, 90, 5, 8, "origin", 8));

        composition.setBiomes(biomes);

        // Add villages
        List<PreparedVillage> villages = new ArrayList<>();

        // Village in center plains
        PreparedVillage village = new PreparedVillage();
        village.setName("Central Village");
        village.setType(BiomeType.VILLAGE);

        PreparedPosition villagePos = new PreparedPosition();
        villagePos.setDirection(Direction.N);
        villagePos.setDirectionAngle(0);
        villagePos.setDistanceFrom(0);
        villagePos.setDistanceTo(2);
        villagePos.setAnchor("origin");
        villagePos.setPriority(5);

        village.setPositions(Arrays.asList(villagePos));
        village.setBuildings(new ArrayList<>());
        village.setStreets(new ArrayList<>());

        // Use VillageDesigner to create village buildings
        VillageTemplate template = VillageTemplateLoader.load("hamlet-medieval");
        VillageDesigner designer = new VillageDesigner();
        VillageDesignResult designResult = designer.design(template, 95);

        // Add village parameters to PreparedVillage
        Map<String, String> params = new HashMap<>();
        HexGridConfig centerGridConfig = designResult.getGridConfigs().get(HexVector2.builder().q(0).r(0).build());
        if (centerGridConfig != null) {
            params.put("village", centerGridConfig.toVillageParameter());
            params.put("road", centerGridConfig.toRoadParameter());
        }
        village.setParameters(params);

        villages.add(village);
        composition.setVillages(villages);

        return composition;
    }

    /**
     * Creates road connections between grids
     */
    private List<RoadConnection> createRoadConnections() {
        List<RoadConnection> roads = new ArrayList<>();

        // Main road from [0,0] to [1,0] (East)
        roads.add(RoadConnection.builder()
            .fromGrid(HexVector2.builder().q(0).r(0).build())
            .toGrid(HexVector2.builder().q(1).r(0).build())
            .fromSide(WHexGrid.SIDE.EAST)
            .toSide(WHexGrid.SIDE.WEST)
            .width(5)
            .level(95)
            .type("street")
            .groupId("main-road")
            .build());

        // Road from [0,0] to [0,-1] (North-West)
        roads.add(RoadConnection.builder()
            .fromGrid(HexVector2.builder().q(0).r(0).build())
            .toGrid(HexVector2.builder().q(0).r(-1).build())
            .fromSide(WHexGrid.SIDE.NORTH_WEST)
            .toSide(WHexGrid.SIDE.SOUTH_EAST)
            .width(4)
            .level(95)
            .type("street")
            .groupId("north-road")
            .build());

        return roads;
    }

    /**
     * Creates river connections between grids
     */
    private List<RiverConnection> createRiverConnections() {
        List<RiverConnection> rivers = new ArrayList<>();

        // River from mountains [0,-2] to plains [0,-1]
        rivers.add(RiverConnection.builder()
            .fromGrid(HexVector2.builder().q(0).r(-2).build())
            .toGrid(HexVector2.builder().q(0).r(-1).build())
            .fromSide(WHexGrid.SIDE.SOUTH_EAST)
            .toSide(WHexGrid.SIDE.NORTH_WEST)
            .width(3)
            .depth(2)
            .level(45)
            .groupId("mountain-river")
            .build());

        return rivers;
    }

    /**
     * Creates a complete composition
     */
    private PreparedHexComposition createComposition() {
        PreparedHexComposition composition = new PreparedHexComposition();
        List<PreparedBiome> biomes = new ArrayList<>();

        // Center: Plains
        biomes.add(createBiome("Central Plains", BiomeType.PLAINS, BiomeShape.CIRCLE,
            4, 6, Direction.N, 0, 0, 0, "origin", 10));

        // North: Mountains
        biomes.add(createBiome("Northern Mountains", BiomeType.MOUNTAINS, BiomeShape.LINE,
            5, 7, Direction.N, 0, 8, 12, "origin", 9));

        // East: Forest
        biomes.add(createBiome("Eastern Forest", BiomeType.FOREST, BiomeShape.CIRCLE,
            4, 6, Direction.E, 90, 8, 12, "origin", 8));

        // South: Swamp
        biomes.add(createBiome("Southern Swamp", BiomeType.SWAMP, BiomeShape.CIRCLE,
            3, 5, Direction.S, 180, 8, 12, "origin", 7));

        // West: Desert
        biomes.add(createBiome("Western Desert", BiomeType.DESERT, BiomeShape.CIRCLE,
            4, 5, Direction.W, 270, 8, 12, "origin", 6));

        composition.setBiomes(biomes);
        composition.setVillages(new ArrayList<>());

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
                    case OCEAN -> "ocean";
                    case LAND -> "coast";  // Use coast for land filler
                    case COAST -> "coast";
                };
            } else if (filled.getBiome() != null && filled.getBiome().getBiome() != null) {
                builderType = switch (filled.getBiome().getBiome().getType()) {
                    case MOUNTAINS -> "mountains";
                    case FOREST -> "coast";     // Forest builder not registered yet
                    case DESERT -> "coast";     // Desert builder not registered yet
                    case SWAMP -> "coast";      // Swamp builder not registered yet
                    case PLAINS -> "island";    // Use island for plains
                    case OCEAN -> "ocean";
                    case COAST -> "coast";
                    default -> "coast";
                };
            } else {
                builderType = "coast";  // Default fallback
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

        // Create PreparedBiome
        PreparedBiome preparedBiome = new PreparedBiome();
        preparedBiome.setName(builderType + "-" + q + "-" + r);
        preparedBiome.setType(biomeType);
        preparedBiome.setShape(BiomeShape.CIRCLE);
        preparedBiome.setSizeFrom(1);
        preparedBiome.setSizeTo(1);
        preparedBiome.setPositions(new ArrayList<>());
        preparedBiome.setParameters(new HashMap<>());

        // Wrap in PlacedBiome
        PlacedBiome placedBiome = new PlacedBiome();
        placedBiome.setBiome(preparedBiome);
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
    private void exportInputModel(PreparedHexComposition composition) throws Exception {
        File inputFile = outputDir.resolve("input-model.json").toFile();

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(inputFile, composition);

        log.info("Exported input model to: {}", inputFile.getAbsolutePath());
    }

    /**
     * Exports generated model as JSON
     */
    private void exportGeneratedModel(HexGridFillResult fillResult,
                                      ConnectionResult connectionResult,
                                      List<RoadConnection> roadConnections,
                                      List<RiverConnection> riverConnections) throws Exception {

        // Create output model
        Map<String, Object> outputModel = new HashMap<>();

        // Summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalGrids", fillResult.getAllGrids().size());
        summary.put("biomeGrids", fillResult.getPlacementResult().getPlacedBiomes().size());
        summary.put("oceanFiller", fillResult.getOceanFillCount());
        summary.put("landFiller", fillResult.getLandFillCount());
        summary.put("coastFiller", fillResult.getCoastFillCount());
        summary.put("roadsApplied", connectionResult.getRoadsApplied());
        summary.put("riversApplied", connectionResult.getRiversApplied());
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

            // Parameters
            if (filled.getHexGrid().getParameters() != null && !filled.getHexGrid().getParameters().isEmpty()) {
                Map<String, String> params = new HashMap<>(filled.getHexGrid().getParameters());

                // Only include non-sensitive parameter keys
                Map<String, Object> paramInfo = new HashMap<>();
                paramInfo.put("g_builder", params.get("g_builder"));

                if (params.containsKey("village")) {
                    paramInfo.put("hasVillage", true);
                    paramInfo.put("villageConfig", params.get("village"));
                }

                if (params.containsKey("road")) {
                    paramInfo.put("hasRoad", true);
                    paramInfo.put("roadConfig", params.get("road"));
                }

                if (params.containsKey("river")) {
                    paramInfo.put("hasRiver", true);
                    paramInfo.put("riverConfig", params.get("river"));
                }

                gridInfo.put("parameters", paramInfo);
            }

            hexGrids.add(gridInfo);
        }
        outputModel.put("hexGrids", hexGrids);

        // Road connections
        List<Map<String, Object>> roads = new ArrayList<>();
        for (RoadConnection road : roadConnections) {
            Map<String, Object> roadInfo = new HashMap<>();
            roadInfo.put("from", coordinateToString(road.getFromGrid()));
            roadInfo.put("to", coordinateToString(road.getToGrid()));
            roadInfo.put("fromSide", road.getFromSide().name());
            roadInfo.put("toSide", road.getToSide().name());
            roadInfo.put("width", road.getWidth());
            roadInfo.put("level", road.getLevel());
            roadInfo.put("type", road.getType());
            roads.add(roadInfo);
        }
        outputModel.put("roadConnections", roads);

        // River connections
        List<Map<String, Object>> rivers = new ArrayList<>();
        for (RiverConnection river : riverConnections) {
            Map<String, Object> riverInfo = new HashMap<>();
            riverInfo.put("from", coordinateToString(river.getFromGrid()));
            riverInfo.put("to", coordinateToString(river.getToGrid()));
            riverInfo.put("fromSide", river.getFromSide().name());
            riverInfo.put("toSide", river.getToSide().name());
            riverInfo.put("width", river.getWidth());
            riverInfo.put("depth", river.getDepth());
            riverInfo.put("level", river.getLevel());
            rivers.add(riverInfo);
        }
        outputModel.put("riverConnections", rivers);

        // Write to file
        File outputFile = outputDir.resolve("generated-model.json").toFile();
        ObjectMapper mapper = new ObjectMapper();
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
     * Helper to create biome
     */
    private PreparedBiome createBiome(String name, BiomeType type, BiomeShape shape,
                                      int sizeFrom, int sizeTo,
                                      Direction direction, int angle,
                                      int distFrom, int distTo,
                                      String anchor, int priority) {
        PreparedBiome biome = new PreparedBiome();
        biome.setName(name);
        biome.setType(type);
        biome.setShape(shape);
        biome.setSizeFrom(sizeFrom);
        biome.setSizeTo(sizeTo);

        PreparedPosition pos = new PreparedPosition();
        pos.setDirection(direction);
        pos.setDirectionAngle(angle);
        pos.setDistanceFrom(distFrom);
        pos.setDistanceTo(distTo);
        pos.setAnchor(anchor);
        pos.setPriority(priority);

        biome.setPositions(Arrays.asList(pos));
        biome.setParameters(new HashMap<>());

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
