package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.EditAction;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.EditService;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.edit.BlockUpdateService;
import de.mhus.nimbus.world.shared.session.EditState;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.redis.WorldRedisService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Editor REST API controller.
 * Manages edit mode configuration and layer selection.
 */
@RestController
@RequestMapping("/control/editor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Editor", description = "Edit mode and layer editing")
public class EditorController extends BaseEditorController {

    private final EditService editService;
    private final WLayerService layerService;
    private final BlockUpdateService blockUpdateService;
    private final de.mhus.nimbus.world.shared.layer.WDirtyChunkService dirtyChunkService;
    private final WorldRedisService redisService;
    private final WSessionService wSessionService;
    private final WorldClientService worldClientService;
    private final EngineMapper engineMapper;
    private final WWorldService worldService;
    private final de.mhus.nimbus.world.shared.layer.WEditCacheDirtyService editCacheDirtyService;
    private final de.mhus.nimbus.world.shared.layer.WEditCacheService editCacheService;

    // ===== EDIT STATE =====

    /**
     * GET /control/editor/{worldId}/session/{sessionId}/edit
     * Returns full edit state
     */
    @GetMapping("/{worldId}/session/{sessionId}/edit")
    public ResponseEntity<?> getEditState(
            @PathVariable String worldId,
            @PathVariable String sessionId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        EditState state = editService.getEditState(worldId, sessionId);
        Optional<EditService.BlockPosition> selectedBlock = editService.getSelectedBlock(worldId, sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("editMode", state.isEditMode());
        response.put("editAction", state.getEditAction() != null ? state.getEditAction().name() : "OPEN_CONFIG_DIALOG");
        response.put("selectedLayer", state.getSelectedLayer());
        response.put("selectedModelId", state.getSelectedModelId());
        response.put("mountX", state.getMountX() != null ? state.getMountX() : 0);
        response.put("mountY", state.getMountY() != null ? state.getMountY() : 0);
        response.put("mountZ", state.getMountZ() != null ? state.getMountZ() : 0);
        response.put("selectedGroup", state.getSelectedGroup());

        // Add selected block coordinates
        if (selectedBlock.isPresent()) {
            Map<String, Integer> blockPos = Map.of(
                    "x", selectedBlock.get().x(),
                    "y", selectedBlock.get().y(),
                    "z", selectedBlock.get().z()
            );
            response.put("selectedBlock", blockPos);
        } else {
            response.put("selectedBlock", null);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /control/editor/{worldId}/session/{sessionId}/edit
     * Update edit state (partial updates supported).
     */
    @PutMapping("/{worldId}/session/{sessionId}/edit")
    public ResponseEntity<?> updateEditState(
            @PathVariable String worldId,
            @PathVariable String sessionId,
            @RequestBody EditStateUpdateRequest request) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        EditState updated = editService.updateEditState(worldId, sessionId, state -> {
            if (request.editMode != null) {
                state.setEditMode(request.editMode);
            }
            if (request.editAction != null) {
                try {
                    EditAction action = EditAction.valueOf(request.editAction);
                    state.setEditAction(action);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid edit action: {}", request.editAction);
                }
            }
            if (request.selectedLayer != null) {
                state.setSelectedLayer(request.selectedLayer);
            }
            if (request.selectedModelId != null) {
                state.setSelectedModelId(request.selectedModelId);
            }
            if (request.mountX != null) {
                state.setMountX(request.mountX);
            }
            if (request.mountY != null) {
                state.setMountY(request.mountY);
            }
            if (request.mountZ != null) {
                state.setMountZ(request.mountZ);
            }
            if (request.selectedGroup != null) {
                state.setSelectedGroup(request.selectedGroup);
            }
        });

        Map<String, Object> response = new HashMap<>();
        response.put("editMode", updated.isEditMode());
        response.put("editAction", updated.getEditAction() != null ? updated.getEditAction().name() : "OPEN_CONFIG_DIALOG");
        response.put("selectedLayer", updated.getSelectedLayer());
        response.put("selectedModelId", updated.getSelectedModelId());
        response.put("mountX", updated.getMountX() != null ? updated.getMountX() : 0);
        response.put("mountY", updated.getMountY() != null ? updated.getMountY() : 0);
        response.put("mountZ", updated.getMountZ() != null ? updated.getMountZ() : 0);
        response.put("selectedGroup", updated.getSelectedGroup());

        return ResponseEntity.ok(response);
    }

    // ===== LAYERS =====

    /**
     * GET /control/editor/{worldId}/layers
     * List all layers for selection dropdown.
     */
    @GetMapping("/{worldId}/layers")
    public ResponseEntity<?> listLayers(@PathVariable String worldId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        List<WLayer> layers = layerService.findLayersByWorld(worldId);

        List<Map<String, Object>> dtos = layers.stream()
                .map(layer -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", layer.getId());
                    dto.put("name", layer.getName());
                    dto.put("layerType", layer.getLayerType().name());
                    dto.put("enabled", layer.isEnabled());
                    dto.put("order", layer.getOrder());
                    dto.put("layerDataId", layer.getLayerDataId());
                    // Note: mountX/Y/Z and groups are now in WLayerModel, not WLayer
                    return dto;
                })
                .toList();

        return ResponseEntity.ok(Map.of("layers", dtos));
    }

    /**
     * POST /control/editor/{worldId}/layers
     * Create a new layer.
     */
    @PostMapping("/{worldId}/layers")
    public ResponseEntity<?> createLayer(
            @PathVariable String worldId,
            @RequestBody de.mhus.nimbus.world.shared.dto.CreateLayerRequest request) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(request.name(), "title");
        if (validation != null) return validation;

        // Check if layer already exists
        Optional<WLayer> existing = layerService.findLayer(worldId, request.name());
        if (existing.isPresent()) {
            return conflict("Layer with title '" + request.name() + "' already exists");
        }

        // Create new layer
        de.mhus.nimbus.world.shared.layer.LayerType layerType = request.layerType() != null
            ? request.layerType()
            : de.mhus.nimbus.world.shared.layer.LayerType.GROUND;
        int order = request.order() != null ? request.order() : 10;
        boolean allChunks = request.allChunks() != null ? request.allChunks() : false;
        boolean baseGround = request.baseGround() != null ? request.baseGround() : false;

        WLayer saved = layerService.createLayer(worldId, request.name(), layerType, order, allChunks, List.of(), baseGround);

        // Note: mountX/Y/Z are now in WLayerModel, not WLayer
        // Models should be created separately via model creation endpoint

        Map<String, Object> response = new HashMap<>();
        response.put("name", saved.getName());
        response.put("layerType", saved.getLayerType().name());
        response.put("enabled", saved.isEnabled());
        response.put("order", saved.getOrder());

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /control/editor/{worldId}/layers/{layerName}
     * Delete a layer and all its data.
     */
    @DeleteMapping("/{worldId}/layers/{layerName}")
    public ResponseEntity<?> deleteLayer(
            @PathVariable String worldId,
            @PathVariable String layerName) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(layerName, "layerName");
        if (validation != null) return validation;

        // Check if layer exists
        Optional<WLayer> layer = layerService.findLayer(worldId, layerName);
        if (layer.isEmpty()) {
            return notFound("Layer not found: " + layerName);
        }

        // Delete layer and associated data
        layerService.deleteLayer(worldId, layerName);

        return ResponseEntity.ok(Map.of("message", "Layer deleted successfully"));
    }

    // ===== BLOCK EDITOR =====

    /**
     * PUT /control/editor/{worldId}/session/{sessionId}/block
     * Update a block at the selected position and trigger "b.u" to client.
     */
    @PutMapping("/{worldId}/session/{sessionId}/block")
    public ResponseEntity<?> updateBlock(
            @PathVariable String worldId,
            @PathVariable String sessionId,
            @RequestBody String request) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        // Get edit state
        EditState state = editService.getEditState(worldId, sessionId);

        // Validate edit mode and layer selection
        if (!state.isEditMode()) {
            return bad("Edit mode not enabled for this session");
        }

        if (state.getSelectedLayer() == null || state.getSelectedLayer().isBlank()) {
            return bad("No layer selected - cannot save block without layer selection");
        }

        try {
            // Validate blockJson
            if (Strings.isEmpty(request)) {
                return bad("blockJson is required");
            }

            // Deserialize block using EngineMapper
            Block block;
            try {
                block = engineMapper.readValue(request, Block.class);
            } catch (Exception e) {
                log.error("Failed to parse blockJson: {}", request, e);
                return bad("Invalid blockJson: " + e.getMessage());
            }

            // Validate block has position
            if (block.getPosition() == null) {
                return bad("Block must have position");
            }

            // Save block to WEditCache
            int x = (int) block.getPosition().getX();
            int y = (int) block.getPosition().getY();
            int z = (int) block.getPosition().getZ();

            boolean success = editService.updateBlock(state, sessionId, x, y, z, block);
            if (!success) {
                return bad("Failed to save block to edit cache");
            }

            log.info("Block saved and update sent: session={} layer={} pos=({},{},{}) blockTypeId={}",
                    sessionId, state.getSelectedLayer(), x, y, z, block.getBlockTypeId());

            Map<String, Object> response = new HashMap<>();
            response.put("blockTypeId", block.getBlockTypeId());
            response.put("layer", state.getSelectedLayer());
            response.put("saved", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update block: session={}", sessionId, e);
            return bad("Failed to update block: " + e.getMessage());
        }
    }

    private ResponseEntity<?> error(String message) {
        return ResponseEntity.status(500).body(Map.of("error", message));
    }

    // ===== DTOs =====

    /**
     * Request DTO for edit state updates.
     * All fields are optional (partial updates).
     */
    @Data
    public static class EditStateUpdateRequest {
        private Boolean editMode;
        private String editAction;
        private String selectedLayer;
        private String selectedModelId;
        private Integer mountX;
        private Integer mountY;
        private Integer mountZ;
        private Integer selectedGroup;
    }

    /**
     * Request DTO for block updates.
     * Accepts complete block definition as JSON string from block-editor.
     */
    @Data
    public static class UpdateBlockRequest {
        private String blockJson;  // Complete block definition as JSON string
        private String meta;  // Optional additional metadata
    }

    // ===== EDIT MODE CONTROL =====

    /**
     * POST /control/editor/{worldId}/session/{sessionId}/activate
     * Activates edit mode for the session.
     */
    @PostMapping("/{worldId}/session/{sessionId}/activate")
    public ResponseEntity<?> activateEditMode(
            @PathVariable String worldId,
            @PathVariable String sessionId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        try {
            // 1. Validate: selectedLayer must be set
            EditState state = editService.getEditState(worldId, sessionId);
            if (state.getSelectedLayer() == null) {
                return bad("No layer selected. Please select a layer before activating edit mode.");
            }

            // 2. Update Redis: editMode=true
            editService.setEditMode(worldId, sessionId, true);

            // 3. Get playerUrl from WSessionService
            Optional<WSession> session = wSessionService.getWithPlayerUrl(sessionId);
            if (session.isEmpty() || session.get().getPlayerUrl() == null) {
                return bad("Player URL not available. Session may not be connected.");
            }

            String playerUrl = session.get().getPlayerUrl();

            // 4. Send "edit" command to world-player
            CommandContext ctx = CommandContext.builder()
                    .worldId(worldId)
                    .sessionId(sessionId)
                    .originServer("world-control")
                    .build();

            try {
                worldClientService.sendPlayerCommand(
                        worldId,
                        sessionId,
                        playerUrl,
                        "edit",
                        List.of("true"),
                        ctx
                );
            } catch (Exception e) {
                log.warn("Failed to send edit command to player: {}", e.getMessage());
                // Continue anyway - Redis state is updated
            }

            log.info("Edit mode activated: worldId={}, sessionId={}, layer={}",
                    worldId, sessionId, state.getSelectedLayer());

            // 5. Return success
            return ResponseEntity.ok().body(Map.of(
                    "editMode", true,
                    "layer", state.getSelectedLayer(),
                    "message", "Edit mode activated"
            ));

        } catch (Exception e) {
            log.error("Failed to activate edit mode: worldId={}, sessionId={}", worldId, sessionId, e);
            return bad("Failed to activate edit mode: " + e.getMessage());
        }
    }

    /**
     * POST /control/editor/{worldId}/session/{sessionId}/discard
     * Discards cached changes for current layer and deactivates edit mode.
     */
    @PostMapping("/{worldId}/session/{sessionId}/discard")
    public ResponseEntity<?> discardOverlays(
            @PathVariable String worldId,
            @PathVariable String sessionId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        try {
            // 1. Validate: editMode must be active
            EditState state = editService.getEditState(worldId, sessionId);
            if (!state.isEditMode()) {
                return bad("Edit mode not active");
            }
            if (state.getSelectedLayer() == null) {
                return bad("No layer selected");
            }

            // 2. Discard changes (deletes cached blocks for current layer, marks chunks dirty for refresh)
            long deletedCount = editService.discardChanges(worldId, sessionId);

            log.info("Discard changes completed: worldId={}, sessionId={}, layer={}, deleted={}",
                    worldId, sessionId, state.getSelectedLayer(), deletedCount);

            // 3. Return count
            return ResponseEntity.ok().body(Map.of(
                    "deleted", deletedCount,
                    "layer", state.getSelectedLayer(),
                    "editMode", state.isEditMode(),
                    "message", "Discarded " + deletedCount + " cached blocks"
            ));

        } catch (Exception e) {
            log.error("Failed to discard changes: worldId={}, sessionId={}", worldId, sessionId, e);
            return bad("Failed to discard changes: " + e.getMessage());
        }
    }

    /**
     * POST /control/editor/{worldId}/session/{sessionId}/change
     * Deactivates edit mode and clears layer/model selection.
     * Preserves cached changes (WEditCache) so user can select a different layer.
     */
    @PostMapping("/{worldId}/session/{sessionId}/change")
    public ResponseEntity<?> changeLayer(
            @PathVariable String worldId,
            @PathVariable String sessionId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        try {
            // 1. Validate: editMode must be active
            EditState state = editService.getEditState(worldId, sessionId);
            if (!state.isEditMode()) {
                return bad("Edit mode not active");
            }

            String previousLayer = state.getSelectedLayer();

            // 2. Clear edit mode and layer/model selection (preserves WEditCache)
            editService.updateEditState(worldId, sessionId, s -> {
                s.setEditMode(false);
                s.setSelectedLayer(null);
                s.setSelectedModelId(null);
            });

            log.info("Change layer completed: worldId={}, sessionId={}, previousLayer={}",
                    worldId, sessionId, previousLayer);

            // 3. Return success
            return ResponseEntity.ok().body(Map.of(
                    "previousLayer", previousLayer != null ? previousLayer : "",
                    "editMode", false,
                    "message", "Edit mode deactivated, cached changes preserved"
            ));

        } catch (Exception e) {
            log.error("Failed to change layer: worldId={}, sessionId={}", worldId, sessionId, e);
            return bad("Failed to change layer: " + e.getMessage());
        }
    }

    /**
     * POST /control/editor/{worldId}/session/{sessionId}/save
     * Saves overlays to the selected layer (fire-and-forget).
     */
    @PostMapping("/{worldId}/session/{sessionId}/save")
    public ResponseEntity<?> saveOverlays(
            @PathVariable String worldId,
            @PathVariable String sessionId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        try {
            // 1. Validate
            EditState state = editService.getEditState(worldId, sessionId);
            if (!state.isEditMode()) {
                return bad("Edit mode not active");
            }
            if (state.getSelectedLayer() == null) {
                return bad("No layer selected");
            }

            // 2. Apply changes (creates WEditCacheDirty entry, processes asynchronously)
            editService.applyChanges(worldId, sessionId);

            log.info("Apply changes triggered: worldId={}, sessionId={}, layer={}",
                    worldId, sessionId, state.getSelectedLayer());

            // 3. Return 202 Accepted immediately
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "message", "Apply changes operation started",
                            "layer", state.getSelectedLayer(),
                            "editMode", true
                    ));

        } catch (Exception e) {
            log.error("Failed to start apply changes: worldId={}, sessionId={}", worldId, sessionId, e);
            return bad("Failed to start apply changes: " + e.getMessage());
        }
    }

    /**
     * GET /control/editor/{worldId}/editcache/statistics
     * Returns edit cache statistics grouped by layer.
     * Shows block count and timestamps for each layer with cached edits.
     */
    @GetMapping("/{worldId}/editcache/statistics")
    public ResponseEntity<?> getEditCacheStatistics(@PathVariable String worldId) {
        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        try {
            List<Map<String, Object>> statistics = editService.getEditCacheStatistics(worldId);

            log.debug("Edit cache statistics retrieved: worldId={}, layerCount={}",
                    worldId, statistics.size());

            return ResponseEntity.ok(statistics);

        } catch (Exception e) {
            log.error("Failed to get edit cache statistics: worldId={}", worldId, e);
            return bad("Failed to get edit cache statistics: " + e.getMessage());
        }
    }

    /**
     * POST /control/editor/{worldId}/editcache/{layerDataId}/discard
     * Discards all cached changes for a specific layer.
     * Used by EditCache-Editor to delete cached blocks without requiring active edit mode.
     */
    @PostMapping("/{worldId}/editcache/{layerDataId}/discard")
    public ResponseEntity<?> discardEditCacheForLayer(
            @PathVariable String worldId,
            @PathVariable String layerDataId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        try {
            // Discard all cached blocks for this layer
            long deletedCount = editCacheDirtyService.discardChanges(worldId, layerDataId);

            log.info("Discard edit cache completed: worldId={}, layerDataId={}, deleted={}",
                    worldId, layerDataId, deletedCount);

            return ResponseEntity.ok().body(Map.of(
                    "deleted", deletedCount,
                    "layerDataId", layerDataId,
                    "message", "Discarded " + deletedCount + " cached blocks"
            ));

        } catch (Exception e) {
            log.error("Failed to discard edit cache: worldId={}, layerDataId={}", worldId, layerDataId, e);
            return bad("Failed to discard edit cache: " + e.getMessage());
        }
    }

    /**
     * POST /control/editor/{worldId}/editcache/{layerDataId}/apply
     * Applies all cached changes for a specific layer.
     * Used by EditCache-Editor to merge cached blocks into layer without requiring active edit mode.
     */
    @PostMapping("/{worldId}/editcache/{layerDataId}/apply")
    public ResponseEntity<?> applyEditCacheForLayer(
            @PathVariable String worldId,
            @PathVariable String layerDataId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        try {
            // Count cached blocks before applying
            long blockCount = editCacheService.countByWorldIdAndLayerDataId(worldId, layerDataId);

            if (blockCount == 0) {
                log.debug("No cached blocks to apply: worldId={}, layerDataId={}", worldId, layerDataId);
                return ResponseEntity.ok().body(Map.of(
                        "applied", 0L,
                        "layerDataId", layerDataId,
                        "message", "No cached blocks to apply"
                ));
            }

            // Apply changes immediately (marks dirty and processes)
            editCacheDirtyService.applyChanges(worldId, layerDataId);

            log.info("Apply edit cache completed: worldId={}, layerDataId={}, applied={}",
                    worldId, layerDataId, blockCount);

            return ResponseEntity.ok().body(Map.of(
                    "applied", blockCount,
                    "layerDataId", layerDataId,
                    "message", "Applied " + blockCount + " cached blocks"
            ));

        } catch (Exception e) {
            log.error("Failed to apply edit cache: worldId={}, layerDataId={}", worldId, layerDataId, e);
            return bad("Failed to apply edit cache: " + e.getMessage());
        }
    }

    // ===== BLOCK PALETTE SUPPORT =====

    /**
     * GET /control/editor/{worldId}/session/{sessionId}/blockRegister
     * Returns the complete block data for the currently marked block.
     * Reads from Redis overlay where the marked block is stored.
     * Used when adding a marked block to the palette.
     * Returns 200 with null/empty response if no marked block exists.
     */
    @GetMapping("/{worldId}/session/{sessionId}/blockRegister")
    public ResponseEntity<?> getBlockRegisterData(
            @PathVariable String worldId,
            @PathVariable String sessionId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        // Get marked block from EditService
        Optional<Block> blockOpt = editService.getRegisterBlockData(worldId, sessionId);
        if (blockOpt.isEmpty()) {
            log.debug("No marked block found: worldId={}, sessionId={}", worldId, sessionId);
            // Return 200 with null to indicate no marked block
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body("null");
        }

        try {
            Block block = blockOpt.get();

            // Serialize to JSON
            String blockJson = engineMapper.writeValueAsString(block);

            log.info("Retrieved marked block data: worldId={}, sessionId={}, blockTypeId={}",
                    worldId, sessionId, block.getBlockTypeId());

            // Return as JSON
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(blockJson);

        } catch (Exception e) {
            log.error("Failed to serialize marked block: worldId={}, sessionId={}",
                    worldId, sessionId, e);
            return bad("Failed to serialize marked block: " + e.getMessage());
        }
    }

    /**
     * POST /control/editor/{worldId}/session/{sessionId}/blockRegister
     * Sets a block as the current marked block (for palette selection and paste).
     * Stores complete block data in Redis overlay.
     * Position in block.position is optional/ignored - only block content matters.
     */
    @PostMapping("/{worldId}/session/{sessionId}/blockRegister")
    public ResponseEntity<?> setBlockRegisterData(
            @PathVariable String worldId,
            @PathVariable String sessionId,
            @RequestBody String blockJson) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        if (Strings.isEmpty(blockJson)) {
            return bad("Block data is required");
        }

        try {
            // Validate by parsing the JSON
            Block block = engineMapper.readValue(blockJson, Block.class);

            if (block == null || Strings.isBlank(block.getBlockTypeId())) {
                return bad("Invalid block data: blockTypeId is required");
            }

            // Store via EditService
            editService.setBlockRegisterData(worldId, sessionId, blockJson);

            log.info("register block set from palette: worldId={}, sessionId={}, blockTypeId={}",
                    worldId, sessionId, block.getBlockTypeId());

            return ResponseEntity.ok(Map.of(
                    "message", "Marked block set successfully",
                    "blockTypeId", block.getBlockTypeId()
            ));

        } catch (Exception e) {
            log.error("Failed to set register block: worldId={}, sessionId={}", worldId, sessionId, e);
            return bad("Failed to set marked block: " + e.getMessage());
        }
    }

    /**
     * POST /control/editor/{worldId}/session/{sessionId}/blockRegister
     * Sets a block as the current marked block (for palette selection and paste).
     * Stores complete block data in Redis overlay.
     * Position in block.position is optional/ignored - only block content matters.
     */
    @DeleteMapping("/{worldId}/session/{sessionId}/blockRegister")
    public ResponseEntity<?> clearBlockRegisterData(
            @PathVariable String worldId,
            @PathVariable String sessionId) {

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        try {
            // Store via EditService
            editService.setBlockRegisterData(worldId, sessionId, null);

            log.info("Register block cleared: worldId={}, sessionId={}",
                    worldId, sessionId);

            return ResponseEntity.ok(Map.of(
                    "message", "Register block cleared successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to set marked block: worldId={}, sessionId={}", worldId, sessionId, e);
            return bad("Failed to set marked block: " + e.getMessage());
        }
    }
}
