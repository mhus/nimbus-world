package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.flat.hexgrid.BuilderContext;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilder;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilderService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * HexGrid manipulator.
 * Generates terrain based on hex grid configuration using a builder pipeline.
 * <p>
 * The pipeline executes builders in this order:
 * 1. Main builder (from g_builder parameter) - creates base terrain
 * 2. EdgeBlenderBuilder (always) - blends edges with neighbors
 * 3. RiverBuilder (if river parameter exists) - adds rivers
 * 4. RoadBuilder (if road parameter exists) - adds roads
 * 5. WallBuilder (if wall parameter exists) - adds walls
 * <p>
 * Parameters from hex grid:
 * - g_builder: Main builder type (required) - ocean, island, coast, mountains, etc.
 * - river: River definitions (optional)
 * - road: Road definitions (optional)
 * - wall: Wall definitions (optional)
 * - ridge: Ridge definitions for mountains (optional)
 * - g_*: Other grid-specific parameters
 */
@Component
@Slf4j
public class HexGridManipulator implements FlatManipulator {

    public static final String NAME = "hex-grid";

    @Autowired
    private WHexGridService hexGridService;

    @Autowired
    private HexGridBuilderService builderService;

    @Autowired
    private WWorldService worldService;

    @Autowired
    @Lazy
    private FlatManipulatorService manipulatorService;

    @Autowired
    private WChunkService chunkService;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.info("Starting hex-grid manipulation: flat={}, parameters={}", flat.getFlatId(), parameters);

        WWorld world = worldService.getByWorldId(flat.getWorldId()).orElseThrow();

        // Load hex grid configuration
        WHexGrid hexGrid = loadHexGrid(flat);
        if (hexGrid == null) {
            throw new IllegalStateException("No hex grid found for flat: " + flat.getFlatId());
        }

        // Load neighbor grids
        Map<WHexGrid.SIDE, WHexGrid> neighborGrids = loadNeighborGrids(hexGrid, flat.getWorldId());

        // Build context
        BuilderContext context = BuilderContext.builder()
                .world(world)
                .flat(flat)
                .builderService(builderService)
                .hexGrid(hexGrid)
                .neighborGrids(neighborGrids)
                .manipulatorService(manipulatorService)
                .chunkService(chunkService)
                .build();

        // Create builder pipeline
        List<HexGridBuilder> builderPipeline = builderService.createBuilderPipeline(hexGrid);
        if (builderPipeline.isEmpty()) {
            throw new IllegalStateException("No builders in pipeline for hex grid: " + hexGrid.getPosition());
        }

        log.info("Executing builder pipeline with {} builders for hex grid: {}",
                builderPipeline.size(), hexGrid.getPosition());

        // Clear all groupIds before executing builders
        flat.getGroups().clear();

        // Execute all builders in pipeline
        for (int i = 0; i < builderPipeline.size(); i++) {
            HexGridBuilder builder = builderPipeline.get(i);
            String builderName = builder.getClass().getSimpleName();

            log.info("Executing builder {}/{}: {} for hex grid: {}",
                    i + 1, builderPipeline.size(), builderName, hexGrid.getPosition());

            builder.setContext(context);
            builder.buildFlat();

            log.debug("Builder {} completed", builderName);
        }

        log.info("Hex-grid manipulation completed: pipeline executed {} builders for hexGrid={}",
                builderPipeline.size(), hexGrid.getPosition());
    }

    /**
     * Load hex grid for the flat.
     */
    private WHexGrid loadHexGrid(WFlat flat) {
        HexVector2 hexGridPos = flat.getHexGrid();
        if (hexGridPos == null) {
            return null;
        }

        return hexGridService.findByWorldIdAndPosition(flat.getWorldId(), hexGridPos).orElse(null);
    }

    /**
     * Load neighboring hex grids.
     * Returns a map with neighbor position as key and loaded hex grid (or null) as value.
     */
    private Map<WHexGrid.SIDE, WHexGrid> loadNeighborGrids(WHexGrid hexGrid, String worldId) {
        Map<WHexGrid.SIDE, WHexGrid> neighborGrids = new EnumMap<>(WHexGrid.SIDE.class);

        // Get all neighbor positions
        Map<WHexGrid.SIDE, HexVector2> neighborPositions = hexGrid.getAllNeighborPositions();

        // Load each neighbor grid if it exists
        for (Map.Entry<WHexGrid.SIDE, HexVector2> entry : neighborPositions.entrySet()) {
            WHexGrid.SIDE direction = entry.getKey();
            HexVector2 position = entry.getValue();

            WHexGrid neighbor = hexGridService.findByWorldIdAndPosition(worldId, position).orElse(null);
            neighborGrids.put(direction, neighbor);

            if (neighbor != null) {
                log.debug("Loaded neighbor grid: direction={}, position={}", direction, neighbor.getPosition());
            }
        }

        return neighborGrids;
    }

}
