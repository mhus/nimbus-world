package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Set material manipulator.
 * Sets material for all positions within a level range.
 * <p>
 * Parameters:
 * - material: Material ID or name (required)
 * - fromLevel: Start level (inclusive) (required)
 * - toLevel: End level (inclusive) (required)
 */
@Component
@Slf4j
public class SetMaterialManipulator implements FlatManipulator {

    public static final String NAME = "set-material";
    public static final String PARAM_MATERIAL = "material";
    public static final String PARAM_FROM_LEVEL = "fromLevel";
    public static final String PARAM_TO_LEVEL = "toLevel";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting set-material manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse required parameters
        String materialParam = parameters != null ? parameters.get(PARAM_MATERIAL) : null;
        if (materialParam == null || materialParam.isBlank()) {
            throw new IllegalArgumentException("Parameter 'material' is required");
        }

        Integer fromLevel = parseIntParameter(parameters, PARAM_FROM_LEVEL, null);
        if (fromLevel == null) {
            throw new IllegalArgumentException("Parameter 'fromLevel' is required");
        }

        Integer toLevel = parseIntParameter(parameters, PARAM_TO_LEVEL, null);
        if (toLevel == null) {
            throw new IllegalArgumentException("Parameter 'toLevel' is required");
        }

        // Parse material ID
        int materialId;
        try {
            materialId = Integer.parseInt(materialParam);
        } catch (NumberFormatException e) {
            // If not a number, try to resolve as material name
            throw new IllegalArgumentException("Material must be an integer ID (material names not yet supported): " + materialParam);
        }

        // Validate parameters
        fromLevel = Math.max(0, Math.min(255, fromLevel));
        toLevel = Math.max(0, Math.min(255, toLevel));

        // Ensure fromLevel <= toLevel
        if (fromLevel > toLevel) {
            int temp = fromLevel;
            fromLevel = toLevel;
            toLevel = temp;
            log.warn("fromLevel > toLevel, swapping: fromLevel={}, toLevel={}", fromLevel, toLevel);
        }

        log.info("SetMaterialManipulator: material={}, fromLevel={}, toLevel={}, region=({},{},{},{})",
                materialId, fromLevel, toLevel, x, z, sizeX, sizeZ);

        // Apply material to all positions in level range
        int changedCount = 0;
        int skippedCount = 0;

        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int flatX = x + localX;
                int flatZ = z + localZ;

                int currentLevel = flat.getLevel(flatX, flatZ);

                // Check if level is in range
                if (currentLevel >= fromLevel && currentLevel <= toLevel) {
                    boolean success = flat.setColumn(flatX, flatZ, materialId);
                    if (success) {
                        changedCount++;
                    } else {
                        skippedCount++;
                    }
                } else {
                    skippedCount++;
                }
            }
        }

        log.info("Set material manipulation completed: material={}, fromLevel={}, toLevel={}, changed={}, skipped={}",
                materialId, fromLevel, toLevel, changedCount, skippedCount);
    }

    private Integer parseIntParameter(Map<String, String> parameters, String name, Integer defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}",
                    name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
