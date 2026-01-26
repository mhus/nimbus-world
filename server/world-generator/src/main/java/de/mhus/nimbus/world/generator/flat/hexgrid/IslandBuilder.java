package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.HillyTerrainManipulator;
import de.mhus.nimbus.world.generator.flat.IslandsManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Island scenario builder.
 * Creates island(s) surrounded by ocean with hilly ocean floor.
 * First uses HillyTerrainManipulator to create ocean floor terrain,
 * then uses IslandsManipulator to create islands.
 */
@Slf4j
public class IslandBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Building island scenario for flat: {}",
                flat.getFlatId());

        int oceanLevel = flat.getSeaLevel();

        // Step 1: Create hilly ocean floor using HillyTerrainManipulator (like OceanBuilder)
        int hillHeight = getLandOffset();
        int baseHeight = Math.min(getHexGridLevel(), oceanLevel - hillHeight + 2); // Ensure ocean floor is below ocean level

        long seed = parseLongParameter(parameters, "seed", System.currentTimeMillis());

        log.debug("Ocean floor generation: baseHeight={}, hillHeight={}, oceanLevel={}, seed={}",
                baseHeight, hillHeight, oceanLevel, seed);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));

        // Use HillyTerrainManipulator to generate ocean floor terrain
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        log.debug("Ocean floor created, now creating islands");

        // Step 2: Create islands using IslandsManipulator
        Map<String, String> islandParams = new HashMap<>();

        // Use parameters from IslandsManipulator with proper defaults
        islandParams.put(IslandsManipulator.PARAM_MAIN_ISLAND_SIZE,
                getParameterOrDefault("mainIslandSize", "40"));
        // mainIslandHeight is HEIGHT ABOVE ocean level, not absolute height!
        islandParams.put(IslandsManipulator.PARAM_MAIN_ISLAND_HEIGHT,
                getParameterOrDefault("mainIslandHeight", "2"));  // 2 pixels above ocean
        islandParams.put(IslandsManipulator.PARAM_SMALL_ISLANDS,
                getParameterOrDefault("smallIslands", "8"));
        islandParams.put(IslandsManipulator.PARAM_SMALL_ISLAND_MIN_RADIUS,
                getParameterOrDefault("smallIslandMinRadius", "8"));
        islandParams.put(IslandsManipulator.PARAM_SMALL_ISLAND_MAX_RADIUS,
                getParameterOrDefault("smallIslandMaxRadius", "15"));
        islandParams.put(IslandsManipulator.PARAM_SCATTER_DISTANCE,
                getParameterOrDefault("scatterDistance", "60"));
        islandParams.put(IslandsManipulator.PARAM_SEED, String.valueOf(seed));
        islandParams.put(IslandsManipulator.PARAM_UNDERWATER, "false");

        log.debug("Island parameters: {}", islandParams);

        // Use IslandsManipulator to create islands
        context.getManipulatorService().executeManipulator(
                IslandsManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                islandParams
        );

        log.info("Island scenario completed: baseHeight={}, hillHeight={}, oceanLevel={}",
                baseHeight, hillHeight, oceanLevel);
    }

    private String getParameterOrDefault(String mainIslandSize, String number) {
        if (parameters != null && parameters.containsKey(mainIslandSize)) {
            return parameters.get(mainIslandSize);
        }
        return number;
    }

    @Override
    protected int getDefaultLandOffset() {
        return 5;  // LAND: normal variation
    }

    @Override
    protected int getDefaultLandLevel() {
        return 15;  // LAND: above ocean level
    }

    private long parseLongParameter(Map<String, String> parameters, String name, long defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid long parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }

    public int getLandSideLevel(WHexGrid.SIDE side) {
        return getLandCenterLevel();
    }

}
