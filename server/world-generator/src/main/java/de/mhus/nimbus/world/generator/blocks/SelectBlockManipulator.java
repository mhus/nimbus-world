package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.WEditCache;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Select Block Manipulator.
 * Selects existing blocks in a defined region and adds them to the ModelSelector.
 * Does not create or modify blocks, only selects them for further operations.
 *
 * Usage:
 * <pre>
 * {
 *   "select-block": {
 *     "position": {"x": 100, "y": 64, "z": 100},
 *     "width": 10,
 *     "height": 5,
 *     "depth": 10,
 *     "color": "#ff0000"
 *   }
 * }
 * </pre>
 *
 * If width, height, or depth are not specified, they default to 1.
 * Color is optional and defaults to the ModelSelector default color.
 *
 * Returns a ModelSelector with all blocks found in the specified region.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SelectBlockManipulator implements BlockManipulator {

    private final WEditCacheService editCacheService;

    @Override
    public String getName() {
        return "select-block";
    }

    @Override
    public String getTitle() {
        return "Select Block";
    }

    @Override
    public String getDescription() {
        return "Selects existing blocks in a defined region. " +
                "Parameters: position {x,y,z}, width (default 1), height (default 1), depth (default 1), color (optional). " +
                "Example: {\"select\": {\"transform\": \"position\", \"width\": 5, \"height\": 3, \"depth\": 5}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Extract position parameter
        JsonNode positionNode = context.getJsonParameter("position");
        if (positionNode == null || !positionNode.isObject()) {
            return ManipulatorResult.error("Missing required parameter 'position' {x, y, z}");
        }

        Integer startX = positionNode.has("x") ? positionNode.get("x").asInt() : null;
        Integer startY = positionNode.has("y") ? positionNode.get("y").asInt() : null;
        Integer startZ = positionNode.has("z") ? positionNode.get("z").asInt() : null;

        if (startX == null || startY == null || startZ == null) {
            return ManipulatorResult.error("Invalid position: x, y, z coordinates required");
        }

        // Extract dimensions (default to 1)
        int width = context.getIntParameter("width") != null ? context.getIntParameter("width") : 1;
        int height = context.getIntParameter("height") != null ? context.getIntParameter("height") : 1;
        int depth = context.getIntParameter("depth") != null ? context.getIntParameter("depth") : 1;

        if (width <= 0 || height <= 0 || depth <= 0) {
            return ManipulatorResult.error("Width, height, and depth must be greater than 0");
        }

        // Extract color (optional)
        String color = context.getParameter("color");
        if (color == null || color.isBlank()) {
            color = "#00ff00"; // Default green
        }

        // Validate required context fields
        String worldId = context.getWorldId();
        if (worldId == null || worldId.isBlank()) {
            return ManipulatorResult.error("WorldId required for select-block operation");
        }

        String layerDataId = context.getLayerDataId();
        if (layerDataId == null || layerDataId.isBlank()) {
            return ManipulatorResult.error("LayerDataId required for select-block operation");
        }

        log.info("Selecting blocks in region: pos=({},{},{}), width={}, height={}, depth={}, layer={}",
                startX, startY, startZ, width, height, depth, layerDataId);

        // Load all cached blocks for the layer
        List<WEditCache> cachedBlocks = editCacheService.findByWorldIdAndLayerDataId(worldId, layerDataId);

        // Create a map for quick lookup by coordinates (x,y,z)
        Map<String, WEditCache> blockMap = new HashMap<>();
        for (WEditCache cache : cachedBlocks) {
            LayerBlock layerBlock = cache.getBlock();
            if (layerBlock != null && layerBlock.getBlock() != null) {
                Block block = layerBlock.getBlock();
                if (block.getPosition() != null) {
                    String key = block.getPosition().getX() + "," +
                               block.getPosition().getY() + "," +
                               block.getPosition().getZ();
                    blockMap.put(key, cache);
                }
            }
        }

        log.debug("Loaded {} cached blocks for layer {}", blockMap.size(), layerDataId);

        // Create ModelSelector for selected blocks
        String layerName = context.getLayerName();
        String autoSelectName = layerName != null && !layerName.isBlank()
                ? layerDataId + ":" + layerName
                : layerDataId;

        ModelSelector modelSelector = ModelSelector.builder()
                .defaultColor(color)
                .autoSelectName(autoSelectName)
                .build();

        // Iterate through the defined region and select blocks
        int selectedCount = 0;
        int missingCount = 0;

        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                for (int dz = 0; dz < depth; dz++) {
                    int x = startX + dx;
                    int y = startY + dy;
                    int z = startZ + dz;

                    String key = x + "," + y + "," + z;
                    WEditCache cachedBlock = blockMap.get(key);

                    if (cachedBlock != null) {
                        // Block exists in cache - add to selection
                        modelSelector.addBlock(x, y, z, color);
                        selectedCount++;
                        log.trace("Selected block at ({},{},{})", x, y, z);
                    } else {
                        // Block not found in cache
                        missingCount++;
                        log.trace("No block found at ({},{},{})", x, y, z);
                    }
                }
            }
        }

        // Build result message
        String message;
        if (selectedCount == 0) {
            message = String.format("No blocks found in region (%dx%dx%d) at (%d,%d,%d)",
                    width, height, depth, startX, startY, startZ);
            log.info(message);
            return ManipulatorResult.error(message);
        } else if (missingCount > 0) {
            message = String.format("Selected %d blocks (%d not found) in region (%dx%dx%d) at (%d,%d,%d)",
                    selectedCount, missingCount, width, height, depth, startX, startY, startZ);
            log.info(message);
        } else {
            message = String.format("Selected %d blocks in region (%dx%dx%d) at (%d,%d,%d)",
                    selectedCount, width, height, depth, startX, startY, startZ);
            log.info(message);
        }

        return ManipulatorResult.success(message, modelSelector);
    }
}
