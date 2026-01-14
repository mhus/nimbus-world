package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tower Block Manipulator - creates a tower (cylinder with cone roof).
 *
 * Parameters:
 * - position: {x, y, z} - Bottom center position (required)
 * - radius: integer - Radius of tower (required)
 * - bodyHeight: integer - Height of cylinder body (required)
 * - roofHeight: integer - Height of cone roof (required)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "tower": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "radius": 6,
 *     "bodyHeight": 20,
 *     "roofHeight": 8,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class TowerBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "tower";
    }

    @Override
    public String getTitle() {
        return "Tower Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a tower with cylinder body and cone roof. " +
                "Parameters: position {x,y,z}, radius, bodyHeight, roofHeight, blockType (optional). " +
                "Example: {\"tower\": {\"transform\": \"position\", \"radius\": 6, \"bodyHeight\": 20, \"roofHeight\": 8}}";
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
        Integer radius = context.getIntParameter("radius");
        Integer bodyHeight = context.getIntParameter("bodyHeight");
        Integer roofHeight = context.getIntParameter("roofHeight");

        if (radius == null || radius <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'radius' (must be > 0)");
        }

        if (bodyHeight == null || bodyHeight <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'bodyHeight' (must be > 0)");
        }

        if (roofHeight == null || roofHeight <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'roofHeight' (must be > 0)");
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

        // Generate tower
        log.info("Generating tower: pos=({},{},{}), radius={}, bodyHeight={}, roofHeight={}, blockType={}",
                x, y, z, radius, bodyHeight, roofHeight, blockType);

        painter.tower(x, y, z, radius, bodyHeight, roofHeight);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated tower: %d blocks (radius %d, body %d, roof %d) at (%d,%d,%d)",
                blockCount, radius, bodyHeight, roofHeight, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
