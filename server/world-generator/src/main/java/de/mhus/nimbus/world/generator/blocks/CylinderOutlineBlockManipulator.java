package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cylinder Outline Block Manipulator - creates a hollow cylinder (tube).
 *
 * Parameters:
 * - position: {x, y, z} - Bottom center position (required)
 * - radius: integer - Cylinder radius (required)
 * - height: integer - Cylinder height (required)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "cylinder-outline": {
 *     "radius": 5,
 *     "height": 10,
 *     "blockType": "n:s",
 *     "position": {"x": 100, "y": 64, "z": 100}
 *   }
 * }
 * </pre>
 *
 * With transformations:
 * <pre>
 * {
 *   "cylinder-outline": {
 *     "transform": "position",
 *     "radius": 8,
 *     "height": 15,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class CylinderOutlineBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "cylinder-outline";
    }

    @Override
    public String getTitle() {
        return "Cylinder Outline Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a hollow cylinder (tube). " +
                "Parameters: position {x,y,z}, radius, height, blockType (optional). " +
                "Example: {\"cylinder-outline\": {\"transform\": \"position\", \"radius\": 5, \"height\": 10}}";
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

        // Extract radius
        Integer radius = context.getIntParameter("radius");
        if (radius == null || radius <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'radius' (must be > 0)");
        }

        // Extract height
        Integer height = context.getIntParameter("height");
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

        // Generate cylinder outline
        log.info("Generating cylinder outline: pos=({},{},{}), radius={}, height={}, blockType={}",
                x, y, z, radius, height, blockType);

        painter.cylinderOutline(x, y, z, radius, height);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated cylinder outline: %d blocks (radius %d, height %d) at (%d,%d,%d)",
                blockCount, radius, height, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
