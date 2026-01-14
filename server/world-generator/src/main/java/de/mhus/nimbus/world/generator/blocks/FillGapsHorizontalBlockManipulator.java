package de.mhus.nimbus.world.generator.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.shared.layer.WEditCache;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fill Gaps Horizontal Block Manipulator - fills horizontal gaps in terrain.
 * Scans an area and fills missing blocks (gaps) where terrain should exist.
 * Intelligently matches neighboring block types or falls back to ground block type.
 *
 * Parameters:
 * - position: {x, y, z} - Starting position (required)
 * - width: integer - Width in X direction (required)
 * - depth: integer - Depth in Z direction (required)
 * - level: integer - Y level to check and fill (optional, default: world.groundLevel)
 * - blockType: string - Block type to use (optional, tries to match neighbors, fallback: world.groundBlockType)
 * - transform: string - Transformations like "position,forward" (optional)
 *
 * Example:
 * <pre>
 * {
 *   "fill-gaps-horizontal": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 100,
 *     "depth": 100,
 *     "level": 64
 *   }
 * }
 * </pre>
 *
 * With explicit blockType:
 * <pre>
 * {
 *   "fill-gaps-horizontal": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 100,
 *     "depth": 100,
 *     "blockType": "n:g"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FillGapsHorizontalBlockManipulator implements BlockManipulator {

    private final WWorldService worldService;
    private final WEditCacheService editCacheService;

    @Override
    public String getName() {
        return "fill-gaps-horizontal";
    }

    @Override
    public String getTitle() {
        return "Fill Horizontal Gaps";
    }

    @Override
    public String getDescription() {
        return "Fills horizontal gaps in terrain within a defined area. " +
                "Checks each column (X,Z) for missing blocks and fills them intelligently. " +
                "Parameters: position {x,y,z}, width, depth, level (optional, default: world.groundLevel), blockType (optional, tries neighbors). " +
                "Example: {\"fill-gaps-horizontal\": {\"transform\": \"position\", \"width\": 100, \"depth\": 100}}";
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

        // Load world
        String worldId = context.getWorldId();
        if (worldId == null || worldId.isBlank()) {
            return ManipulatorResult.error("WorldId required");
        }

        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ManipulatorResult.error("World not found: " + worldId);
        }

        WWorld world = worldOpt.get();

        // Extract or determine level
        Integer level = context.getIntParameter("level");
        if (level == null) {
            level = world.getGroundLevel();
            if (level == null) {
                level = 0; // Ultimate fallback
            }
            log.debug("Using world ground level: {}", level);
        }

        // Use y from position if provided, otherwise use level
        if (y == null) {
            y = level;
        }

        // Extract blockType (optional - will try to match neighbors or use world default)
        String explicitBlockType = context.getParameter("blockType");

        // Get default from world
        String defaultBlockType = world.getGroundBlockType();
        if (defaultBlockType == null || defaultBlockType.isBlank()) {
            defaultBlockType = "n:g"; // Ultimate fallback
        }

        String layerDataId = context.getLayerDataId();
        String modelName = context.getModelName();
        int groupId = context.getGroupId();

        // Initialize ModelSelector
        if (context.getModelSelector() == null) {
            String layerName = context.getLayerName();
            String autoSelectName = layerName != null && !layerName.isBlank()
                    ? layerDataId + ":" + layerName
                    : layerDataId;

            context.setModelSelector(ModelSelector.builder()
                    .defaultColor("#00ff00")
                    .autoSelectName(autoSelectName)
                    .build());
        }

        ModelSelector modelSelector = context.getModelSelector();

        // Scan area and fill gaps
        int filledCount = 0;
        int scannedCount = 0;

        log.info("Scanning for gaps: area=({},{}) to ({},{}), level={}",
                x, z, x + width - 1, z + depth - 1, level);

        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int currentX = x + dx;
                int currentZ = z + dz;
                scannedCount++;

                // Check if block exists at this position
                boolean blockExists = editCacheService.findByCoordinates(
                        worldId,
                        layerDataId,
                        modelName,
                        currentX,
                        level,
                        currentZ
                ).isPresent();

                if (!blockExists) {
                    // Found a gap - determine block type to use
                    String blockTypeToUse = explicitBlockType;

                    if (blockTypeToUse == null || blockTypeToUse.isBlank()) {
                        // Try to match neighboring blocks
                        blockTypeToUse = findNeighborBlockType(
                                worldId, layerDataId, modelName,
                                currentX, level, currentZ
                        );

                        if (blockTypeToUse == null) {
                            // No neighbor found, use world default
                            blockTypeToUse = defaultBlockType;
                        }
                    }

                    // Parse block definition
                    BlockDef blockDef = BlockDef.of(blockTypeToUse).orElse(null);
                    if (blockDef == null) {
                        log.warn("Invalid blockType '{}' at ({},{},{}), skipping", blockTypeToUse, currentX, level, currentZ);
                        continue;
                    }

                    // Create and place block
                    Block block = Block.builder()
                            .position(
                                    de.mhus.nimbus.generated.types.Vector3Int.builder()
                                            .x(currentX)
                                            .y(level)
                                            .z(currentZ)
                                            .build()
                            ).build();

                    blockDef.fillBlock(block);
                    editCacheService.doSetAndSendBlock(world, layerDataId, modelName, block, groupId);

                    // Add to ModelSelector
                    String color = modelSelector.getDefaultColor();
                    if (color == null) {
                        color = "#00ff00";
                    }
                    modelSelector.addBlock(currentX, level, currentZ, color);

                    filledCount++;

                    if (filledCount % 100 == 0) {
                        log.debug("Filled {} gaps so far...", filledCount);
                    }
                }
            }
        }

        String message = String.format("Filled %d gaps in terrain (scanned %d positions, level %d) in area (%d,%d) to (%d,%d)",
                filledCount, scannedCount, level, x, z, x + width - 1, z + depth - 1);

        log.info(message);

        return ManipulatorResult.success(message, modelSelector);
    }

    /**
     * Try to find a neighboring block and return its block type.
     * Checks in order: North, South, East, West
     *
     * @return blockType of neighbor, or null if no neighbor found
     */
    private String findNeighborBlockType(String worldId, String layerDataId, String modelName,
                                          int x, int y, int z) {
        // Check cardinal directions
        int[][] directions = {
                {0, 0, -1},  // North
                {0, 0, 1},   // South
                {1, 0, 0},   // East
                {-1, 0, 0}   // West
        };

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];

            Optional<WEditCache> neighborOpt = editCacheService.findByCoordinates(
                    worldId, layerDataId, modelName, nx, ny, nz
            );

            if (neighborOpt.isPresent()) {
                WEditCache neighbor = neighborOpt.get();
                String blockType = neighbor.getBlock().getBlock().getBlockTypeId();
                if (blockType != null && !blockType.isBlank()) {
                    log.debug("Found neighbor block type '{}' at ({},{},{})", blockType, nx, ny, nz);
                    return blockType;
                }
            }
        }

        return null;
    }
}
