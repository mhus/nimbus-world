package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stairs Block Manipulator - creates stairs in a specified direction.
 *
 * Parameters:
 * - position: {x, y, z} - Start position (required)
 * - steps: integer - Number of steps (required)
 * - direction: string - Direction: "north", "south", "east", "west" (required)
 * - width: integer - Width of stairs perpendicular to direction (default: 1)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "stairs": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "steps": 10,
 *     "direction": "north",
 *     "width": 3,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class StairsBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "stairs";
    }

    @Override
    public String getTitle() {
        return "Stairs Generator";
    }

    @Override
    public String getDescription() {
        return "Creates stairs in a specified direction. " +
                "Parameters: position {x,y,z}, steps, direction (north/south/east/west), width (optional), blockType (optional). " +
                "Example: {\"stairs\": {\"transform\": \"position\", \"steps\": 10, \"direction\": \"north\", \"width\": 3}}";
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

        // Extract steps
        Integer steps = context.getIntParameter("steps");
        if (steps == null || steps <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'steps' (must be > 0)");
        }

        // Extract direction
        String direction = context.getParameter("direction");
        if (direction == null || direction.isBlank()) {
            return ManipulatorResult.error("Missing required parameter 'direction' (north/south/east/west)");
        }

        // Convert direction to dirX, dirZ
        int dirX = 0, dirZ = 0;
        switch (direction.toLowerCase()) {
            case "north" -> dirZ = -1;
            case "south" -> dirZ = 1;
            case "east" -> dirX = 1;
            case "west" -> dirX = -1;
            default -> {
                return ManipulatorResult.error("Invalid direction: " + direction + " (use north/south/east/west)");
            }
        }

        // Extract width (optional, default 1)
        Integer width = context.getIntParameter("width");
        if (width == null) {
            width = 1;
        }
        if (width <= 0) {
            return ManipulatorResult.error("Invalid parameter 'width' (must be > 0)");
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

        // Generate stairs
        log.info("Generating stairs: pos=({},{},{}), steps={}, direction={}, width={}, blockType={}",
                x, y, z, steps, direction, width, blockType);

        painter.stairs(x, y, z, steps, dirX, dirZ, width);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated stairs: %d blocks (%d steps, %s, width %d) at (%d,%d,%d)",
                blockCount, steps, direction, width, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
