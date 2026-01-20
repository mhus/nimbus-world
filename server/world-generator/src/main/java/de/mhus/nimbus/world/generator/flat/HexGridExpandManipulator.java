package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.HexMathUtil;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Manipulator to expand the editable HexGrid area.
 * Marks additional positions around the current HexGrid as editable (NOT_SET_MUTABLE/255)
 * by checking if positions fall within an expanded hex radius.
 *
 * Parameters:
 * - expandBy: Integer value to expand the hex grid by (default: 5)
 *   This expands the editable area without shifting the hex grid position.
 *
 * Only positions that are currently NOT_SET (0) will be changed to NOT_SET_MUTABLE (255).
 * Temporarily disables unknownProtected during expansion, then re-enables it.
 */
@Component
@Slf4j
public class HexGridExpandManipulator implements FlatManipulator {

    public static final String NAME = "hex-grid-expand";
    public static final String PARAM_EXPAND_BY = "expandBy";

    @Autowired
    private WHexGridService hexGridService;

    @Autowired
    private WWorldService worldService;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.info("Starting hex-grid expansion: flat={}, parameters={}", flat.getFlatId(), parameters);

        // Get expandBy parameter
        int expandBy = parseIntParameter(parameters, PARAM_EXPAND_BY, 5);
        if (expandBy < 0) {
            throw new IllegalArgumentException("expandBy must be >= 0, got: " + expandBy);
        }

        log.info("Expanding HexGrid by {} pixels", expandBy);

        // Load hex grid configuration
        HexVector2 hexGridPos = flat.getHexGrid();
        if (hexGridPos == null) {
            throw new IllegalStateException("No hex grid assigned to flat: " + flat.getFlatId());
        }

        WHexGrid hexGrid = hexGridService.findByWorldIdAndPosition(flat.getWorldId(), hexGridPos)
                .orElse(null);
        if (hexGrid == null) {
            log.warn("Hex grid not found in database, using flat's hex grid position only: {}", hexGridPos);
        }

        // Load world to get hexGridSize
        WWorld world = worldService.getByWorldId(flat.getWorldId())
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + flat.getWorldId()));

        int gridSize = world.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize);
        }

        log.debug("Using hexGridSize={} from world", gridSize);

        // Calculate hex center in cartesian coordinates
        double[] hexCenter = HexMathUtil.hexToCartesian(hexGridPos, gridSize);
        double hexCenterX = hexCenter[0];
        double hexCenterZ = hexCenter[1];

        log.debug("Hex center in cartesian: ({}, {})", hexCenterX, hexCenterZ);

        // Calculate expanded grid size (only for checking, doesn't change world's gridSize)
        int expandedGridSize = gridSize + expandBy;

        log.info("Expanding editable area from gridSize={} to expandedSize={}", gridSize, expandedGridSize);

        // Temporarily disable protection to allow modifications
        boolean wasProtected = flat.isUnknownProtected();
        flat.setUnknownProtected(false);

        int expandedCount = 0;
        int alreadyEditableCount = 0;
        int outsideCount = 0;

        // Iterate through entire flat
        for (int localX = 0; localX < flat.getSizeX(); localX++) {
            for (int localZ = 0; localZ < flat.getSizeZ(); localZ++) {
                // Get current material
                int currentMaterial = flat.getColumn(localX, localZ);

                // Only process positions that are currently NOT_SET (0)
                if (currentMaterial != WFlat.MATERIAL_NOT_SET) {
                    if (currentMaterial == WFlat.MATERIAL_NOT_SET_MUTABLE) {
                        alreadyEditableCount++;
                    }
                    continue;
                }

                // Calculate world coordinates
                int worldX = flat.getMountX() + localX;
                int worldZ = flat.getMountZ() + localZ;

                // Adjust coordinates for hex grid check (accounting for 10-pixel border offset)
                int hexCheckX = worldX + 10;
                int hexCheckZ = worldZ + 10;

                // Check if this position is inside the EXPANDED HexGrid
                boolean isInExpandedHex = HexMathUtil.isPointInHex(hexCheckX, hexCheckZ, hexCenterX, hexCenterZ, expandedGridSize);

                if (isInExpandedHex) {
                    // Position is inside expanded hex: change from NOT_SET (0) to NOT_SET_MUTABLE (255)
                    flat.setColumn(localX, localZ, WFlat.MATERIAL_NOT_SET_MUTABLE);
                    expandedCount++;
                } else {
                    outsideCount++;
                }
            }
        }

        // Re-enable protection
        flat.setUnknownProtected(wasProtected);

        log.info("Hex-grid expansion completed: flat={}, expandedBy={}, newlyEditable={}, alreadyEditable={}, outsideExpanded={}, unknownProtected={}",
                flat.getFlatId(), expandBy, expandedCount, alreadyEditableCount, outsideCount, wasProtected);
    }

    /**
     * Parse integer parameter with default value.
     */
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
