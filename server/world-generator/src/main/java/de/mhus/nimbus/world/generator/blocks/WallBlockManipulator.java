package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Wall Block Manipulator - creates a vertical wall.
 *
 * Parameters:
 * - position: {x, y, z} - Starting position (required)
 * - width: integer - Width of the wall (required)
 * - height: integer - Height of the wall (required)
 * - direction: string - Direction: "N", "E", "S", "W", "X", "Z" (required)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Direction guide:
 * - "N" or "Z": Wall extends along Z-axis (North-South)
 * - "E" or "X": Wall extends along X-axis (East-West)
 * - "S": Same as "Z"
 * - "W": Same as "X"
 *
 * Example:
 * <pre>
 * {
 *   "g_wall": {
 *     "width": 10,
 *     "height": 5,
 *     "direction": "N",
 *     "blockType": "n:s",
 *     "position": {"x": 100, "y": 64, "z": 100}
 *   }
 * }
 * </pre>
 *
 * With transformations:
 * <pre>
 * {
 *   "g_wall": {
 *     "transform": "position,forward",
 *     "width": 10,
 *     "height": 5,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class WallBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "g_wall";
    }

    @Override
    public String getTitle() {
        return "Wall Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a vertical wall extending in the specified direction. " +
                "Parameters: position {x,y,z}, width, height, direction (N/E/S/W/X/Z), blockType (optional). " +
                "Example: {\"wall\": {\"transform\": \"position\", \"width\": 10, \"height\": 5, \"direction\": \"N\"}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Extract position parameter
        JsonNode positionNode = context.getJsonParameter("position");
        if (positionNode == null || !positionNode.isObject()) {
            return ManipulatorResult.error("Missing required parameter 'position' {x, y, z}");
        }

        Integer x = positionNode.has("x") ? positionNode.get("x").asInt() : null;
        Integer y = positionNode.has("y") ? positionNode.get("y").asInt() : null;
        Integer z = positionNode.has("z") ? positionNode.get("z").asInt() : null;

        if (x == null || y == null || z == null) {
            return ManipulatorResult.error("Invalid position: x, y, z coordinates required");
        }

        // Extract dimensions
        Integer width = context.getIntParameter("width");
        Integer height = context.getIntParameter("height");

        if (width == null || width <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'width' (must be > 0)");
        }

        if (height == null || height <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'height' (must be > 0)");
        }

        // Extract direction
        String direction = context.getParameter("direction");
        if (direction == null || direction.isBlank()) {
            return ManipulatorResult.error("Missing required parameter 'direction' (N/E/S/W/X/Z)");
        }

        direction = direction.toUpperCase();
        boolean alongX = false; // true = X-axis (E-W), false = Z-axis (N-S)

        switch (direction) {
            case "N":
            case "S":
            case "Z":
                alongX = false;
                break;
            case "E":
            case "W":
            case "X":
                alongX = true;
                break;
            default:
                return ManipulatorResult.error("Invalid direction '" + direction + "'. Must be N, E, S, W, X, or Z");
        }

        // Extract blockType
        String blockType = context.getParameter("blockType");
        if (blockType == null || blockType.isBlank()) {
            return ManipulatorResult.error("Missing required parameter 'blockType'");
        }

        // Parse block definition
        BlockDef blockDef = BlockDef.of(blockType).orElse(null);
        if (blockDef == null) {
            return ManipulatorResult.error("Invalid blockType: " + blockType);
        }

        // Create painter
        BlockManipulatorService service = context.getService();
        EditCachePainter painter;
        try {
            painter = service.createBlockPainter(context, blockDef);
        } catch (BlockManipulatorException e) {
            return ManipulatorResult.error("Failed to create painter: " + e.getMessage());
        }

        // Generate wall
        log.info("Generating wall: pos=({},{},{}), width={}, height={}, direction={}, blockType={}",
                x, y, z, width, height, direction, blockType);

        if (alongX) {
            // Wall extends along X-axis (E-W)
            painter.rectangleX(x, y, z, height, width);
        } else {
            // Wall extends along Z-axis (N-S)
            painter.rectangleZ(x, y, z, width, height);
        }

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated wall: %d blocks (%dx%d, %s) at (%d,%d,%d)",
                blockCount, width, height, direction, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
