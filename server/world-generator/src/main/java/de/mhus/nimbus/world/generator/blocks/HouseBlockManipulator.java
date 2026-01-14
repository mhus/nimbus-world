package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * House Block Manipulator - creates a predefined house template.
 *
 * Parameters:
 * - position: {x, y, z} - Bottom left corner position (required)
 * - width: integer - Width of house (required, min 3)
 * - length: integer - Length of house (required, min 3)
 * - wallHeight: integer - Height of walls (required, min 2)
 * - roofHeight: integer - Height of roof (required, min 1)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "house": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "width": 10,
 *     "length": 12,
 *     "wallHeight": 5,
 *     "roofHeight": 4,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class HouseBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "house";
    }

    @Override
    public String getTitle() {
        return "House Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a predefined house with walls, floor, roof, door and windows. " +
                "Parameters: position {x,y,z}, width, length, wallHeight, roofHeight, blockType (optional). " +
                "Example: {\"house\": {\"transform\": \"position\", \"width\": 10, \"length\": 12, \"wallHeight\": 5, \"roofHeight\": 4}}";
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
        Integer length = context.getIntParameter("length");
        Integer wallHeight = context.getIntParameter("wallHeight");
        Integer roofHeight = context.getIntParameter("roofHeight");

        if (width == null || width < 3) {
            return ManipulatorResult.error("Missing or invalid parameter 'width' (must be >= 3)");
        }

        if (length == null || length < 3) {
            return ManipulatorResult.error("Missing or invalid parameter 'length' (must be >= 3)");
        }

        if (wallHeight == null || wallHeight < 2) {
            return ManipulatorResult.error("Missing or invalid parameter 'wallHeight' (must be >= 2)");
        }

        if (roofHeight == null || roofHeight < 1) {
            return ManipulatorResult.error("Missing or invalid parameter 'roofHeight' (must be >= 1)");
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

        // Generate house
        log.info("Generating house: pos=({},{},{}), width={}, length={}, wallHeight={}, roofHeight={}, blockType={}",
                x, y, z, width, length, wallHeight, roofHeight, blockType);

        painter.house(x, y, z, width, length, wallHeight, roofHeight);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated house: %d blocks (%dx%d, wall height %d, roof height %d) at (%d,%d,%d)",
                blockCount, width, length, wallHeight, roofHeight, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
