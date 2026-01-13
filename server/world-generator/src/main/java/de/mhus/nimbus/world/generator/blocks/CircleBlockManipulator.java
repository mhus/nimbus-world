package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Circle Block Manipulator - creates a filled circle on a specified plane.
 *
 * Parameters:
 * - position: {x, y, z} - Center position (required)
 * - radius: integer - Circle radius (required)
 * - plane: string - Plane orientation: "Y" (horizontal), "X", "Z" (default: "Y")
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 * - painter: string - Painter type: "default", "raster", "grid-2", "grid-5" (optional)
 *
 * Plane guide:
 * - "Y": Horizontal circle (default) - extends in X-Z plane
 * - "X": Vertical circle - extends in Y-Z plane
 * - "Z": Vertical circle - extends in X-Y plane
 *
 * Example:
 * <pre>
 * {
 *   "circle": {
 *     "radius": 10,
 *     "blockType": "n:s",
 *     "position": {"x": 100, "y": 64, "z": 100}
 *   }
 * }
 * </pre>
 *
 * With plane specification:
 * <pre>
 * {
 *   "circle": {
 *     "transform": "position",
 *     "radius": 8,
 *     "plane": "X",
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class CircleBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "circle";
    }

    @Override
    public String getTitle() {
        return "Circle Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a filled circle on the specified plane. " +
                "Parameters: position {x,y,z}, radius, plane (Y/X/Z, default Y), blockType (optional), painter (optional). " +
                "Example: {\"circle\": {\"transform\": \"position\", \"radius\": 5, \"plane\": \"Y\"}}";
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

        // Extract plane (default to Y)
        String plane = context.getParameter("plane");
        if (plane == null || plane.isBlank()) {
            plane = "Y";
        }
        plane = plane.toUpperCase();

        if (!plane.equals("Y") && !plane.equals("X") && !plane.equals("Z")) {
            return ManipulatorResult.error("Invalid plane '" + plane + "'. Must be Y, X, or Z");
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

        // Create painter (painter type is handled by createBlockPainter via "painter" parameter)
        BlockManipulatorService service = context.getService();
        EditCachePainter painter;
        try {
            painter = service.createBlockPainter(context, blockDef);
        } catch (BlockManipulatorException e) {
            return ManipulatorResult.error("Failed to create painter: " + e.getMessage());
        }

        // Generate circle on specified plane
        log.info("Generating circle: pos=({},{},{}), radius={}, plane={}, blockType={}",
                x, y, z, radius, plane, blockType);

        switch (plane) {
            case "Y":
                painter.circleY(x, y, z, radius);
                break;
            case "X":
                painter.circleX(x, y, z, radius);
                break;
            case "Z":
                painter.circleZ(x, y, z, radius);
                break;
        }

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated circle: %d blocks (radius %d, plane %s) at (%d,%d,%d)",
                blockCount, radius, plane, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
