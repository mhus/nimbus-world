package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bridge Block Manipulator - creates a bridge between two points with pillars.
 *
 * Parameters:
 * - position: {x, y, z} - Start position (deck height) (required)
 * - endPosition: {x, y, z} - End position (deck height) (required)
 * - width: integer - Width of bridge (required)
 * - pillarSpacing: integer - Spacing between pillars (0 = no pillars) (default: 0)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "bridge": {
 *     "position": {"x": 100, "y": 70, "z": 100},
 *     "endPosition": {"x": 150, "y": 70, "z": 150},
 *     "width": 5,
 *     "pillarSpacing": 10,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class BridgeBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "bridge";
    }

    @Override
    public String getTitle() {
        return "Bridge Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a bridge between two points with optional pillars. " +
                "Parameters: position {x,y,z}, endPosition {x,y,z}, width, pillarSpacing (optional), blockType (optional). " +
                "Example: {\"bridge\": {\"transform\": \"position\", \"endPosition\": {\"x\": 150, \"y\": 70, \"z\": 150}, \"width\": 5, \"pillarSpacing\": 10}}";
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

        // Extract width
        Integer width = context.getIntParameter("width");
        if (width == null || width <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'width' (must be > 0)");
        }

        // Extract pillarSpacing (optional, default 0 = no pillars)
        Integer pillarSpacing = context.getIntParameter("pillarSpacing");
        if (pillarSpacing == null) {
            pillarSpacing = 0;
        }
        if (pillarSpacing < 0) {
            return ManipulatorResult.error("Invalid parameter 'pillarSpacing' (must be >= 0)");
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

        // Generate bridge
        log.info("Generating bridge: start=({},{},{}), end=({},{},{}), width={}, pillarSpacing={}, blockType={}",
                x1, y1, z1, x2, y2, z2, width, pillarSpacing, blockType);

        painter.bridge(x1, y1, z1, x2, y2, z2, width, pillarSpacing);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated bridge: %d blocks (width %d, pillar spacing %d) from (%d,%d,%d) to (%d,%d,%d)",
                blockCount, width, pillarSpacing, x1, y1, z1, x2, y2, z2);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
