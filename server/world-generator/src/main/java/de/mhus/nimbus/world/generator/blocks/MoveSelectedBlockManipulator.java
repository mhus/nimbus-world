package de.mhus.nimbus.world.generator.blocks;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.WEditCache;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.util.ModelSelectorUtil;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Move Selected Blocks Manipulator.
 * Moves all blocks that are currently selected in the ModelSelector by the specified offset.
 *
 * Usage:
 * <pre>
 * {
 *   "move-selected": {
 *     "x": 5,
 *     "y": 0,
 *     "z": -3
 *   }
 * }
 * </pre>
 *
 * If a coordinate is not specified, it defaults to 0 (no movement in that direction).
 *
 * Reads all selected blocks from WEditCache, deletes them at their current position,
 * and creates them at the new position with the same block data.
 * Returns a new ModelSelector with the updated positions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MoveSelectedBlockManipulator implements BlockManipulator {

    private final WSessionService wSessionService;
    private final WEditCacheService editCacheService;
    private final WWorldService worldService;

    @Override
    public String getName() {
        return "move-selected";
    }

    @Override
    public String getTitle() {
        return "Move Selected Blocks";
    }

    @Override
    public String getDescription() {
        return "Moves all blocks that are currently selected in the ModelSelector by the specified offset. " +
                "Parameters: x (default 0), y (default 0), z (default 0). " +
                "Example: {\"move-selected\": {\"x\": 5, \"y\": 0, \"z\": -3}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Validate required context fields
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return ManipulatorResult.error("SessionId required for move-selected operation");
        }

        String worldId = context.getWorldId();
        if (worldId == null || worldId.isBlank()) {
            return ManipulatorResult.error("WorldId required for move-selected operation");
        }

        String layerDataId = context.getLayerDataId();
        if (layerDataId == null || layerDataId.isBlank()) {
            return ManipulatorResult.error("LayerDataId required for move-selected operation");
        }

        String modelName = context.getModelName();
        String groupId = context.getGroupId();

        // Load WWorld
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ManipulatorResult.error("World not found: " + worldId);
        }
        WWorld world = worldOpt.get();

        // Load WSession to get ModelSelector
        var sessionOpt = wSessionService.get(sessionId);
        if (sessionOpt.isEmpty()) {
            return ManipulatorResult.error("Session not found: " + sessionId);
        }

        List<String> modelSelectorData = sessionOpt.get().getModelSelector();
        if (modelSelectorData == null || modelSelectorData.isEmpty()) {
            return ManipulatorResult.error("No blocks selected. Please select blocks first using the ModelSelector.");
        }

        // Parse ModelSelector
        ModelSelector modelSelector = ModelSelectorUtil.fromStringList(modelSelectorData);
        if (modelSelector == null || modelSelector.getBlockCount() == 0) {
            return ManipulatorResult.error("No blocks selected. ModelSelector is empty.");
        }

        // Get movement offsets (default to 0 if not specified)
        int dx = context.getIntParameter("x") != null ? context.getIntParameter("x") : 0;
        int dy = context.getIntParameter("y") != null ? context.getIntParameter("y") : 0;
        int dz = context.getIntParameter("z") != null ? context.getIntParameter("z") : 0;

        if (dx == 0 && dy == 0 && dz == 0) {
            return ManipulatorResult.error("No movement specified. At least one of x, y, or z must be non-zero.");
        }

        log.info("Moving {} selected blocks by offset ({},{},{}) in layer {}",
                modelSelector.getBlockCount(), dx, dy, dz, layerDataId);

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

        // Process each selected block
        int movedCount = 0;
        int errorCount = 0;
        List<String> blocks = modelSelector.getBlocks();

        // Create new ModelSelector for the moved blocks
        String layerName = context.getLayerName();
        String autoSelectName = layerName != null && !layerName.isBlank()
                ? layerDataId + ":" + layerName
                : layerDataId;

        ModelSelector newModelSelector = ModelSelector.builder()
                .defaultColor(modelSelector.getDefaultColor())
                .autoSelectName(autoSelectName)
                .build();

        for (String blockEntry : blocks) {
            try {
                // Parse block entry: "x,y,z,#color"
                String[] parts = blockEntry.split(",");
                if (parts.length < 3) {
                    log.warn("Invalid block entry format: {}", blockEntry);
                    errorCount++;
                    continue;
                }

                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                String color = parts.length > 3 ? parts[3].trim() : modelSelector.getDefaultColor();

                // Find the cached block
                String key = x + "," + y + "," + z;
                WEditCache cachedBlock = blockMap.get(key);

                if (cachedBlock == null) {
                    log.warn("Block not found in cache at ({},{},{}), skipping", x, y, z);
                    errorCount++;
                    continue;
                }

                // Calculate new position
                int newX = x + dx;
                int newY = y + dy;
                int newZ = z + dz;

                // Get the original block data
                LayerBlock layerBlock = cachedBlock.getBlock();
                Block originalBlock = layerBlock.getBlock();

                // Create a new block with updated position
                Block newBlock = Block.builder()
                        .position(Vector3Int.builder()
                                .x(newX)
                                .y(newY)
                                .z(newZ)
                                .build())
                        .blockTypeId(originalBlock.getBlockTypeId())
                        .offsets(originalBlock.getOffsets())
                        .rotation(originalBlock.getRotation())
                        .faceVisibility(originalBlock.getFaceVisibility())
                        .status(originalBlock.getStatus())
                        .modifiers(originalBlock.getModifiers())
                        .metadata(originalBlock.getMetadata())
                        .level(originalBlock.getLevel())
                        .source(originalBlock.getSource())
                        .build();

                // Delete old block
                editCacheService.doDeleteAndSendBlock(world, layerDataId, x, y, z);

                // Set new block at new position
                editCacheService.doSetAndSendBlock(world, layerDataId, modelName, newBlock, groupId);

                // Add to new ModelSelector
                newModelSelector.addBlock(newX, newY, newZ, color);

                movedCount++;
                log.debug("Moved block from ({},{},{}) to ({},{},{})",
                        x, y, z, newX, newY, newZ);

            } catch (NumberFormatException e) {
                log.warn("Failed to parse block coordinates from entry: {}", blockEntry, e);
                errorCount++;
            } catch (Exception e) {
                log.error("Failed to move block from entry: {}", blockEntry, e);
                errorCount++;
            }
        }

        // Update ModelSelector in session with new positions
        wSessionService.updateModelSelector(sessionId, ModelSelectorUtil.toStringList(newModelSelector));
        log.debug("Updated ModelSelector for session: {}", sessionId);

        // Build result message
        String message;
        if (errorCount > 0) {
            message = String.format("Moved %d blocks by (%d,%d,%d), %d errors occurred",
                    movedCount, dx, dy, dz, errorCount);
            log.warn(message);
        } else {
            message = String.format("Successfully moved %d blocks by (%d,%d,%d)",
                    movedCount, dx, dy, dz);
            log.info(message);
        }

        return ManipulatorResult.success(message, newModelSelector);
    }
}
