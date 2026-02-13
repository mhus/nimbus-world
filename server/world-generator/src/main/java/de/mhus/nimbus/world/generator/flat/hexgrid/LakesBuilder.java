package de.mhus.nimbus.world.generator.flat.hexgrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.composer.point.LakesPoint;
import de.mhus.nimbus.world.generator.flat.manipulator.LakesManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * LakesBuilder builds a lake system from LakesPoint configuration.
 * Creates a main lake with multiple smaller lakes scattered around.
 *
 * This builder reads the g_lakes parameter and uses LakesManipulator
 * to create the lake system with appropriate sizes and distribution.
 */
@Slf4j
public class LakesBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        // Get actual hexGridSize from world context
        int hexGridSize = context.getHexGridSize();

        log.debug("Building lakes for flat: {} with hexGridSize: {}", flat.getFlatId(), hexGridSize);

        // Get lakes parameter from hex grid
        String lakesParam = hexGrid.getParameters() != null ?
            hexGrid.getParameters().get("g_lakes") : null;

        if (lakesParam == null || lakesParam.isBlank()) {
            log.debug("No lakes parameter found, skipping");
            return;
        }

        try {
            // Parse lakes configuration
            LakesPoint.LakesConfig config = objectMapper.readValue(
                lakesParam, LakesPoint.LakesConfig.class);

            log.debug("Parsed lakes config for '{}': mainRadius={}, smallLakes={}",
                config.getLakesName(), config.getMainLakeRadius(), config.getSmallLakes());

            // Build the lake system
            buildLakes(flat, config, hexGridSize);

            log.debug("Lakes '{}' completed", config.getLakesName());

        } catch (Exception e) {
            log.error("Failed to build lakes for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Build a lake system using LakesManipulator
     */
    private void buildLakes(WFlat flat, LakesPoint.LakesConfig config, int hexGridSize) {
        // Calculate the region to manipulate (entire flat)
        int regionX = 0;
        int regionZ = 0;
        int regionSizeX = flat.getSizeX();
        int regionSizeZ = flat.getSizeZ();

        log.debug("Building lakes at flat center with mainRadius={}, depth={}, smallLakes={}",
            config.getMainLakeRadius(), config.getMainLakeDepth(), config.getSmallLakes());

        // Create parameters map for LakesManipulator
        Map<String, String> manipulatorParams = new HashMap<>();
        manipulatorParams.put(LakesManipulator.PARAM_MAIN_LAKE_RADIUS, String.valueOf(config.getMainLakeRadius()));
        manipulatorParams.put(LakesManipulator.PARAM_MAIN_LAKE_DEPTH, String.valueOf(config.getMainLakeDepth()));
        manipulatorParams.put(LakesManipulator.PARAM_SMALL_LAKES, String.valueOf(config.getSmallLakes()));
        manipulatorParams.put(LakesManipulator.PARAM_SMALL_LAKE_MIN_RADIUS, String.valueOf(config.getSmallLakeMinRadius()));
        manipulatorParams.put(LakesManipulator.PARAM_SMALL_LAKE_MAX_RADIUS, String.valueOf(config.getSmallLakeMaxRadius()));
        manipulatorParams.put(LakesManipulator.PARAM_SCATTER_DISTANCE, String.valueOf(config.getScatterDistance()));
        manipulatorParams.put(LakesManipulator.PARAM_SEED, String.valueOf(config.getSeed()));

        // Create LakesManipulator and apply
        LakesManipulator manipulator = new LakesManipulator();
        manipulator.manipulate(flat, regionX, regionZ, regionSizeX, regionSizeZ, manipulatorParams);

        log.info("Lake system built: name='{}', mainRadius={}, mainDepth={}, smallLakes={}",
            config.getLakesName(), config.getMainLakeRadius(), config.getMainLakeDepth(),
            config.getSmallLakes());
    }

    @Override
    protected int getDefaultOffset() {
        return 0;
    }

    @Override
    protected int getDefaultAsl() {
        return 0;
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }
}
