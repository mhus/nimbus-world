package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Arch Block Manipulator - creates an arch with pillars.
 *
 * Parameters:
 * - position: {x, y, z} - Bottom left corner position (required)
 * - width: integer - Width of arch (required)
 * - height: integer - Height of pillars (required)
 * - depth: integer - Depth of arch (default: 1)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "arch": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "width": 8,
 *     "height": 10,
 *     "depth": 3,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class ArchBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "arch";
    }

    @Override
    public String getTitle() {
        return "Arch Generator";
    }

    @Override
    public String getDescription() {
        return "Creates an arch with pillars. " +
                "Parameters: position {x,y,z}, width, height, depth (optional), blockType (optional). " +
                "Example: {\"arch\": {\"transform\": \"position\", \"width\": 8, \"height\": 10, \"depth\": 3}}";
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

        if (width == null || width < 3) {
            return ManipulatorResult.error("Missing or invalid parameter 'width' (must be >= 3)");
        }

        if (height == null || height < 2) {
            return ManipulatorResult.error("Missing or invalid parameter 'height' (must be >= 2)");
        }

        // Extract depth (optional, default 1)
        Integer depth = context.getIntParameter("depth");
        if (depth == null) {
            depth = 1;
        }
        if (depth <= 0) {
            return ManipulatorResult.error("Invalid parameter 'depth' (must be > 0)");
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

        // Generate arch
        log.info("Generating arch: pos=({},{},{}), width={}, height={}, depth={}, blockType={}",
                x, y, z, width, height, depth, blockType);

        painter.arch(x, y, z, width, height, depth);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated arch: %d blocks (width %d, height %d, depth %d) at (%d,%d,%d)",
                blockCount, width, height, depth, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
