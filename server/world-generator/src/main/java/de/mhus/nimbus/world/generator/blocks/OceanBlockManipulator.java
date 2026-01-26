package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Ocean Block Manipulator - fills an area with ocean blocks at a specific level.
 * Only places blocks where no other blocks exist (uses no-overwrite behavior).
 *
 * Parameters:
 * - position: {x, y, z} - Starting position (required)
 * - width: integer - Width in X direction (required)
 * - depth: integer - Depth in Z direction (required)
 * - level: integer - Y level to place ocean blocks (optional, default: world.waterLevel)
 * - blockType: string - Block type to use (optional, default: "n:o")
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "ocean": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 100,
 *     "depth": 100,
 *     "level": 62,
 *     "blockType": "n:o"
 *   }
 * }
 * </pre>
 *
 * Using world water level:
 * <pre>
 * {
 *   "ocean": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 100,
 *     "depth": 100
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OceanBlockManipulator implements BlockManipulator {

    private final WWorldService worldService;

    @Override
    public String getName() {
        return "ocean";
    }

    @Override
    public String getTitle() {
        return "Ocean Filler";
    }

    @Override
    public String getDescription() {
        return "Fills an area with ocean blocks at a specific level. " +
                "Only places blocks where no other blocks exist. " +
                "Parameters: position {x,y,z}, width, depth, level (optional, default: world.waterLevel), blockType (optional, default: n:o). " +
                "Example: {\"ocean\": {\"transform\": \"position\", \"width\": 100, \"depth\": 100}}";
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

        if (x == null || z == null) {
            return ManipulatorResult.error("Invalid position: x and z coordinates required");
        }

        // Extract dimensions
        Integer width = context.getIntParameter("width");
        Integer depth = context.getIntParameter("depth");

        if (width == null || width <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'width' (must be > 0)");
        }

        if (depth == null || depth <= 0) {
            return ManipulatorResult.error("Missing or invalid parameter 'depth' (must be > 0)");
        }

        // Extract or determine level
        Integer level = context.getIntParameter("level");
        if (level == null) {
            // Use world water level as default
            String worldId = context.getWorldId();
            if (worldId == null || worldId.isBlank()) {
                return ManipulatorResult.error("WorldId required for determining default water level");
            }

            Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
            if (worldOpt.isEmpty()) {
                return ManipulatorResult.error("World not found: " + worldId);
            }

            WWorld world = worldOpt.get();
            level = world.getSeaLevel();

            if (level == null) {
                return ManipulatorResult.error("No level specified and world has no waterLevel configured");
            }

            log.debug("Using world water level: {}", level);
        }

        // Use y from position if provided, otherwise use level
        if (y == null) {
            y = level;
        }

        // Extract blockType (default to ocean block "n:o")
        String blockType = context.getParameter("blockType");
        if (blockType == null || blockType.isBlank()) {
            blockType = "n:o";
            log.debug("Using default ocean blockType: n:o");
        }

        // Parse block definition
        BlockDef blockDef = BlockDef.of(blockType).orElse(null);
        if (blockDef == null) {
            return ManipulatorResult.error("Invalid blockType: " + blockType);
        }

        // Create painter with no-overwrite flavor
        BlockManipulatorService service = context.getService();
        EditCachePainter painter;
        try {
            painter = service.createBlockPainter(context, blockDef);

            // Apply no-overwrite flavor to avoid overwriting existing blocks
            EditCachePainter.BlockPainter basePainter = painter.getPainter();
            EditCachePainter.BlockPainter noOverwritePainter = new EditCachePainter.NoOverwritePainter(basePainter);
            painter.setPainter(noOverwritePainter);

            log.debug("Applied no-overwrite painter for ocean filling");
        } catch (BlockManipulatorException e) {
            return ManipulatorResult.error("Failed to create painter: " + e.getMessage());
        }

        // Fill ocean area
        log.info("Generating ocean: pos=({},{},{}), width={}, depth={}, level={}, blockType={}",
                x, y, z, width, depth, level, blockType);

        painter.rectangleY(x, level, z, width, depth);

        // Get ModelSelector from context
        ModelSelector modelSelector = context.getModelSelector();

        int blockCount = modelSelector.getBlockCount();
        String message = String.format("Generated ocean: %d blocks (%dx%d at level %d) at (%d,%d,%d)",
                blockCount, width, depth, level, x, y, z);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }
}
