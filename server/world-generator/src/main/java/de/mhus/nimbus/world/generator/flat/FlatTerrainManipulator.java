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
        log.debug("Manipulating flat terrain: region=({},{},{},{}), parameters={}", x, z, sizeX, sizeZ, parameters);

        // Parse parameters
        int groundLevel = parseIntParameter(parameters, PARAM_GROUND_LEVEL, DEFAULT_GROUND_LEVEL);

        // Clamp ground level to valid range
        groundLevel = Math.max(0, Math.min(255, groundLevel));

        log.info("FlatTerrainManipulator: groundLevel={}, oceanLevel={}, unknownProtected={}, borderProtected={}",
                groundLevel, flat.getSeaLevel(), flat.isUnknownProtected(), flat.isBorderProtected());

        int oceanLevel = flat.getSeaLevel();

        // Generate flat terrain
        int successCount = 0;
        int failedLevelCount = 0;
        int failedColumnCount = 0;

        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int flatX = x + localX;
                int flatZ = z + localZ;

                // Check current state
                int currentColumn = flat.getColumn(flatX, flatZ);
                int currentLevel = flat.getLevel(flatX, flatZ);

                // Calculate material based on height vs water level
                int materialId = groundLevel <= oceanLevel
                        ? FlatMaterialService.SAND
                        : FlatMaterialService.GRASS;

                // IMPORTANT: Set column FIRST, then level
                // This is required for flats with unknownProtected=true (e.g. HexGrid flats)
                // where setLevel() will fail if column is NOT_SET (0)
                boolean columnSet = flat.setColumn(flatX, flatZ, materialId);
                boolean levelSet = flat.setLevel(flatX, flatZ, groundLevel);

                if (columnSet && levelSet) {
                    successCount++;
                } else {
                    if (!columnSet) failedColumnCount++;
                    if (!levelSet) failedLevelCount++;

                    // Log first few failures for debugging
                    if (failedLevelCount + failedColumnCount <= 5) {
                        log.warn("Failed to set position ({},{}): currentColumn={}, currentLevel={}, columnSet={}, levelSet={}",
                                flatX, flatZ, currentColumn, currentLevel, columnSet, levelSet);
                    }
                }
            }
        }

        log.info("Flat terrain manipulated: region=({},{},{},{}), groundLevel={}, success={}, failedColumn={}, failedLevel={}",
                x, z, sizeX, sizeZ, groundLevel, successCount, failedColumnCount, failedLevelCount);
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
