package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.flat.hexgrid.BuilderContext;
import de.mhus.nimbus.world.generator.flat.hexgrid.CompositionBuilder;
import de.mhus.nimbus.world.generator.flat.hexgrid.HexGridBuilderService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

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
    public static final String PARAM_TYPE = "g.type";

    @Autowired
    private WHexGridService hexGridService;

    @Autowired
    private HexGridBuilderService builderService;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.info("Starting hex-grid manipulation: flat={}, parameters={}", flat.getFlatId(), parameters);

        // Load hex grid configuration
        WHexGrid hexGrid = loadHexGrid(flat);
        if (hexGrid == null) {
            throw new IllegalStateException("No hex grid found for flat: " + flat.getFlatId());
        }

        // Extract scenario type
        String type = parameters != null ? parameters.get(PARAM_TYPE) : null;
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Parameter 'g.type' is required");
        }

        // Merge hex grid parameters with manipulator parameters
        Map<String, String> mergedParameters = extractHexGridParameters(hexGrid, parameters);

        // Get builder for scenario type
        CompositionBuilder builder = builderService.getBuilder(type);
        if (builder == null) {
            throw new IllegalArgumentException("Unknown scenario type: " + type +
                    ". Available types: " + builderService.getAvailableTypes());
        }

        // Load neighbor grids and extract their types
        Map<WHexGrid.NEIGHBOR, WHexGrid> neighborGrids = loadNeighborGrids(hexGrid, flat.getWorldId());
        Map<WHexGrid.NEIGHBOR, String> neighborTypes = extractNeighborTypes(neighborGrids);

        // Build context
        BuilderContext context = BuilderContext.builder()
                .flat(flat)
                .hexGrid(hexGrid)
                .parameters(mergedParameters)
                .neighborGrids(neighborGrids)
                .neighborTypes(neighborTypes)
                .build();

        // Build the scenario
        log.info("Building scenario '{}' for hex grid: {}, neighbors: {}",
                type, hexGrid.getPosition(), neighborTypes);
        builder.build(context);

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
     * Parameters from hex grid with 'gf.' prefix are included.
     * Manipulator parameters override hex grid parameters.
     */
    private Map<String, String> extractHexGridParameters(WHexGrid hexGrid, Map<String, String> manipulatorParams) {
        Map<String, String> merged = new HashMap<>();

        // Add hex grid parameters with 'gf.' prefix
        if (hexGrid.getParameters() != null) {
            hexGrid.getParameters().forEach((key, value) -> {
                if (key.startsWith("gf.")) {
                    merged.put(key, value);
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
    private Map<WHexGrid.NEIGHBOR, WHexGrid> loadNeighborGrids(WHexGrid hexGrid, String worldId) {
        Map<WHexGrid.NEIGHBOR, WHexGrid> neighborGrids = new EnumMap<>(WHexGrid.NEIGHBOR.class);

        // Get all neighbor positions
        Map<WHexGrid.NEIGHBOR, HexVector2> neighborPositions = hexGrid.getAllNeighborPositions();

        // Load each neighbor grid if it exists
        for (Map.Entry<WHexGrid.NEIGHBOR, HexVector2> entry : neighborPositions.entrySet()) {
            WHexGrid.NEIGHBOR direction = entry.getKey();
            HexVector2 position = entry.getValue();

            WHexGrid neighbor = hexGridService.findByWorldIdAndPosition(worldId, position).orElse(null);
            neighborGrids.put(direction, neighbor);

            if (neighbor != null) {
                log.debug("Loaded neighbor grid: direction={}, position={}", direction, neighbor.getPosition());
            }
        }

        return neighborGrids;
    }

    /**
     * Extract scenario types from neighbor grids.
     * Returns a map with neighbor position as key and scenario type (g.type parameter) as value.
     */
    private Map<WHexGrid.NEIGHBOR, String> extractNeighborTypes(Map<WHexGrid.NEIGHBOR, WHexGrid> neighborGrids) {
        Map<WHexGrid.NEIGHBOR, String> neighborTypes = new EnumMap<>(WHexGrid.NEIGHBOR.class);

        for (Map.Entry<WHexGrid.NEIGHBOR, WHexGrid> entry : neighborGrids.entrySet()) {
            WHexGrid.NEIGHBOR direction = entry.getKey();
            WHexGrid neighbor = entry.getValue();

            String type = null;
            if (neighbor != null && neighbor.getParameters() != null) {
                type = neighbor.getParameters().get(PARAM_TYPE);
            }

            neighborTypes.put(direction, type);
        }

        return neighborTypes;
    }
}
