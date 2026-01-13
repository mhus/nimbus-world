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
 * Parameters:
 * - from: {x, y, z} - Start position (required)
 * - to: {x, y, z} - End position (required)
 * - blockType: string - Block type to use (default from defaults)
 *
 * Example:
 * <pre>
 * {
 *   "line": {
 *     "from": {"x": 100, "y": 64, "z": 100},
 *     "to": {"x": 120, "y": 74, "z": 110},
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
                "Parameters: from {x,y,z}, to {x,y,z}, blockType (optional). " +
                "Example: {\"line\": {\"from\": {\"x\": 0, \"y\": 0, \"z\": 0}, \"to\": {\"x\": 10, \"y\": 5, \"z\": 0}}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Extract from position
        JsonNode fromNode = context.getJsonParameter("from");
        if (fromNode == null || !fromNode.isObject()) {
            return ManipulatorResult.error("Missing required parameter 'from' {x, y, z}");
        }

        Integer x1 = fromNode.has("x") ? fromNode.get("x").asInt() : null;
        Integer y1 = fromNode.has("y") ? fromNode.get("y").asInt() : null;
        Integer z1 = fromNode.has("z") ? fromNode.get("z").asInt() : null;

        if (x1 == null || y1 == null || z1 == null) {
            return ManipulatorResult.error("Invalid 'from' position: x, y, z coordinates required");
        }

        // Extract to position
        JsonNode toNode = context.getJsonParameter("to");
        if (toNode == null || !toNode.isObject()) {
            return ManipulatorResult.error("Missing required parameter 'to' {x, y, z}");
        }

        Integer x2 = toNode.has("x") ? toNode.get("x").asInt() : null;
        Integer y2 = toNode.has("y") ? toNode.get("y").asInt() : null;
        Integer z2 = toNode.has("z") ? toNode.get("z").asInt() : null;

        if (x2 == null || y2 == null || z2 == null) {
            return ManipulatorResult.error("Invalid 'to' position: x, y, z coordinates required");
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
