package de.mhus.nimbus.world.control.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.EditAction;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.edit.BlockUpdateService;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.redis.WorldRedisService;
import de.mhus.nimbus.world.shared.session.BlockRegister;
import de.mhus.nimbus.world.shared.session.EditState;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.util.ModelSelectorUtil;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Edit state management service.
 * Stores edit configuration in Redis for cross-pod sharing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EditService {

    private static final Block AIR_BLOCK = Block.builder()
            .blockTypeId("0")
            .position(Vector3Int.builder().x(0).y(0).z(0).build())
            .build();
    private final WorldRedisService redisService;
    private final WLayerService layerService;
    private final WorldClientService worldClient;
    private final WSessionService wSessionService;
    private final BlockInfoService blockInfoService;
    private final WWorldService worldService;
    private final ObjectMapper objectMapper;
    private final BlockUpdateService blockUpdateService;
    private final de.mhus.nimbus.world.shared.layer.WEditCacheService editCacheService;
    private final de.mhus.nimbus.world.shared.layer.WEditCacheDirtyService editCacheDirtyService;

    private static final Duration EDIT_STATE_TTL = Duration.ofHours(24);
    private static final String EDIT_STATE_PREFIX = "edit:";
    private static final Duration OVERLAY_TTL = Duration.ofHours(24);

    /**
     * Get edit state for session.
     * Returns default state if not found in Redis.
     */
    @Transactional(readOnly = true)
    public EditState getEditState(String worldId, String sessionId) {
        return wSessionService.getEditState(sessionId).orElseGet(() ->
                EditState.builder()
                        .worldId(worldId)
                        .editMode(false)
                        .editAction(EditAction.OPEN_CONFIG_DIALOG)
                        .selectedGroup(0)
                        .lastUpdated(Instant.now())
                        .build()
        );
    }

    /**
     * Update edit state using Consumer pattern.
     * If layer changes and edit mode is active, updates model display accordingly.
     */
    @Transactional
    public EditState updateEditState(String worldId, String sessionId, Consumer<EditState> updater) {
        EditState oldState = getEditState(worldId, sessionId);
        String oldLayer = oldState.getSelectedLayer();

        EditState state = getEditState(worldId, sessionId);
        state.setWorldId(worldId);
        updater.accept(state);
        state.setLastUpdated(Instant.now());

        // Enrich layerDataId and modelName when layer is selected
        enrichLayerInfo(worldId, state);

        // Save using WSessionService
        wSessionService.updateEditState(sessionId, state);

        // Update model display if edit mode is active and layer changed
        if (state.isEditMode() && !java.util.Objects.equals(oldLayer, state.getSelectedLayer())) {
            updateModelDisplay(worldId, sessionId, state);
        }

        return state;
    }

    /**
     * Enrich EditState with layerDataId and modelName from WLayer/WLayerModel.
     */
    private void enrichLayerInfo(String worldId, EditState state) {
        if (state.getSelectedLayer() == null || state.getSelectedLayer().isBlank()) {
            state.setLayerDataId(null);
            state.setModelName(null);
            return;
        }

        Optional<WLayer> layerOpt = layerService.findLayer(worldId, state.getSelectedLayer());
        if (layerOpt.isPresent()) {
            WLayer layer = layerOpt.get();
            state.setLayerDataId(layer.getLayerDataId());

            // If MODEL layer and modelId is set, get modelName
            if (layer.getLayerType() == LayerType.MODEL && state.getSelectedModelId() != null) {
                // Get modelName from WLayerModel
                // For now, just use selectedModelId as modelName
                // TODO: Load from WLayerModelService when available
                state.setModelName(state.getSelectedModelId());
            } else {
                state.setModelName(null);
            }

            log.debug("Enriched EditState: layerDataId={}, modelName={}",
                    state.getLayerDataId(), state.getModelName());
        }
    }

    /**
     * Enable/disable edit mode.
     * When enabled and a MODEL layer is selected, displays the model blocks in the client.
     */
    @Transactional
    public void setEditMode(String worldId, String sessionId, boolean enabled) {
        EditState state = getEditState(worldId, sessionId);
        state.setEditMode(enabled);

        // Enrich layer info
        enrichLayerInfo(worldId, state);

        // Save using WSessionService
        wSessionService.updateEditState(sessionId, state);

        log.debug("Edit mode {}: session={}", enabled ? "enabled" : "disabled", sessionId);

        if (enabled) {
            // Get current edit state to check selected layer
            if (state.getSelectedLayer() != null) {
                Optional<WLayer> layerOpt = layerService.findLayer(worldId, state.getSelectedLayer());
                if (layerOpt.isPresent() && layerOpt.get().getLayerType() == LayerType.MODEL) {
                    // Display model blocks in client
                    displayModelInClient(worldId, sessionId, layerOpt.get(), state.getSelectedGroup());
                }
            }
        } else {
            // Clear model display when edit mode is disabled
            clearModelDisplay(worldId, sessionId);
        }
    }

    /**
     * Execute edit action at block position.
     * Reads current EditAction from state and performs corresponding operation.
     */
    @Transactional
    public void doAction(String worldId, String sessionId, int x, int y, int z, String command, List<String> args) {
        // Get current edit state
        EditAction action = toAction(command);
        if (action == null) {
            EditState state = getEditState(worldId, sessionId);
            action = state.getEditAction();
        }
        // Get playerUrl from WSession (not from EditState)
        Optional<WSession> wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot perform edit action", sessionId);
            return;
        }

        String playerUrl = wSession.get().getPlayerUrl();

        if (playerUrl == null) {
            log.warn("No player URL available for session {}, cannot perform edit action", sessionId);
            return;
        }

        if (action == null) {
            action = EditAction.OPEN_CONFIG_DIALOG; // Default
        }

        log.debug("Executing edit action: session={} action={} pos=({},{},{})",
                sessionId, action, x, y, z);

        switch (action) {
            case OPEN_CONFIG_DIALOG:
                // Open config dialog at client
                clientOpenConfigDialogAtClient(worldId, sessionId, playerUrl);
                break;
            case OPEN_EDITOR:
                // Open block editor dialog at client
                setSelectedBlock(worldId, sessionId, x, y, z);
                clientOpenBlockEditorDialogAtClient(worldId, sessionId, playerUrl, x, y, z);
                break;

            case MARK_BLOCK:
                // get block definition at position
                Map<String, Object> blockInfo = blockInfoService.loadBlockInfo(worldId, sessionId, x, y, z);
                // Store marked block and show in client
                doMarkBlock(worldId, sessionId, x, y, z);
                // store also blockInfo data in redis to use in copy
                storeBlockDataRegistry(worldId, sessionId, blockInfo);
                log.info("Block marked: session={} pos=({},{},{})", sessionId, x, y, z);
                break;

            case EditAction.PASTE_BLOCK:
                // Paste marked block to current position
                pasteMarkedBlock(worldId, sessionId, x, y, z);
                break;

            case DELETE_BLOCK:
                // Delete block at position
                deleteBlock(worldId, sessionId, x, y, z);
                break;

            case SMOOTH_BLOCKS:
                smoothBlocks(worldId, sessionId, x, y, z);
                break;
            case ROUGH_BLOCKS:
                roughBlocks(worldId, sessionId, x, y, z);
                break;
            case CLONE_BLOCK:
                // Clone block from layer to current position
                cloneBlock(worldId, sessionId, x, y, z);
                break;
            default:
                log.warn("Unknown edit action: {}", action);
                setSelectedBlock(worldId, sessionId, x, y, z);
                break;
        }
    }

    private EditAction toAction(String command) {
        if (command == null) return null;
        try {
            return EditAction.valueOf(command.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("Invalid edit action command: {}", command);
        }
        return null;
    }

    private void roughBlocks(String worldId, String sessionId, int x, int y, int z) {
        EditState editState = getEditState(worldId, sessionId);

        RoughBlockOperation.builder()
                .editService(this)
                .editState(editState)
                .sessionId(sessionId)
                .centerX(x)
                .centerY(y)
                .centerZ(z)
                .build()
                .execute();
    }

    private void smoothBlocks(String worldId, String sessionId, int x, int y, int z) {
        EditState editState = getEditState(worldId, sessionId);

        SmoothBlockOperation.builder()
                .editService(this)
                .editState(editState)
                .sessionId(sessionId)
                .centerX(x)
                .centerY(y)
                .centerZ(z)
                .build()
                .execute();
    }

    private void clientSetSelectedEditBlock(String worldId, String sessionId, String origin, int x, int y, int z) {
        CommandContext ctx = CommandContext.builder()
                .worldId(worldId)
                .sessionId(sessionId)
                .originServer("world-control")
                .build();
        worldClient.sendPlayerCommand(
                worldId,
                sessionId,
                origin,
                "client",
                List.of("setSelectedEditBlock", String.valueOf(x),String.valueOf(y),String.valueOf(z) ),
                ctx);
    }

    private void clientOpenConfigDialogAtClient(String worldId, String sessionId, String origin) {
        CommandContext ctx = CommandContext.builder()
                .worldId(worldId)
                .sessionId(sessionId)
                .originServer("world-control")
                .build();
        worldClient.sendPlayerCommand(
                worldId,
                sessionId,
                origin,
                "client",
                List.of("openComponent", "edit_config" ),
                ctx);
    }

    private void clientOpenBlockEditorDialogAtClient(String worldId, String sessionId, String origin, int x, int y, int z) {
        CommandContext ctx = CommandContext.builder()
                .worldId(worldId)
                .sessionId(sessionId)
                .originServer("world-control")
                .build();
        worldClient.sendPlayerCommand(
                worldId,
                sessionId,
                origin,
                "client",
                List.of("openComponent", "block_editor", String.valueOf(x),String.valueOf(y),String.valueOf(z) ),
                ctx);
    }

    /**
     * Update selected block coordinates.
     */
    @Transactional
    public void setSelectedBlock(String worldId, String sessionId, int x, int y, int z) {
        String key = editStateKey(sessionId);
        redisService.putValue(worldId, key + "selectedBlockX", String.valueOf(x), EDIT_STATE_TTL);
        redisService.putValue(worldId, key + "selectedBlockY", String.valueOf(y), EDIT_STATE_TTL);
        redisService.putValue(worldId, key + "selectedBlockZ", String.valueOf(z), EDIT_STATE_TTL);
        log.debug("Selected block updated: session={} pos=({},{},{})", sessionId, x, y, z);

    }

    /**
     * Store marked block coordinates for copy/move operations.
     */
    @Transactional
    public void setMarkedBlock(String worldId, String sessionId, int x, int y, int z) {
        String key = editStateKey(sessionId);
        redisService.putValue(worldId, key + "markedBlockX", String.valueOf(x), EDIT_STATE_TTL);
        redisService.putValue(worldId, key + "markedBlockY", String.valueOf(y), EDIT_STATE_TTL);
        redisService.putValue(worldId, key + "markedBlockZ", String.valueOf(z), EDIT_STATE_TTL);
        log.debug("Marked block stored: session={} pos=({},{},{})", sessionId, x, y, z);
    }

    /**
     * Store marked block info (complete block data with metadata) in Redis.
     * Used for copy/move operations.
     */
    private void storeBlockDataRegistry(String worldId, String sessionId, Map<String, Object> blockInfo) {
        if (blockInfo == null) {
            wSessionService.deleteBlockRegister(sessionId);
            log.debug("Register block info cleared: session={}", sessionId);
            return;
        }

        try {
            // Convert Map to BlockRegister
            Block block = objectMapper.convertValue(blockInfo.get("block"), Block.class);

            BlockRegister blockRegister = BlockRegister.builder()
                    .block(block)
                    .layer((String) blockInfo.get("layer"))
                    .group((Integer) blockInfo.get("group"))
                    .groupName((String) blockInfo.get("groupName"))
                    .readOnly((Boolean) blockInfo.get("readOnly"))
                    .build();

            wSessionService.updateBlockRegister(sessionId, blockRegister);
            log.debug("Register block info stored: session={}", sessionId);
        } catch (Exception e) {
            log.error("Failed to store register block info: session={}", sessionId, e);
        }
    }


    /**
     * Get selected block coordinates (if set).
     */
    @Transactional(readOnly = true)
    public Optional<BlockPosition> getSelectedBlock(String worldId, String sessionId) {
        String key = editStateKey(sessionId);
        String xStr = redisService.getValue(worldId, key + "selectedBlockX").orElse(null);
        String yStr = redisService.getValue(worldId, key + "selectedBlockY").orElse(null);
        String zStr = redisService.getValue(worldId, key + "selectedBlockZ").orElse(null);

        if (xStr == null || yStr == null || zStr == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BlockPosition(
                    Integer.parseInt(xStr),
                    Integer.parseInt(yStr),
                    Integer.parseInt(zStr)
            ));
        } catch (NumberFormatException e) {
            log.warn("Invalid block position in Redis: session={}", sessionId);
            return Optional.empty();
        }
    }

    /**
     * Delete edit state (on session close).
     */
    @Transactional
    public void deleteEditState(String worldId, String sessionId) {
        String key = editStateKey(sessionId);

        // Delete all related keys
        redisService.deleteValue(worldId, key + "editMode");
        redisService.deleteValue(worldId, key + "editAction");
        redisService.deleteValue(worldId, key + "selectedLayer");
        redisService.deleteValue(worldId, key + "mountX");
        redisService.deleteValue(worldId, key + "mountY");
        redisService.deleteValue(worldId, key + "mountZ");
        redisService.deleteValue(worldId, key + "selectedGroup");
        redisService.deleteValue(worldId, key + "selectedBlockX");
        redisService.deleteValue(worldId, key + "selectedBlockY");
        redisService.deleteValue(worldId, key + "selectedBlockZ");
        redisService.deleteValue(worldId, key + "markedBlockX");
        redisService.deleteValue(worldId, key + "markedBlockY");
        redisService.deleteValue(worldId, key + "markedBlockZ");
        redisService.deleteValue(worldId, key + "playerIp");
        redisService.deleteValue(worldId, key + "playerPort");

        log.debug("Edit state deleted: session={}", sessionId);
    }

    /**
     * Apply changes for the currently selected layer.
     * Commits all cached edits to the layer by creating a WEditCacheDirty entry.
     * The actual merge happens asynchronously via WEditCacheDirtyService scheduler.
     *
     * @param worldId World identifier
     * @param sessionId Session identifier
     * @throws IllegalStateException if no layer is selected or layer not found
     */
    public void applyChanges(String worldId, String sessionId) {
        // Get current edit state
        EditState state = getEditState(worldId, sessionId);
        if (state.getSelectedLayer() == null) {
            throw new IllegalStateException("No layer selected");
        }

        // Get layer to retrieve layerDataId
        Optional<WLayer> layerOpt = layerService.findLayer(worldId, state.getSelectedLayer());
        if (layerOpt.isEmpty()) {
            throw new IllegalStateException("Layer not found: " + state.getSelectedLayer());
        }

        WLayer layer = layerOpt.get();
        String layerDataId = layer.getLayerDataId();

        // Trigger apply changes via WEditCacheDirtyService
        editCacheDirtyService.applyChanges(worldId, layerDataId);

        log.info("Apply changes triggered: worldId={}, layerDataId={}, layer={}",
                worldId, layerDataId, state.getSelectedLayer());
    }

    /**
     * Discard all changes for the currently selected layer.
     * Deletes all cached edits and marks affected chunks dirty for refresh.
     *
     * @param worldId World identifier
     * @param sessionId Session identifier
     * @return Number of discarded blocks
     * @throws IllegalStateException if no layer is selected or layer not found
     */
    public long discardChanges(String worldId, String sessionId) {
        // Get current edit state
        EditState state = getEditState(worldId, sessionId);
        if (state.getSelectedLayer() == null) {
            throw new IllegalStateException("No layer selected");
        }

        // Get layer to retrieve layerDataId
        Optional<WLayer> layerOpt = layerService.findLayer(worldId, state.getSelectedLayer());
        if (layerOpt.isEmpty()) {
            throw new IllegalStateException("Layer not found: " + state.getSelectedLayer());
        }

        WLayer layer = layerOpt.get();
        String layerDataId = layer.getLayerDataId();

        // Discard changes via WEditCacheDirtyService
        long deletedCount = editCacheDirtyService.discardChanges(worldId, layerDataId);

        log.info("Discard changes completed: worldId={}, layerDataId={}, layer={}, deleted={}",
                worldId, layerDataId, state.getSelectedLayer(), deletedCount);

        return deletedCount;
    }

    /**
     * Get edit cache statistics for a world.
     * Returns grouped statistics per layer with block count and timestamps.
     *
     * @param worldId World identifier
     * @return List of edit cache statistics per layer
     */
    public List<Map<String, Object>> getEditCacheStatistics(String worldId) {
        // Get all layers for this world
        List<WLayer> layers = layerService.findByWorldId(worldId);

        // Build statistics per layer
        List<Map<String, Object>> statistics = new ArrayList<>();

        for (WLayer layer : layers) {
            String layerDataId = layer.getLayerDataId();

            // Get cached blocks for this layer
            List<de.mhus.nimbus.world.shared.layer.WEditCache> caches =
                editCacheService.findByWorldIdAndLayerDataId(worldId, layerDataId);

            // Skip layers with no cache entries
            if (caches.isEmpty()) {
                continue;
            }

            // Calculate timestamps
            Instant firstDate = caches.stream()
                .map(de.mhus.nimbus.world.shared.layer.WEditCache::getCreatedAt)
                .filter(d -> d != null)
                .min(Instant::compareTo)
                .orElse(null);

            Instant lastDate = caches.stream()
                .map(de.mhus.nimbus.world.shared.layer.WEditCache::getModifiedAt)
                .filter(d -> d != null)
                .max(Instant::compareTo)
                .orElse(null);

            Map<String, Object> stat = new HashMap<>();
            stat.put("layerDataId", layerDataId);
            stat.put("layerName", layer.getName());
            stat.put("blockCount", caches.size());
            stat.put("firstDate", firstDate);
            stat.put("lastDate", lastDate);

            statistics.add(stat);
        }

        // Sort by layer title
        statistics.sort((a, b) -> {
            String nameA = (String) a.get("layerName");
            String nameB = (String) b.get("layerName");
            return nameA.compareTo(nameB);
        });

        return statistics;
    }

    /**
     * Paste marked block to target position.
     * Uses stored block data from Redis to recreate the block at the new position.
     * Only the block content is used, not the original position.
     */
    @Transactional
    public void pasteMarkedBlock(String worldId, String sessionId, int x, int y, int z) {
        // Get marked block data from Redis
        Optional<Block> originalBlockOpt = getRegisterBlockData(worldId, sessionId);
        if (originalBlockOpt.isEmpty()) {
            log.warn("No marked block data for paste: session={}", sessionId);
            return;
        }

        Block originalBlock = originalBlockOpt.get();
        setBlock(worldId, sessionId, originalBlock, x, y, z);
    }

    public void setBlock(String worldId, String sessionId, Block originalBlock, int x, int y, int z) {
        // Clone block with all properties (without position)
        Block pastedBlock = BlockUtil.cloneBlock(originalBlock);

        // Set new position
        pastedBlock.setPosition(Vector3Int.builder()
                .x(x)
                .y(y)
                .z(z)
                .build());

        // Get edit state to determine layer
        EditState editState = getEditState(worldId, sessionId);
        if (editState.getSelectedLayer() == null) {
            log.error("No layer selected for editing: session={}", sessionId);
            return;
        }

        // Get layer information
        Optional<WLayer> layerOpt = layerService.findLayer(worldId, editState.getSelectedLayer());
        if (layerOpt.isEmpty()) {
            log.error("Selected layer not found: session={} layer={}", sessionId, editState.getSelectedLayer());
            return;
        }

        WLayer layer = layerOpt.get();
        String layerDataId = layer.getLayerDataId();

        // Determine modelName for MODEL layers
        String modelName = null;
        if (layer.getLayerType() == de.mhus.nimbus.world.shared.layer.LayerType.MODEL) {
            String selectedModelId = editState.getSelectedModelId();
            if (selectedModelId != null) {
                Optional<de.mhus.nimbus.world.shared.layer.WLayerModel> modelOpt =
                    layerService.loadModelById(selectedModelId);
                if (modelOpt.isPresent()) {
                    modelName = modelOpt.get().getName();
                }
            }
        }

        // Calculate chunk coordinates
        de.mhus.nimbus.world.shared.world.WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalStateException("World not found: " + worldId));

        // Save to WEditCache with modelName
        editCacheService.doSetAndSendBlock(world, layerDataId, modelName, pastedBlock, editState.getSelectedGroup());

        log.info("Block pasted: session={} layer={} to=({},{},{}) type={}",
                sessionId, layer.getName(), x, y, z, pastedBlock.getBlockTypeId());

    }

    /**
     * Delete block at position.
     */
    @Transactional
    public void deleteBlock(String worldId, String sessionId, int x, int y, int z) {
        log.debug("Delete block: session={} pos=({},{},{})", sessionId, x, y, z);
        setBlock(worldId, sessionId, AIR_BLOCK, x, y, z);
    }

    /**
     * Clone block from layer at position.
     * Combination of finding the block at the position and pasting it.
     * The block is read from the layer system and inserted into the current edit layer.
     */
    @Transactional
    public void cloneBlock(String worldId, String sessionId, int x, int y, int z) {
        log.debug("Clone block: session={} pos=({},{},{})", sessionId, x, y, z);

        // Get block info at this position from the layer system
        Map<String, Object> blockInfo = blockInfoService.loadBlockInfo(worldId, sessionId, x, y, z);
        if (blockInfo == null || blockInfo.isEmpty()) {
            log.warn("No block found to clone at position: session={} pos=({},{},{})", sessionId, x, y, z);
            return;
        }

        // Extract block data
        @SuppressWarnings("unchecked")
        Map<String, Object> blockData = (Map<String, Object>) blockInfo.get("block");
        if (blockData == null) {
            log.warn("No block data in blockInfo for clone: session={} pos=({},{},{})", sessionId, x, y, z);
            return;
        }

        try {
            // Convert to Block object
            String blockJson = objectMapper.writeValueAsString(blockData);
            Block blockToClone = objectMapper.readValue(blockJson, Block.class);

            // Use existing setBlock method to paste at same position
            // This handles all the overlay logic, client updates, etc.
            setBlock(worldId, sessionId, blockToClone, x, y, z);

            log.info("Block cloned: session={} pos=({},{},{}) type={}",
                    sessionId, x, y, z, blockToClone.getBlockTypeId());
        } catch (Exception e) {
            log.error("Failed to clone block: session={} pos=({},{},{})", sessionId, x, y, z, e);
        }
    }

    /**
     * Validate that selected layer exists and is enabled.
     */
    public Optional<WLayer> validateSelectedLayer(EditState state) {
        if (state.getSelectedLayer() == null) {
            return Optional.empty();
        }
        return layerService.findLayer(state.getWorldId(), state.getSelectedLayer())
                .filter(WLayer::isEnabled);
    }

    // ===== PRIVATE HELPERS =====

    private String editStateKey(String sessionId) {
        return EDIT_STATE_PREFIX + sessionId + ":";
    }

    private void saveEditState(String worldId, String sessionId, EditState state) {
        String key = editStateKey(sessionId);

        redisService.putValue(worldId, key + "editMode", String.valueOf(state.isEditMode()), EDIT_STATE_TTL);

        if (state.getEditAction() != null) {
            redisService.putValue(worldId, key + "editAction", state.getEditAction().name(), EDIT_STATE_TTL);
        }

        if (state.getSelectedLayer() != null) {
            redisService.putValue(worldId, key + "selectedLayer", state.getSelectedLayer(), EDIT_STATE_TTL);
        } else {
            redisService.deleteValue(worldId, key + "selectedLayer");
        }

        if (state.getSelectedModelId() != null) {
            redisService.putValue(worldId, key + "selectedModelId", state.getSelectedModelId(), EDIT_STATE_TTL);
        } else {
            redisService.deleteValue(worldId, key + "selectedModelId");
        }

        if (state.getMountX() != null) {
            redisService.putValue(worldId, key + "mountX", String.valueOf(state.getMountX()), EDIT_STATE_TTL);
        }
        if (state.getMountY() != null) {
            redisService.putValue(worldId, key + "mountY", String.valueOf(state.getMountY()), EDIT_STATE_TTL);
        }
        if (state.getMountZ() != null) {
            redisService.putValue(worldId, key + "mountZ", String.valueOf(state.getMountZ()), EDIT_STATE_TTL);
        }

        redisService.putValue(worldId, key + "selectedGroup", String.valueOf(state.getSelectedGroup()), EDIT_STATE_TTL);

        log.trace("Edit state saved: session={} layer={}",
                sessionId, state.getSelectedLayer());
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value);
    }

    private EditAction parseEditAction(String value) {
        if (value == null) return EditAction.OPEN_CONFIG_DIALOG;
        try {
            return EditAction.valueOf(value);
        } catch (IllegalArgumentException e) {
            return EditAction.OPEN_CONFIG_DIALOG;
        }
    }

    private Integer parseInt(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Mark a block at the specified position.
     * Sends command to client to highlight/mark the block.
     */
    @Transactional
    public void doMarkBlock(String worldId, String sessionId, int x, int y, int z) {
        // Get playerUrl from WSession
        Optional<WSession> wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot mark block", sessionId);
            return;
        }

        String playerUrl = wSession.get().getPlayerUrl();

        setMarkedBlock(worldId, sessionId, x, y, z);

        // Send command to client to mark the block visually
        clientSetSelectedEditBlock(worldId, sessionId, playerUrl, x, y, z);

        log.info("Block marked: worldId={}, session={}, pos=({},{},{})", worldId, sessionId, x, y, z);
    }

    /**
     * Clear the marked block.
     * Removes marked block from Redis and sends empty setSelectedEditBlock to client.
     */
    @Transactional
    public void clearMarkedBlock(String worldId, String sessionId) {
        // Get playerUrl from WSession
        Optional<WSession> wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot clear mark", sessionId);
            return;
        }

        String playerUrl = wSession.get().getPlayerUrl();

        // Remove marked block from Redis
        String key = editStateKey(sessionId);
        redisService.deleteValue(worldId, key + "markedBlockX");
        redisService.deleteValue(worldId, key + "markedBlockY");
        redisService.deleteValue(worldId, key + "markedBlockZ");

        // Send empty command to client to clear the visual marker
        CommandContext ctx = CommandContext.builder()
                .worldId(worldId)
                .sessionId(sessionId)
                .originServer("world-control")
                .build();
        worldClient.sendPlayerCommand(
                worldId,
                sessionId,
                playerUrl,
                "client",
                List.of("setSelectedEditBlock"),
                ctx);

        log.info("Marked block cleared: worldId={}, session={}", worldId, sessionId);
    }

    /**
     * Get marked block as Block object from Redis.
     * Returns the complete Block that was stored when the block was marked.
     * Uses WSessionService to retrieve BlockRegister data.
     */
    @Transactional(readOnly = true)
    public Optional<Block> getRegisterBlockData(String worldId, String sessionId) {
        // Get block register from WSessionService
        Optional<BlockRegister> blockRegisterOpt = wSessionService.getBlockRegister(sessionId);
        if (blockRegisterOpt.isEmpty()) {
            log.debug("No registered block data found: sessionId={}", sessionId);
            return Optional.empty();
        }

        Block block = blockRegisterOpt.get().getBlock();
        if (block == null) {
            log.warn("No block data in block register: sessionId={}", sessionId);
            return Optional.empty();
        }

        log.debug("Retrieved marked block: blockTypeId={}", block.getBlockTypeId());
        return Optional.of(block);
    }

    /**
     * Set paste block in Redis.
     * Stores a complete block definition for later paste operations.
     */
    @Transactional
    public void setPasteBlock(String worldId, String sessionId, String blockJson) {
        String key = editStateKey(sessionId);
        redisService.putValue(worldId, key + "pasteBlock", blockJson, EDIT_STATE_TTL);
        log.debug("Paste block set: session={}", sessionId);
    }

    /**
     * Get paste block from Redis.
     * Returns the stored block JSON for paste operations.
     */
    @Transactional(readOnly = true)
    public Optional<String> getPasteBlock(String worldId, String sessionId) {
        String key = editStateKey(sessionId);
        return redisService.getValue(worldId, key + "pasteBlock");
    }

    /**
     * Set marked block data (complete block JSON for palette selection).
     * Uses existing storeMarkedBlockInfo to store in Redis.
     *
     * @param worldId World identifier
     * @param sessionId Session identifier
     * @param blockJson Block data as JSON string
     */
    public void setBlockRegisterData(String worldId, String sessionId, String blockJson) {
        try {

            if (blockJson == null ) {
                storeBlockDataRegistry(worldId, sessionId, null);
                log.info("Cleared block register data: worldId={}, sessionId={}",
                        worldId, sessionId);
                return;
            }
            // Parse block JSON
            Block block = objectMapper.readValue(blockJson, Block.class);

            // Create blockInfo map (same format as when marking from world)
            Map<String, Object> blockInfo = new HashMap<>();
            blockInfo.put("block", block);

            // Use existing store method
            storeBlockDataRegistry(worldId, sessionId, blockInfo);

            log.info("Register block set from palette: worldId={}, sessionId={}, blockTypeId={}",
                    worldId, sessionId, block.getBlockTypeId());

        } catch (Exception e) {
            log.error("Failed to set register block data: worldId={}, sessionId={}",
                    worldId, sessionId, e);
            throw new RuntimeException("Failed to set marked block data", e);
        }
    }

    /**
     * Get block at position, reading from overlay, layer, and chunk (in that order).
     * Uses blockInfoService which handles the priority correctly:
     * 1. Overlay (if sessionId provided)
     * 2. Selected layer (if editState has selectedLayer)
     * 3. Chunk data
     *
     * @param editState Edit state containing layer selection
     * @param sessionId Session ID for overlay lookup
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Block at position, or null if not found
     */
    public Block getBlock(EditState editState, String sessionId, int x, int y, int z) {
        String worldId = editState.getWorldId();

        // Use blockInfoService which handles the priority correctly
        // It reads from: overlay -> layer -> chunk
        Map<String, Object> blockInfo = blockInfoService.loadBlockInfo(worldId, sessionId, x, y, z);

        if (blockInfo == null) {
            return null;
        }

        Object blockObj = blockInfo.get("block");
        if (blockObj == null) {
            return null;
        }

        try {
            return objectMapper.convertValue(blockObj, Block.class);
        } catch (Exception e) {
            log.warn("Failed to convert block at ({},{},{}): {}", x, y, z, e.getMessage());
            return null;
        }
    }

    /**
     * Update block at position.
     * Writes to overlay and sends update to client.
     * Always writes through the selected layer in edit state.
     *
     * @param editState Edit state containing session and layer info
     * @param sessionId Session ID for overlay and client updates
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param block Block to write
     * @return true if successful
     */
    public boolean updateBlock(EditState editState, String sessionId, int x, int y, int z, Block block) {
        String worldId = editState.getWorldId();

        // Set position in block if not already set
        if (block.getPosition() == null) {
            block.setPosition(Vector3Int.builder()
                    .x(x)
                    .y(y)
                    .z(z)
                    .build());
        }

        // Get layerDataId from selected layer
        String selectedLayer = editState.getSelectedLayer();
        if (selectedLayer == null) {
            log.error("No layer selected in edit state: worldId={}, sessionId={}", worldId, sessionId);
            return false;
        }

        Optional<WLayer> layerOpt = layerService.findLayer(worldId, selectedLayer);
        if (layerOpt.isEmpty()) {
            log.error("Selected layer not found: worldId={}, layer={}", worldId, selectedLayer);
            return false;
        }

        WLayer layer = layerOpt.get();
        String layerDataId = layer.getLayerDataId();

        // Determine modelName for MODEL layers
        String modelName = null;
        if (layer.getLayerType() == de.mhus.nimbus.world.shared.layer.LayerType.MODEL) {
            String selectedModelId = editState.getSelectedModelId();
            if (selectedModelId != null) {
                // Load model to get title
                Optional<de.mhus.nimbus.world.shared.layer.WLayerModel> modelOpt =
                    layerService.loadModelById(selectedModelId);
                if (modelOpt.isPresent()) {
                    modelName = modelOpt.get().getName();
                } else {
                    log.warn("Selected model not found: modelId={}, using null", selectedModelId);
                }
            } else {
                log.warn("MODEL layer selected but no selectedModelId in edit state, using null");
            }
        }

        // Get world and calculate chunk key
        var world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalStateException("World not found: " + worldId));

        editCacheService.doSetAndSendBlock(world, layerDataId, modelName, block, editState.getSelectedGroup());

        return true;
    }

    /**
     * Update model display based on current edit state.
     * Clears old display and shows new model if applicable.
     *
     * @param worldId   World identifier
     * @param sessionId Session identifier
     * @param state     Current edit state
     */
    private void updateModelDisplay(String worldId, String sessionId, EditState state) {
        // Always clear first
        clearModelDisplay(worldId, sessionId);

        // Show new model if MODEL layer is selected
        if (state.getSelectedLayer() != null) {
            Optional<WLayer> layerOpt = layerService.findLayer(worldId, state.getSelectedLayer());
            if (layerOpt.isPresent() && layerOpt.get().getLayerType() == LayerType.MODEL) {
                displayModelInClient(worldId, sessionId, layerOpt.get(),state.getSelectedGroup());
            }
        }
    }

    /**
     * Display model blocks in client using modelselector command.
     * Stores model selector in WSession and sends command to display it.
     *
     * @param worldId   World identifier
     * @param sessionId Session identifier
     * @param layer     Layer with MODEL type
     */
    private void displayModelInClient(String worldId, String sessionId, WLayer layer, int selectedGroupId) {
        // Get WSession
        Optional<WSession> wSessionOpt = wSessionService.getWithPlayerUrl(sessionId);
        if (wSessionOpt.isEmpty() || Strings.isBlank(wSessionOpt.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot display model", sessionId);
            return;
        }

        WSession wSession = wSessionOpt.get();
        String playerUrl = wSession.getPlayerUrl();

        // Get all block positions from all models via WLayerService
        List<int[]> positions = layerService.getModelBlockPositions(layer.getLayerDataId());

        // Also load blocks from WEditCache for this layer
        List<de.mhus.nimbus.world.shared.layer.WEditCache> editCacheBlocks =
            editCacheService.findByWorldIdAndLayerDataId(worldId, layer.getLayerDataId());

        if (positions.isEmpty() && editCacheBlocks.isEmpty()) {
            log.debug("No blocks found in models or edit cache for layer {}", layer.getName());
            return;
        }

        // Build ModelSelector
        // Source format: "layerDataId:layerName" - used as auto select title
        String source = layer.getLayerDataId() + ":" + layer.getName();
        ModelSelector modelSelector = ModelSelector.builder()
                .defaultColor("#dddd00") // for modified blocks
                .autoSelectName(source)
                .blocks(new ArrayList<>())
                .build();

        // Add model blocks
        for (int[] pos : positions) {
            String color;
            if (pos[4] == 0 && pos[5] == 0 && pos[6] == 0) {
                color = "#ff0000"; // the root block
            } else if (pos[3] == selectedGroupId) {
                color = "#ffff00"; // selected group
            } else {
                color = "#00ff00"; // for existing blocks
            }
            modelSelector.addBlock(pos[0], pos[1], pos[2], color);
        }

        // Add WEditCache blocks (modified/new blocks)
        for (de.mhus.nimbus.world.shared.layer.WEditCache cache : editCacheBlocks) {
            de.mhus.nimbus.world.shared.layer.LayerBlock layerBlock = cache.getBlock();
            if (layerBlock != null && layerBlock.getBlock() != null) {
                var block = layerBlock.getBlock();
                modelSelector.addBlock(
                        block.getPosition().getX(),
                        block.getPosition().getY(),
                        block.getPosition().getZ(),
                        "#dddd00" // modified/cached blocks
                );
            }
        }

        // Convert ModelSelector to List<String> and store in WSession
        List<String> modelSelectorData = ModelSelectorUtil.toStringList(modelSelector);
        wSessionService.updateModelSelector(sessionId, modelSelectorData);

        // Build command context
        CommandContext ctx = CommandContext.builder()
                .worldId(worldId)
                .sessionId(sessionId)
                .originServer("world-control")
                .build();

        // Send ShowModelSelectorCommand to player
        // This command will load ModelSelector from WSession and send to client
        worldClient.sendPlayerCommand(
                worldId,
                sessionId,
                playerUrl,
                "client.ShowModelSelector",
                List.of(),  // No arguments needed - uses session ID from context
                ctx
        );

        log.info("Stored model selector in WSession and sent display command: layer={} session={} modelBlocks={} cachedBlocks={} total={}",
                layer.getName(), sessionId, positions.size(), editCacheBlocks.size(),
                positions.size() + editCacheBlocks.size());
    }

    /**
     * Clear model display in client.
     * Removes ModelSelector from WSession and sends disable command.
     *
     * @param worldId   World identifier
     * @param sessionId Session identifier
     */
    private void clearModelDisplay(String worldId, String sessionId) {
        // Get WSession
        Optional<WSession> wSessionOpt = wSessionService.getWithPlayerUrl(sessionId);
        if (wSessionOpt.isEmpty() || Strings.isBlank(wSessionOpt.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot clear model display", sessionId);
            return;
        }

        WSession wSession = wSessionOpt.get();
        String playerUrl = wSession.getPlayerUrl();

        // Clear ModelSelector from WSession
        wSessionService.updateModelSelector(sessionId, null);

        // Build command context
        CommandContext ctx = CommandContext.builder()
                .worldId(worldId)
                .sessionId(sessionId)
                .originServer("world-control")
                .build();

        // Send disable command to client: modelselector, 'disable'
        worldClient.sendPlayerCommand(
                worldId,
                sessionId,
                playerUrl,
                "client",
                List.of("modelselector", "disable"),
                ctx
        );

        log.info("Cleared model display and removed from WSession: session={}", sessionId);
    }

    /**
     * Block position record.
     */
    public record BlockPosition(int x, int y, int z) {}
}
