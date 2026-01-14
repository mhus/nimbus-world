package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Line Block Manipulator - creates a line between two points.
 *
 * Parameters (Variant 1 - from/to):
 * - from: {x, y, z} - Start position (required)
 * - to: {x, y, z} - End position (required)
 * - blockType: string - Block type to use (default from defaults)
 *
 * Parameters (Variant 2 - position/dimensions):
 * - position: {x, y, z} - Start position (required)
 * - width: integer - Extent in X direction (optional, default: 0)
 * - height: integer - Extent in Y direction (optional, default: 0)
 * - depth: integer - Extent in Z direction (optional, default: 0)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example with from/to:
 * <pre>
 * {
 *   "line": {
 *     "from": {"x": 100, "y": 64, "z": 100},
 *     "to": {"x": 120, "y": 74, "z": 110},
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 *
 * Example with position/dimensions:
 * <pre>
 * {
 *   "line": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "width": 20,
 *     "height": 10,
 *     "depth": 10,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class LineBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "line";
    }

    @Override
    public String getTitle() {
        return "Line Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a line between two points. " +
                "Parameters (Variant 1): from {x,y,z}, to {x,y,z}, blockType (optional). " +
                "Parameters (Variant 2): position {x,y,z}, width, height, depth, blockType (optional). " +
                "Example: {\"line\": {\"from\": {\"x\": 0, \"y\": 0, \"z\": 0}, \"to\": {\"x\": 10, \"y\": 5, \"z\": 0}}} " +
                "or {\"line\": {\"position\": {\"x\": 0, \"y\": 0, \"z\": 0}, \"width\": 10, \"height\": 5}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        Integer x1, y1, z1, x2, y2, z2;

        // Check which variant is used
        JsonNode fromNode = context.getJsonParameter("from");
        JsonNode toNode = context.getJsonParameter("to");
        JsonNode positionNode = context.getJsonParameter("position");

        if (fromNode != null && toNode != null) {
            // Variant 1: from/to
            if (!fromNode.isObject()) {
                return ManipulatorResult.error("Invalid parameter 'from' - must be {x, y, z}");
            }
            if (!toNode.isObject()) {
                return ManipulatorResult.error("Invalid parameter 'to' - must be {x, y, z}");
            }

            x1 = fromNode.has("x") ? fromNode.get("x").asInt() : null;
            y1 = fromNode.has("y") ? fromNode.get("y").asInt() : null;
            z1 = fromNode.has("z") ? fromNode.get("z").asInt() : null;

            if (x1 == null || y1 == null || z1 == null) {
                return ManipulatorResult.error("Invalid 'from' position: x, y, z coordinates required");
            }

            x2 = toNode.has("x") ? toNode.get("x").asInt() : null;
            y2 = toNode.has("y") ? toNode.get("y").asInt() : null;
            z2 = toNode.has("z") ? toNode.get("z").asInt() : null;

            if (x2 == null || y2 == null || z2 == null) {
                return ManipulatorResult.error("Invalid 'to' position: x, y, z coordinates required");
            }

        } else if (positionNode != null) {
            // Variant 2: position + dimensions
            if (!positionNode.isObject()) {
                return ManipulatorResult.error("Invalid parameter 'position' - must be {x, y, z}");
            }

            x1 = positionNode.has("x") ? positionNode.get("x").asInt() : null;
            y1 = positionNode.has("y") ? positionNode.get("y").asInt() : null;
            z1 = positionNode.has("z") ? positionNode.get("z").asInt() : null;

            if (x1 == null || y1 == null || z1 == null) {
                return ManipulatorResult.error("Invalid 'position': x, y, z coordinates required");
            }

            // Extract dimensions (default to 0)
            Integer width = context.getIntParameter("width");
            Integer height = context.getIntParameter("height");
            Integer depth = context.getIntParameter("depth");

            if (width == null) width = 0;
            if (height == null) height = 0;
            if (depth == null) depth = 0;

            // Calculate to position
            x2 = x1 + width;
            y2 = y1 + height;
            z2 = z1 + depth;

            log.debug("Calculated line from position: from=({},{},{}), to=({},{},{}), dimensions=({},{},{})",
                    x1, y1, z1, x2, y2, z2, width, height, depth);

        } else {
            return ManipulatorResult.error("Missing required parameters. " +
                    "Use either 'from' + 'to' or 'position' + dimensions (width/height/depth)");
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

        // Generate line
        log.info("Generating line: from=({},{},{}), to=({},{},{}), blockType={}",
                x1, y1, z1, x2, y2, z2, blockType);

        painter.line(x1, y1, z1, x2, y2, z2);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);
        int distance = (int) Math.sqrt(dx*dx + dy*dy + dz*dz);

        String message = String.format("Generated line: %d blocks (~%d units) from (%d,%d,%d) to (%d,%d,%d)",
                blockCount, distance, x1, y1, z1, x2, y2, z2);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
