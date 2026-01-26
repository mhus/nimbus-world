package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.flat.FlatManipulatorService;
import de.mhus.nimbus.world.generator.flat.hexgrid.BuilderContext;
import de.mhus.nimbus.world.generator.flat.hexgrid.RoadBuilder;
import de.mhus.nimbus.world.generator.flat.hexgrid.VillageBuilder;
import de.mhus.nimbus.world.shared.generator.FlatLevelImageCreator;
import de.mhus.nimbus.world.shared.generator.FlatMaterialImageCreator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for VillageDesigner with WFlat and Builders
 * Tests the complete flow: Design -> WFlat -> Builder -> Images
 */
@Slf4j
public class VillageDesignerIntegrationTest {

    private static final int FLAT_SIZE = 512;
    private static final int OCEAN_LEVEL = 50;
    private Path outputDir;

    @BeforeEach
    public void setup() throws Exception {
        // Create output directory
        outputDir = Paths.get("target/test-output/village-designer");
        Files.createDirectories(outputDir);
        log.info("Output directory: {}", outputDir.toAbsolutePath());
    }

    @Test
    public void testHamletWithBuilders() throws Exception {
        log.info("=== Testing Hamlet Village Generation ===");

        // 1. Design the village using VillageDesigner
        VillageTemplate template = VillageTemplateLoader.load("hamlet-medieval");
        VillageDesigner designer = new VillageDesigner();
        VillageDesignResult designResult = designer.design(template, 95);

        assertNotNull(designResult);
        assertEquals(1, designResult.getGridConfigs().size());

        // 2. Get the grid config for the single hamlet grid
        HexGridConfig gridConfig = designResult.getGridConfigs().values().iterator().next();
        HexVector2 gridPos = gridConfig.getGridPosition();

        log.info("Grid position: [{}, {}]", gridPos.getQ(), gridPos.getR());
        log.info("Plots: {}", gridConfig.getPlots().size());

        // 3. Create WFlat manually (no MongoDB)
        WFlat flat = createEmptyFlat("hamlet-test", gridPos);

        // 4. Create WHexGrid with village parameters
        WHexGrid hexGrid = createHexGrid(gridPos, gridConfig);

        // 5. Create mocked context
        BuilderContext context = createMockedContext(flat, hexGrid);

        // 6. Apply RoadBuilder first (for plaza)
        if (gridConfig.getRoadConfig() != null) {
            log.info("Applying RoadBuilder...");
            RoadBuilder roadBuilder = new RoadBuilder();
            roadBuilder.setContext(context);
            roadBuilder.buildFlat();
            log.info("RoadBuilder completed");
        }

        // 7. Apply VillageBuilder (for buildings)
        log.info("Applying VillageBuilder...");
        VillageBuilder villageBuilder = new VillageBuilder();
        villageBuilder.setContext(context);
        villageBuilder.buildFlat();
        log.info("VillageBuilder completed");

        // 8. Generate and save images
        saveImages(flat, "hamlet-medieval");

        // 9. Verify flat has been modified
        assertFlatModified(flat);
    }

    @Test
    public void test2x1VillageWithBuilders() throws Exception {
        log.info("=== Testing 2x1 Village Generation ===");

        // 1. Design the village
        VillageTemplate template = VillageTemplateLoader.load("village-2x1-medieval");
        VillageDesigner designer = new VillageDesigner();
        VillageDesignResult designResult = designer.design(template, 95);

        assertEquals(2, designResult.getGridConfigs().size());

        // 2. Process each grid
        for (Map.Entry<HexVector2, HexGridConfig> entry : designResult.getGridConfigs().entrySet()) {
            HexVector2 gridPos = entry.getKey();
            HexGridConfig gridConfig = entry.getValue();

            log.info("Processing grid [{}, {}] with {} plots",
                gridPos.getQ(), gridPos.getR(), gridConfig.getPlots().size());

            // Create WFlat for this grid
            WFlat flat = createEmptyFlat("village-2x1-" + gridPos.getQ() + "-" + gridPos.getR(), gridPos);

            // Create WHexGrid with parameters
            WHexGrid hexGrid = createHexGrid(gridPos, gridConfig);

            // Create context
            BuilderContext context = createMockedContext(flat, hexGrid);

            // Apply RoadBuilder if configured
            if (gridConfig.getRoadConfig() != null) {
                RoadBuilder roadBuilder = new RoadBuilder();
                roadBuilder.setContext(context);
                roadBuilder.buildFlat();
            }

            // Apply VillageBuilder
            VillageBuilder villageBuilder = new VillageBuilder();
            villageBuilder.setContext(context);
            villageBuilder.buildFlat();

            // Save images
            saveImages(flat, "village-2x1-grid-" + gridPos.getQ() + "-" + gridPos.getR());

            // Verify
            assertFlatModified(flat);
        }
    }

    @Test
    public void test5CrossTownWithBuilders() throws Exception {
        log.info("=== Testing 5-Cross Town Generation ===");

        // 1. Design the town
        VillageTemplate template = VillageTemplateLoader.load("town-5cross-medieval");
        VillageDesigner designer = new VillageDesigner();
        VillageDesignResult designResult = designer.design(template, 95);

        assertEquals(5, designResult.getGridConfigs().size());

        // 2. Process center grid (most interesting)
        HexVector2 centerPos = HexVector2.builder().q(1).r(1).build();
        HexGridConfig centerConfig = designResult.getGridConfigs().get(centerPos);

        assertNotNull(centerConfig, "Center grid should exist");

        log.info("Processing center grid with {} plots and {} routes",
            centerConfig.getPlots().size(),
            centerConfig.getRoadConfig() != null ? centerConfig.getRoadConfig().getRoutes().size() : 0);

        // Create WFlat
        WFlat flat = createEmptyFlat("town-5cross-center", centerPos);

        // Create WHexGrid
        WHexGrid hexGrid = createHexGrid(centerPos, centerConfig);

        // Create context
        BuilderContext context = createMockedContext(flat, hexGrid);

        // Apply RoadBuilder (plaza + routes)
        if (centerConfig.getRoadConfig() != null) {
            RoadBuilder roadBuilder = new RoadBuilder();
            roadBuilder.setContext(context);
            roadBuilder.buildFlat();
        }

        // Apply VillageBuilder
        VillageBuilder villageBuilder = new VillageBuilder();
        villageBuilder.setContext(context);
        villageBuilder.buildFlat();

        // Save images
        saveImages(flat, "town-5cross-center");

        // Verify
        assertFlatModified(flat);
    }

    /**
     * Creates an empty WFlat with initialized arrays
     */
    private WFlat createEmptyFlat(String flatId, HexVector2 gridPos) {
        byte[] levels = new byte[FLAT_SIZE * FLAT_SIZE];
        byte[] columns = new byte[FLAT_SIZE * FLAT_SIZE];

        // Initialize with base terrain
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (byte) OCEAN_LEVEL;  // Base level
            columns[i] = 0;  // Not set
        }

        return WFlat.builder()
            .flatId(flatId)
            .worldId("test-world")
            .layerDataId("test-layer")
            .hexGrid(gridPos)
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
    }

    /**
     * Creates a WHexGrid with parameters from HexGridConfig
     */
    private WHexGrid createHexGrid(HexVector2 gridPos, HexGridConfig gridConfig) {
        Map<String, String> parameters = new HashMap<>();

        // Add village parameter
        String villageParam = gridConfig.toVillageParameter();
        parameters.put("g_village", villageParam);
        log.debug("Village parameter: {}", villageParam);

        // Add road parameter
        String roadParam = gridConfig.toRoadParameter();
        if (!roadParam.equals("{}")) {
            parameters.put("g_road", roadParam);
            log.debug("Road parameter: {}", roadParam);
        }

        // Create HexGrid public data
        de.mhus.nimbus.generated.types.HexGrid publicData = new de.mhus.nimbus.generated.types.HexGrid();
        publicData.setPosition(gridPos);
        publicData.setName("test-grid");

        return WHexGrid.builder()
            .publicData(publicData)
            .position(TypeUtil.toStringHexCoord(gridPos.getQ(), gridPos.getR()))
            .worldId("test-world")
            .parameters(parameters)
            .build();
    }

    /**
     * Creates a mocked BuilderContext
     */
    private BuilderContext createMockedContext(WFlat flat, WHexGrid hexGrid) {
        // Mock services
        FlatManipulatorService manipulatorService = mock(FlatManipulatorService.class);
        WChunkService chunkService = mock(WChunkService.class);
        WWorld world = mock(WWorld.class);

        // Create context
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
     * Saves level and material images for a flat
     */
    private void saveImages(WFlat flat, String name) throws Exception {
        // Generate level image
        FlatLevelImageCreator levelCreator = new FlatLevelImageCreator(flat);
        byte[] levelImageData = levelCreator.create(false);

        File levelFile = outputDir.resolve(name + "-level.png").toFile();
        try (FileOutputStream fos = new FileOutputStream(levelFile)) {
            fos.write(levelImageData);
        }
        log.info("Saved level image: {}", levelFile.getAbsolutePath());

        // Generate material image
        FlatMaterialImageCreator materialCreator = new FlatMaterialImageCreator(flat);
        byte[] materialImageData = materialCreator.create(false);

        File materialFile = outputDir.resolve(name + "-material.png").toFile();
        try (FileOutputStream fos = new FileOutputStream(materialFile)) {
            fos.write(materialImageData);
        }
        log.info("Saved material image: {}", materialFile.getAbsolutePath());
    }

    /**
     * Verifies that the flat has been modified (buildings/roads placed)
     */
    private void assertFlatModified(WFlat flat) {
        byte[] columns = flat.getColumns();
        byte[] levels = flat.getLevels();

        int modifiedColumns = 0;
        int modifiedLevels = 0;

        for (int i = 0; i < columns.length; i++) {
            if (columns[i] != 0) {
                modifiedColumns++;
            }
            if (levels[i] != OCEAN_LEVEL) {
                modifiedLevels++;
            }
        }

        log.info("Modified columns: {}, modified levels: {}", modifiedColumns, modifiedLevels);

        assertTrue(modifiedColumns > 0, "Some columns should be modified");
        assertTrue(modifiedLevels > 0, "Some levels should be modified");
    }
}
