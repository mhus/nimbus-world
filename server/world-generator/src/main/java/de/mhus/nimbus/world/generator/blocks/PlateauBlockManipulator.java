package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Plateau block manipulator - creates a flat rectangular platform.
 *
 * Parameters:
 * - position: {x, y, z} - Starting position (required)
 * - width: integer - Width in X direction (required)
 * - depth: integer - Depth in Z direction (required)
 * - blockType: string - Block type to use (default from defaults)
 *
 * Example:
 * <pre>
 * {
 *   "plateau": {
 *     "width": 10,
 *     "depth": 10,
 *     "blockType": "n:s",
 *     "position": {"x": 100, "y": 64, "z": 100}
 *   }
 * }
 * </pre>
 *
 * Can be combined with transformations:
 * <pre>
 * {
 *   "plateau": {
 *     "transform": "position,forward",
 *     "width": 10,
 *     "depth": 5,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class PlateauBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "plateau";
    }

    @Override
    public String getTitle() {
        return "Plateau Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a flat rectangular platform. Parameters: position {x,y,z}, width, depth, blockType (optional). " +
                "Example: {\"plateau\": {\"transform\": \"position,forward\", \"width\": 10, \"depth\": 5}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Extract parameters
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

        Integer width = context.getIntParameter("width");
        Integer depth = context.getIntParameter("depth");

        if (width == null || width <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'width' (must be > 0)");
        }

        if (depth == null || depth <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'depth' (must be > 0)");
        }

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

        // Generate plateau using rectangleY (filled rectangle in XZ plane)
        log.info("Generating plateau: pos=({},{},{}), width={}, depth={}, blockType={}",
                x, y, z, width, depth, blockType);

        painter.rectangleY(x, y, z, width, depth);

        // Get ModelSelector from context (automatically filled by EditCachePainter)
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated plateau: %d blocks (%dx%d) at (%d,%d,%d)",
                blockCount, width, depth, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
