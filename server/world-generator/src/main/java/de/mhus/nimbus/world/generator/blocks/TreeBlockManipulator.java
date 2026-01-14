package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tree Block Manipulator - creates a tree with trunk and crown.
 *
 * Parameters:
 * - position: {x, y, z} - Base position of trunk (required)
 * - trunkHeight: integer - Height of trunk (required)
 * - crownRadius: integer - Radius of crown (required)
 * - blockType: string - Block type to use (default from defaults)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "tree": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "trunkHeight": 8,
 *     "crownRadius": 4,
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class TreeBlockManipulator implements BlockManipulator {

    @Override
    public String getName() {
        return "tree";
    }

    @Override
    public String getTitle() {
        return "Tree Generator";
    }

    @Override
    public String getDescription() {
        return "Creates a tree with trunk and spherical crown. " +
                "Parameters: position {x,y,z}, trunkHeight, crownRadius, blockType (optional). " +
                "Example: {\"tree\": {\"transform\": \"position\", \"trunkHeight\": 8, \"crownRadius\": 4}}";
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
        Integer trunkHeight = context.getIntParameter("trunkHeight");
        Integer crownRadius = context.getIntParameter("crownRadius");

        if (trunkHeight == null || trunkHeight <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'trunkHeight' (must be > 0)");
        }

        if (crownRadius == null || crownRadius <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'crownRadius' (must be > 0)");
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

        // Generate tree
        log.info("Generating tree: pos=({},{},{}), trunkHeight={}, crownRadius={}, blockType={}",
                x, y, z, trunkHeight, crownRadius, blockType);

        painter.tree(x, y, z, trunkHeight, crownRadius);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated tree: %d blocks (trunk height %d, crown radius %d) at (%d,%d,%d)",
                blockCount, trunkHeight, crownRadius, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
