package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spiral Block Manipulator - creates a spiral staircase around a center point.
 *
 * Parameters:
 * - position: {x, y, z} - Center position (required)
 * - radius: integer - Radius of spiral (required)
 * - height: integer - Total height (required)
 * - rotations: double - Number of complete rotations (default: 2.0)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "spiral": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "radius": 5,
 *     "height": 20,
 *     "rotations": 2.5,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class SpiralBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "spiral";
    }

    @Override
    public String getTitle() {
        return "Spiral Staircase Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a spiral staircase around a center point. " +
                "Parameters: position {x,y,z}, radius, height, rotations (optional), blockType (optional). " +
                "Example: {\"spiral\": {\"transform\": \"position\", \"radius\": 5, \"height\": 20, \"rotations\": 2.5}}";
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
        Integer height = context.getIntParameter("height");

        if (radius == null || radius <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'radius' (must be > 0)");
        }

        if (height == null || height <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'height' (must be > 0)");
        }

        // Extract rotations (optional, default 2.0)
        Double rotations = context.getDoubleParameter("rotations");
        if (rotations == null) {
            rotations = 2.0;
        }
        if (rotations <= 0) {
            return ManipulatorResult.error("Invalid parameter 'rotations' (must be > 0)");
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

        // Generate spiral
        log.info("Generating spiral: pos=({},{},{}), radius={}, height={}, rotations={}, blockType={}",
                x, y, z, radius, height, rotations, blockType);

        painter.spiral(x, y, z, radius, height, rotations);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated spiral: %d blocks (radius %d, height %d, %.1f rotations) at (%d,%d,%d)",
                blockCount, radius, height, rotations, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
