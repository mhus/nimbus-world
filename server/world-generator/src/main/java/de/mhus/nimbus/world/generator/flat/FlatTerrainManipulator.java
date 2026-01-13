package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Flat terrain manipulator.
 * Generates simple flat terrain at configurable height.
 * <p>
 * Parameters:
 * - groundLevel: Height of the terrain (default: 64)
 */
@Component
@Slf4j
public class FlatTerrainManipulator implements FlatManipulator {

    public static final String NAME = "flat";
    public static final String PARAM_GROUND_LEVEL = "groundLevel";

    private static final int DEFAULT_GROUND_LEVEL = 64;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ, Map<String, String> parameters) {
        log.debug("Manipulating flat terrain: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int groundLevel = parseIntParameter(parameters, PARAM_GROUND_LEVEL, DEFAULT_GROUND_LEVEL);

        // Clamp ground level to valid range
        groundLevel = Math.max(0, Math.min(255, groundLevel));

        int oceanLevel = flat.getOceanLevel();

        // Generate flat terrain
        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int flatX = x + localX;
                int flatZ = z + localZ;

                // Set level (height)
                flat.setLevel(flatX, flatZ, groundLevel);

                // Set column (material based on height vs water level)
                int materialId = groundLevel <= oceanLevel
                        ? FlatMaterialService.SAND
                        : FlatMaterialService.GRASS;
                flat.setColumn(flatX, flatZ, materialId);
            }
        }

        log.info("Flat terrain manipulated: region=({},{},{},{}), groundLevel={}",
                x, z, sizeX, sizeZ, groundLevel);
    }

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
