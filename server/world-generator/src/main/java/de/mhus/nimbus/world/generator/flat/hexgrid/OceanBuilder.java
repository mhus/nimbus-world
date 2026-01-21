package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.HillyTerrainManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Ocean scenario builder.
 * Creates deep ocean with hilly ocean floor using HillyTerrainManipulator.
 */
@Slf4j
public class OceanBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Building ocean scenario for flat: {}",
                flat.getFlatId());

        int oceanLevel = flat.getOceanLevel();

        // Use getHexGridLevel() as baseHeight (PARAM_BASE_HEIGHT in HillyTerrainManipulator)
        // Use getLandOffset() as hillHeight (PARAM_HILL_HEIGHT in HillyTerrainManipulator)
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

        // Set all to sand material for ocean floor
        for (int z = 0; z < flat.getSizeZ(); z++) {
            for (int x = 0; x < flat.getSizeX(); x++) {
                flat.setColumn(x, z, FlatMaterialService.SAND);
            }
        }

        log.info("Ocean scenario completed: baseHeight={}, hillHeight={}, oceanLevel={}",
                baseHeight, hillHeight, oceanLevel);
    }

    @Override
    protected int getDefaultLandOffset() {
        return 7;  // OCEAN: medium variation for ocean floor
    }

    @Override
    protected int getDefaultLandLevel() {
        return -10;  // OCEAN: below ocean level
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
