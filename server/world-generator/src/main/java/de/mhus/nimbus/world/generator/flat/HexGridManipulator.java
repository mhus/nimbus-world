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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HexGrid manipulator.
 * Generates terrain based on hex grid configuration and scenario type.
 * Uses CompositionBuilder pattern to create different landscape scenarios.
 * <p>
 * Parameters:
 * - g.type: Scenario type (required) - ocean, island, coast, heath, hills, mountains, plains, dessert, forest, swamp, city, village
 * - gf.*: Grid flat specific parameters (extracted from hex grid and merged with manipulator parameters)
 */
@Component
@Slf4j
public class HexGridManipulator implements FlatManipulator {

    public static final String NAME = "hex-grid";
    public static final String PARAM_TYPE = "type";

    @Autowired
    private WHexGridService hexGridService;

    @Autowired
    private HexGridBuilderService builderService;

    @Autowired
    private WWorldService worldService;

    @Autowired
    @Lazy
    private FlatManipulatorService manipulatorService;

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

        // Merge hex grid parameters with manipulator parameters
        Map<String, String> mergedParameters = extractHexGridParameters(hexGrid, parameters);

        // Extract scenario type
        String type = mergedParameters != null ? mergedParameters.get(PARAM_TYPE) : null;
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Parameter 'g_builder' is required");
        }

        // Get builder for scenario type
        Optional<HexGridBuilder> builderOpt = builderService.createBuilder(type, mergedParameters);
        if (builderOpt.isEmpty()) {
            throw new IllegalArgumentException("Unknown scenario type: " + type);
        }
        HexGridBuilder builder = builderOpt.get();

        // Load neighbor grids and extract their types
        Map<WHexGrid.SIDE, WHexGrid> neighborGrids = loadNeighborGrids(hexGrid, flat.getWorldId());

        // Build context
        BuilderContext context = BuilderContext.builder()
                .world(world)
                .flat(flat)
                .builderService(builderService)
                .hexGrid(hexGrid)
                .neighborGrids(neighborGrids)
                .manipulatorService(manipulatorService)
                .build();

        // Build the scenario
        log.info("Building scenario '{}' for hex grid: {}",
                type, hexGrid.getPosition());
        builder.setContext(context);
        builder.buildFlat();

        log.info("Hex-grid manipulation completed: scenario={}, hexGrid={}", type, hexGrid.getPosition());
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
     * Extract parameters from hex grid and merge with manipulator parameters.
     * Parameters from hex grid with 'g_' prefix are included.
     * Manipulator parameters override hex grid parameters.
     */
    private Map<String, String> extractHexGridParameters(WHexGrid hexGrid, Map<String, String> manipulatorParams) {
        Map<String, String> merged = new HashMap<>();

        // Add hex grid parameters with 'g_' prefix, remove prefix
        if (hexGrid.getParameters() != null) {
            hexGrid.getParameters().forEach((key, value) -> {
                if (key.startsWith("g_")) {
                    merged.put(key.substring(2), value);
                }
            });
        }

        // Override with manipulator parameters
        if (manipulatorParams != null) {
            merged.putAll(manipulatorParams);
        }

        log.debug("Merged parameters: hexGrid={}, manipulator={}, result={}",
                hexGrid.getParameters() != null ? hexGrid.getParameters().size() : 0,
                manipulatorParams != null ? manipulatorParams.size() : 0,
                merged.size());

        return merged;
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
