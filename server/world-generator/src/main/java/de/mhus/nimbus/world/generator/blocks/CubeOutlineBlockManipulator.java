package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cube Outline Block Manipulator - creates a hollow rectangular cuboid (only edges/frame).
 *
 * Parameters:
 * - position: {x, y, z} - Starting position (required)
 * - width: integer - Width in X direction (required)
 * - height: integer - Height in Y direction (required)
 * - depth: integer - Depth in Z direction (required)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "cube-outline": {
 *     "width": 10,
 *     "height": 5,
 *     "depth": 10,
 *     "blockType": "n:s",
 *     "position": {"x": 100, "y": 64, "z": 100}
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class CubeOutlineBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "cube-outline";
    }

    @Override
    public String getTitle() {
        return "Cube Outline Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a hollow rectangular cuboid (only faces, no fill). " +
                "Parameters: position {x,y,z}, width, height, depth, blockType (optional). " +
                "Example: {\"cube-outline\": {\"transform\": \"position\", \"width\": 10, \"height\": 5, \"depth\": 10}}";
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
        Integer depth = context.getIntParameter("depth");

        if (width == null || width <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'width' (must be > 0)");
        }

        if (height == null || height <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'height' (must be > 0)");
        }

        if (depth == null || depth <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'depth' (must be > 0)");
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

        // Generate cube outline
        log.info("Generating cube outline: pos=({},{},{}), size={}x{}x{}, blockType={}",
                x, y, z, width, height, depth, blockType);

        painter.cubeOutline(x, y, z, width, height, depth);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated cube outline: %d blocks (%dx%dx%d) at (%d,%d,%d)",
                blockCount, width, height, depth, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
