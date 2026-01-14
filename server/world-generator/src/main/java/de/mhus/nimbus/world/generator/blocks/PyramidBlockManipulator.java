package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pyramid Block Manipulator - creates a filled pyramid.
 *
 * Parameters:
 * - position: {x, y, z} - Bottom center position (required)
 * - size: integer - Base size (required)
 * - height: integer - Pyramid height (required)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "pyramid": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "size": 20,
 *     "height": 15,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class PyramidBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "pyramid";
    }

    @Override
    public String getTitle() {
        return "Pyramid Generator (Filled)";
    }

    @Override
    public String getDescription() {
        return "Creates a filled pyramid with square base. " +
                "Parameters: position {x,y,z}, size, height, blockType (optional). " +
                "Example: {\"pyramid\": {\"transform\": \"position\", \"size\": 20, \"height\": 15}}";
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
        Integer size = context.getIntParameter("size");
        Integer height = context.getIntParameter("height");

        if (size == null || size <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'size' (must be > 0)");
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

        // Generate pyramid
        log.info("Generating filled pyramid: pos=({},{},{}), size={}, height={}, blockType={}",
                x, y, z, size, height, blockType);

        painter.pyramid(x, y, z, size, height);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated filled pyramid: %d blocks (base %dx%d, height %d) at (%d,%d,%d)",
                blockCount, size, size, height, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
