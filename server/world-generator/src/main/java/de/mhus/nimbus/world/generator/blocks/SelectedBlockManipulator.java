package de.mhus.nimbus.world.generator.blocks;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.session.BlockRegister;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.util.ModelSelectorUtil;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fill Selected Blocks Manipulator.
 * Fills all selected blocks with a specified block type or the registered block type.
 *
 * Usage with blockType:
 * <pre>
 * {
 *   "selected": {
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 *
 * Usage with registered block:
 * <pre>
 * {
 *   "selected": {
 *     "registered": true
 *   }
 * }
 * </pre>
 *
 * When using "registered": true, the block data is retrieved from BlockRegister (WSessionService).
 * The registered block's type and properties are used, but the position is adjusted for each selected block.
 *
 * Either "blockType" or "registered" must be specified (not both).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SelectedBlockManipulator implements BlockManipulator {

    private final WSessionService wSessionService;
    private final WEditCacheService editCacheService;
    private final WWorldService worldService;

    @Override
    public String getName() {
        return "selected";
    }

    @Override
    public String getTitle() {
        return "Fill Selected Blocks";
    }

    @Override
    public String getDescription() {
        return "Fills all selected blocks with a specified block type or the registered block type. " +
                "Parameters: blockType (string) OR registered (boolean). " +
                "Example: {\"selected\": {\"blockType\": \"n:s\"}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Validate required context fields
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return ManipulatorResult.error("SessionId required for fill-selected operation");
        }

        String worldId = context.getWorldId();
        if (worldId == null || worldId.isBlank()) {
            return ManipulatorResult.error("WorldId required for fill-selected operation");
        }

        String layerDataId = context.getLayerDataId();
        if (layerDataId == null || layerDataId.isBlank()) {
            return ManipulatorResult.error("LayerDataId required for fill-selected operation");
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

        // Determine fill mode: registered or blockType
        Boolean useRegistered = context.getBooleanParameter("registered");
        String blockType = context.getParameter("blockType");

        if ((useRegistered == null || !useRegistered) && (blockType == null || blockType.isBlank())) {
            return ManipulatorResult.error("Either 'registered': true or 'blockType' must be specified");
        }

        if (useRegistered != null && useRegistered && blockType != null && !blockType.isBlank()) {
            return ManipulatorResult.error("Cannot specify both 'registered' and 'blockType'. Choose one.");
        }

        Block templateBlock = null;
        BlockDef blockDef = null;
        String fillSource;

        if (useRegistered != null && useRegistered) {
            // Use registered block from BlockRegister
            Optional<BlockRegister> blockRegisterOpt = wSessionService.getBlockRegister(sessionId);
            if (blockRegisterOpt.isEmpty()) {
                return ManipulatorResult.error("No block registered. Please mark a block first (MARK_BLOCK) or select from palette.");
            }

            BlockRegister blockRegister = blockRegisterOpt.get();
            templateBlock = blockRegister.getBlock();
            if (templateBlock == null) {
                return ManipulatorResult.error("BlockRegister has no block data");
            }

            fillSource = "registered block (type: " + templateBlock.getBlockTypeId() + ")";
            log.info("Filling selected blocks with registered block: blockTypeId={}", templateBlock.getBlockTypeId());

        } else {
            // Use specified blockType
            blockDef = BlockDef.of(blockType).orElse(null);
            if (blockDef == null) {
                return ManipulatorResult.error("Invalid blockType: " + blockType);
            }

            fillSource = "blockType: " + blockType;
            log.info("Filling selected blocks with blockType: {}", blockType);
        }

        // Process each selected block
        int filledCount = 0;
        int errorCount = 0;
        List<String> blocks = modelSelector.getBlocks();

        // Create new ModelSelector for filled blocks
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

                // Create new block for this position
                Block newBlock;
                if (templateBlock != null) {
                    // Clone registered block with new position
                    newBlock = Block.builder()
                            .position(Vector3Int.builder()
                                    .x(x)
                                    .y(y)
                                    .z(z)
                                    .build())
                            .blockTypeId(templateBlock.getBlockTypeId())
                            .offsets(templateBlock.getOffsets())
                            .rotation(templateBlock.getRotation())
                            .faceVisibility(templateBlock.getFaceVisibility())
                            .status(templateBlock.getStatus())
                            .modifiers(templateBlock.getModifiers())
                            .metadata(templateBlock.getMetadata())
                            .level(templateBlock.getLevel())
                            .source(templateBlock.getSource())
                            .build();
                } else {
                    // Create block from BlockDef
                    newBlock = Block.builder()
                            .position(Vector3Int.builder()
                                    .x(x)
                                    .y(y)
                                    .z(z)
                                    .build())
                            .build();
                    blockDef.fillBlock(newBlock);
                }

                // Set block
                editCacheService.doSetAndSendBlock(world, layerDataId, modelName, newBlock, groupId);

                // Add to new ModelSelector
                newModelSelector.addBlock(x, y, z, color);

                filledCount++;
                log.trace("Filled block at ({},{},{})", x, y, z);

            } catch (NumberFormatException e) {
                log.warn("Failed to parse block coordinates from entry: {}", blockEntry, e);
                errorCount++;
            } catch (Exception e) {
                log.error("Failed to fill block from entry: {}", blockEntry, e);
                errorCount++;
            }
        }

        // Update ModelSelector in session with filled blocks
        wSessionService.updateModelSelector(sessionId, ModelSelectorUtil.toStringList(newModelSelector));
        log.debug("Updated ModelSelector for session: {}", sessionId);

        // Build result message
        String message;
        if (errorCount > 0) {
            message = String.format("Filled %d blocks with %s, %d errors occurred",
                    filledCount, fillSource, errorCount);
            log.warn(message);
        } else {
            message = String.format("Successfully filled %d blocks with %s",
                    filledCount, fillSource);
            log.info(message);
        }

        return ManipulatorResult.success(message, newModelSelector);
    }
}
