package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tunnel Block Manipulator - creates a tunnel between two points.
 *
 * Parameters:
 * - position: {x, y, z} - Start position (required)
 * - endPosition: {x, y, z} - End position (required)
 * - width: integer - Width of tunnel (required)
 * - height: integer - Height of tunnel (required)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "tunnel": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "endPosition": {"x": 150, "y": 64, "z": 150},
 *     "width": 3,
 *     "height": 4,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class TunnelBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "tunnel";
    }

    @Override
    public String getTitle() {
        return "Tunnel Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a tunnel between two points. " +
                "Parameters: position {x,y,z}, endPosition {x,y,z}, width, height, blockType (optional). " +
                "Example: {\"tunnel\": {\"transform\": \"position\", \"endPosition\": {\"x\": 150, \"y\": 64, \"z\": 150}, \"width\": 3, \"height\": 4}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Extract start position parameter
        JsonNode positionNode = context.getJsonParameter("position");
        if (positionNode == null || !positionNode.isObject()) {
            return ManipulatorResult.error("Missing required parameter 'position' {x, y, z}");
        }

        Integer x1 = positionNode.has("x") ? positionNode.get("x").asInt() : null;
        Integer y1 = positionNode.has("y") ? positionNode.get("y").asInt() : null;
        Integer z1 = positionNode.has("z") ? positionNode.get("z").asInt() : null;

        if (x1 == null || y1 == null || z1 == null) {
            return ManipulatorResult.error("Invalid position: x, y, z coordinates required");
        }

        // Extract end position parameter
        JsonNode endPositionNode = context.getJsonParameter("endPosition");
        if (endPositionNode == null || !endPositionNode.isObject()) {
            return ManipulatorResult.error("Missing required parameter 'endPosition' {x, y, z}");
        }

        Integer x2 = endPositionNode.has("x") ? endPositionNode.get("x").asInt() : null;
        Integer y2 = endPositionNode.has("y") ? endPositionNode.get("y").asInt() : null;
        Integer z2 = endPositionNode.has("z") ? endPositionNode.get("z").asInt() : null;

        if (x2 == null || y2 == null || z2 == null) {
            return ManipulatorResult.error("Invalid endPosition: x, y, z coordinates required");
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

        // Generate tunnel
        log.info("Generating tunnel: start=({},{},{}), end=({},{},{}), width={}, height={}, blockType={}",
                x1, y1, z1, x2, y2, z2, width, height, blockType);

        painter.tunnel(x1, y1, z1, x2, y2, z2, width, height);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated tunnel: %d blocks (width %d, height %d) from (%d,%d,%d) to (%d,%d,%d)",
                blockCount, width, height, x1, y1, z1, x2, y2, z2);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
