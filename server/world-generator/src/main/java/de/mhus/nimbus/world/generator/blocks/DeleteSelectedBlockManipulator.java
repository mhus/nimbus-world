package de.mhus.nimbus.world.generator.blocks;

import de.mhus.nimbus.world.shared.layer.WEditCacheService;
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
 * Delete Selected Blocks Manipulator.
 * Deletes all blocks that are currently selected in the ModelSelector.
 *
 * Usage:
 * <pre>
 * {
 *   "delete-selected": {}
 * }
 * </pre>
 *
 * Reads the ModelSelector from WSession and deletes all selected blocks
 * using WEditCacheService.doDeleteAndSendBlock().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteSelectedBlockManipulator implements BlockManipulator {

    private final WSessionService wSessionService;
    private final WEditCacheService editCacheService;
    private final WWorldService worldService;

    @Override
    public String getName() {
        return "delete-selected";
    }

    @Override
    public String getTitle() {
        return "Delete Selected Blocks";
    }

    @Override
    public String getDescription() {
        return "Deletes all blocks that are currently selected in the ModelSelector. No parameters required. " +
                "Example: {\"delete-selected\": {}}";
    }

    @Override
    public ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException {
        // Validate required context fields
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return ManipulatorResult.error("SessionId required for delete-selected operation");
        }

        String worldId = context.getWorldId();
        if (worldId == null || worldId.isBlank()) {
            return ManipulatorResult.error("WorldId required for delete-selected operation");
        }

        String layerDataId = context.getLayerDataId();
        if (layerDataId == null || layerDataId.isBlank()) {
            return ManipulatorResult.error("LayerDataId required for delete-selected operation");
        }

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

        log.info("Deleting {} selected blocks from layer {}", modelSelector.getBlockCount(), layerDataId);

        // Delete each block
        int deletedCount = 0;
        int errorCount = 0;
        List<String> blocks = modelSelector.getBlocks();

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

                // Delete block using WEditCacheService
                editCacheService.doDeleteAndSendBlock(world, layerDataId, x, y, z);
                deletedCount++;

                log.debug("Deleted block at ({},{},{})", x, y, z);

            } catch (NumberFormatException e) {
                log.warn("Failed to parse block coordinates from entry: {}", blockEntry, e);
                errorCount++;
            } catch (Exception e) {
                log.error("Failed to delete block from entry: {}", blockEntry, e);
                errorCount++;
            }
        }

        // Clear ModelSelector after deletion
        wSessionService.updateModelSelector(sessionId, null);
        log.debug("Cleared ModelSelector for session: {}", sessionId);

        // Build result message
        String message;
        if (errorCount > 0) {
            message = String.format("Deleted %d blocks, %d errors occurred", deletedCount, errorCount);
            log.warn(message);
        } else {
            message = String.format("Successfully deleted %d blocks", deletedCount);
            log.info(message);
        }

        return ManipulatorResult.success(message);
    }
}
