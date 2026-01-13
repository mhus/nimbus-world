package de.mhus.nimbus.world.generator.mcp;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.dto.CreateLayerRequest;
import de.mhus.nimbus.world.shared.layer.*;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP (Model Context Protocol) REST API Controller.
 * Provides unified access to world control operations for MCP clients.
 * <p>
 * Base path: /control/mcp
 */
@RestController
@RequestMapping("/control/mcp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "MCP", description = "Model Context Protocol API for world control")
public class McpController extends BaseEditorController {

    private final WSessionService sessionService;
    private final WLayerService layerService;
    private final WLayerModelRepository modelRepository;
    private final WLayerTerrainRepository terrainRepository;
    private final WWorldService worldService;
    private final WBlockTypeService blockTypeService;
    private final EngineMapper engineMapper;

    // ==================== MCP PROTOCOL ====================

    /**
     * Get MCP server info and available tools.
     * GET /control/mcp
     */
    @GetMapping
    @Operation(summary = "Get MCP server info and available tools")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MCP server info")
    })
    public ResponseEntity<?> getMcpInfo() {
        log.debug("MCP: Get server info");

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "Nimbus World Control MCP Server");
        serverInfo.put("version", "1.0.0");
        serverInfo.put("protocol", "mcp");
        serverInfo.put("description", "Model Context Protocol server for Nimbus world control operations");

        List<Map<String, Object>> tools = new ArrayList<>();

        // Session tools
        tools.add(createToolDescriptor(
                "get_marked_block",
                "Get the marked block position from a session",
                Map.of(
                        "sessionId", Map.of(
                                "type", "string",
                                "description", "Session ID",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "get_selected_block",
                "Get the selected block position from a session",
                Map.of(
                        "sessionId", Map.of(
                                "type", "string",
                                "description", "Session ID",
                                "required", true
                        )
                )
        ));

        // World tools
        tools.add(createToolDescriptor(
                "list_worlds",
                "List all available worlds",
                Map.of()
        ));

        tools.add(createToolDescriptor(
                "get_world",
                "Get detailed information about a specific world",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "get_block_types",
                "Get block types for a world",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "limit", Map.of(
                                "type", "number",
                                "description", "Maximum number of results",
                                "required", false,
                                "default", 100
                        )
                )
        ));

        // Layer tools
        tools.add(createToolDescriptor(
                "list_layers",
                "List all layers for a world",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "get_layer",
                "Get detailed information about a specific layer",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "layerId", Map.of(
                                "type", "string",
                                "description", "Layer ID",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "create_layer",
                "Create a new layer in a world",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "name", Map.of(
                                "type", "string",
                                "description", "Layer name (must be unique per world)",
                                "required", true
                        ),
                        "layerType", Map.of(
                                "type", "string",
                                "description", "Layer type: TERRAIN or MODEL",
                                "required", true,
                                "enum", List.of("TERRAIN", "MODEL")
                        ),
                        "order", Map.of(
                                "type", "number",
                                "description", "Layer order (lower renders first)",
                                "required", false,
                                "default", 0
                        ),
                        "mountX", Map.of(
                                "type", "number",
                                "description", "Mount point X (MODEL layers only)",
                                "required", false
                        ),
                        "mountY", Map.of(
                                "type", "number",
                                "description", "Mount point Y (MODEL layers only)",
                                "required", false
                        ),
                        "mountZ", Map.of(
                                "type", "number",
                                "description", "Mount point Z (MODEL layers only)",
                                "required", false
                        ),
                        "ground", Map.of(
                                "type", "boolean",
                                "description", "Whether this layer defines ground level",
                                "required", false,
                                "default", false
                        ),
                        "enabled", Map.of(
                                "type", "boolean",
                                "description", "Whether the layer is enabled",
                                "required", false,
                                "default", true
                        )
                        // Note: mountX/Y/Z, ground, and groups removed - use WLayerModel API
                )
        ));

        // Layer block tools
        tools.add(createToolDescriptor(
                "get_layer_blocks",
                "Get blocks from a MODEL layer",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "layerId", Map.of(
                                "type", "string",
                                "description", "Layer ID (must be MODEL type)",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "add_layer_blocks",
                "Add blocks to a MODEL layer",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "layerId", Map.of(
                                "type", "string",
                                "description", "Layer ID (must be MODEL type)",
                                "required", true
                        ),
                        "blocks", Map.of(
                                "type", "array",
                                "description", "Array of blocks to add",
                                "required", true,
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "x", Map.of("type", "number", "description", "X coordinate"),
                                                "y", Map.of("type", "number", "description", "Y coordinate"),
                                                "z", Map.of("type", "number", "description", "Z coordinate"),
                                                "blockId", Map.of("type", "string", "description", "Block type ID"),
                                                "group", Map.of("type", "number", "description", "Group ID (optional)")
                                        )
                                )
                        )
                )
        ));

        Map<String, Object> response = new HashMap<>();
        response.put("server", serverInfo);
        response.put("tools", tools);
        response.put("toolCount", tools.size());

        // Add REST API endpoints info
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("GET /control/mcp", "Get this server info");
        endpoints.put("GET /control/mcp/sessions/{sessionId}/marked-block", "Get marked block");
        endpoints.put("GET /control/mcp/sessions/{sessionId}/selected-block", "Get selected block");
        endpoints.put("GET /control/mcp/worlds", "List worlds");
        endpoints.put("GET /control/mcp/worlds/{worldId}", "Get world");
        endpoints.put("GET /control/mcp/worlds/{worldId}/blocktypes", "Get block types");
        endpoints.put("GET /control/mcp/worlds/{worldId}/layers", "List layers");
        endpoints.put("GET /control/mcp/worlds/{worldId}/layers/{layerId}", "Get layer");
        endpoints.put("POST /control/mcp/worlds/{worldId}/layers", "Create layer");
        endpoints.put("GET /control/mcp/worlds/{worldId}/layers/{layerId}/blocks", "Get layer blocks");
        endpoints.put("POST /control/mcp/worlds/{worldId}/layers/{layerId}/blocks", "Add layer blocks");
        // Note: groups endpoint removed - use WLayerModel API
        response.put("endpoints", endpoints);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createToolDescriptor(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", Map.of(
                "type", "object",
                "properties", parameters
        ));
        return tool;
    }

    // ==================== SESSION OPERATIONS ====================

    /**
     * Get marked block position from session.
     * GET /control/mcp/sessions/{sessionId}/marked-block
     */
//    @GetMapping("/sessions/{sessionId}/marked-block")
//    @Operation(summary = "Get marked block position from session")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "Success (may return null if no block marked)"),
//            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
//            @ApiResponse(responseCode = "404", description = "Session not found")
//    })
//    public ResponseEntity<?> getMarkedBlock(@Parameter(description = "Session ID") @PathVariable String sessionId) {
//        log.debug("MCP: Get marked block: sessionId={}", sessionId);
//
//        ResponseEntity<?> validation = validateId(sessionId, "sessionId");
//        if (validation != null) return validation;
//
//        // Get session to find worldId
//        Optional<WSession> sessionOpt = sessionService.get(sessionId);
//        if (sessionOpt.isEmpty()) {
//            return notFound("session not found");
//        }
//
//        String worldId = sessionOpt.get().getWorldId();
//        Optional<Block> markedBlock = editService.getRegisterBlockData(worldId, sessionId);
//
//        if (markedBlock.isEmpty()) {
//            return ResponseEntity.ok(Map.of("marked", false));
//        }
//
//        // Extract position from block data
//        Block block = markedBlock.get();
//        if (block.getPosition() == null) {
//            return ResponseEntity.ok(Map.of("marked", false));
//        }
//
//        return ResponseEntity.ok(Map.of(
//                "marked", true,
//                "x", (int) block.getPosition().getX(),
//                "y", (int) block.getPosition().getY(),
//                "z", (int) block.getPosition().getZ()
//        ));
//    }

//    /**
//     * Get selected block position from session.
//     * GET /control/mcp/sessions/{sessionId}/selected-block
//     */
//    @GetMapping("/sessions/{sessionId}/selected-block")
//    @Operation(summary = "Get selected block position from session")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "Success (may return null if no block selected)"),
//            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
//            @ApiResponse(responseCode = "404", description = "Session not found")
//    })
//    public ResponseEntity<?> getSelectedBlock(@Parameter(description = "Session ID") @PathVariable String sessionId) {
//        log.debug("MCP: Get selected block: sessionId={}", sessionId);
//
//        ResponseEntity<?> validation = validateId(sessionId, "sessionId");
//        if (validation != null) return validation;
//
//        Optional<WSession> sessionOpt = sessionService.get(sessionId);
//        if (sessionOpt.isEmpty()) {
//            return notFound("session not found");
//        }
//
//        String worldId = sessionOpt.get().getWorldId();
//        Optional<EditService.BlockPosition> selectedBlock = editService.getSelectedBlock(worldId, sessionId);
//
//        if (selectedBlock.isEmpty()) {
//            return ResponseEntity.ok(Map.of("selected", false));
//        }
//
//        EditService.BlockPosition pos = selectedBlock.get();
//        return ResponseEntity.ok(Map.of(
//                "selected", true,
//                "x", pos.x(),
//                "y", pos.y(),
//                "z", pos.z()
//        ));
//    }

    // ==================== WORLD OPERATIONS ====================

    /**
     * List all worlds.
     * GET /control/mcp/worlds
     */
    @GetMapping("/worlds")
    @Operation(summary = "List all worlds")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success")
    })
    public ResponseEntity<?> listWorlds() {
        log.debug("MCP: List worlds");

        List<WWorld> worlds = worldService.findAll();
        List<Map<String, Object>> worldDtos = worlds.stream()
                .map(this::toWorldDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "worlds", worldDtos,
                "count", worldDtos.size()
        ));
    }

    /**
     * Get world by ID.
     * GET /control/mcp/worlds/{worldId}
     */
    @GetMapping("/worlds/{worldId}")
    @Operation(summary = "Get world by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "World found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "World not found")
    })
    public ResponseEntity<?> getWorld(@Parameter(description = "World ID") @PathVariable String worldId) {
        log.debug("MCP: Get world: worldId={}", worldId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        return ResponseEntity.ok(wid);
    }

    // ==================== LAYER OPERATIONS ====================

    /**
     * List all layers for a world.
     * GET /control/mcp/worlds/{worldId}/layers
     */
    @GetMapping("/worlds/{worldId}/layers")
    @Operation(summary = "List all layers for a world")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> listLayers(@Parameter(description = "World ID") @PathVariable String worldId) {
        log.debug("MCP: List layers: worldId={}", worldId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        List<WLayer> layers = layerService.findByWorldId(worldId);
        List<Map<String, Object>> layerDtos = layers.stream()
                .map(this::toLayerDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "layers", layerDtos,
                "count", layerDtos.size()
        ));
    }

    /**
     * Get layer by ID.
     * GET /control/mcp/worlds/{worldId}/layers/{layerId}
     */
    @GetMapping("/worlds/{worldId}/layers/{layerId}")
    @Operation(summary = "Get layer by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Layer found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Layer not found")
    })
    public ResponseEntity<?> getLayer(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Layer ID") @PathVariable String layerId) {

        log.debug("MCP: Get layer: worldId={}, layerId={}", worldId, layerId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        var validation = validateId(layerId, "layerId");
        if (validation != null) return validation;

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty() || !layerOpt.get().getWorldId().equals(worldId)) {
            return notFound("layer not found");
        }

        return ResponseEntity.ok(toLayerDto(layerOpt.get()));
    }

    /**
     * Create new layer.
     * POST /control/mcp/worlds/{worldId}/layers
     */
    @PostMapping("/worlds/{worldId}/layers")
    @Operation(summary = "Create new layer")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Layer created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Layer name already exists")
    })
    public ResponseEntity<?> createLayer(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @RequestBody CreateLayerRequest request) {

        log.debug("MCP: Create layer: worldId={}, name={}", worldId, request.name());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        if (blank(request.name())) {
            return bad("name required");
        }

        if (request.layerType() == null) {
            return bad("layerType required (TERRAIN or MODEL)");
        }

        // Check for duplicate name
        if (layerService.findByWorldIdAndName(worldId, request.name()).isPresent()) {
            return conflict("layer name already exists");
        }

        try {
            // Note: mountX/Y/Z, ground, and groups are now in WLayerModel, not WLayer
            // These fields are ignored here - use WLayerModel API for MODEL layers
            WLayer layer = WLayer.builder()
                    .worldId(worldId)
                    .name(request.name())
                    .layerType(request.layerType())
                    .allChunks(request.allChunks() != null ? request.allChunks() : true)
                    .affectedChunks(request.affectedChunks() != null ? request.affectedChunks() : List.of())
                    .order(request.order() != null ? request.order() : 0)
                    .enabled(request.enabled() != null ? request.enabled() : true)
                    .baseGround(request.baseGround() != null ? request.baseGround() : false)
                    .build();

            layer.touchCreate();
            WLayer saved = layerService.save(layer);

            log.info("MCP: Created layer: id={}, name={}", saved.getId(), saved.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
        } catch (Exception e) {
            log.error("MCP: Failed to create layer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create layer: " + e.getMessage()));
        }
    }

    // Note: updateLayerGroups endpoint removed - groups are now in WLayerModel, not WLayer

    // ==================== LAYER BLOCK OPERATIONS ====================

    /**
     * Get layer blocks (for MODEL layers).
     * GET /control/mcp/worlds/{worldId}/layers/{layerId}/blocks
     */
    @GetMapping("/worlds/{worldId}/layers/{layerId}/blocks")
    @Operation(summary = "Get layer blocks (MODEL layers only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters or not a MODEL layer"),
            @ApiResponse(responseCode = "404", description = "Layer not found")
    })
    public ResponseEntity<?> getLayerBlocks(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Layer ID") @PathVariable String layerId) {

        log.debug("MCP: Get layer blocks: worldId={}, layerId={}", worldId, layerId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        var validation = validateId(layerId, "layerId");
        if (validation != null) return validation;

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty() || !layerOpt.get().getWorldId().equals(worldId)) {
            return notFound("layer not found");
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            return bad("operation only supported for MODEL layers");
        }

        if (layer.getLayerDataId() == null) {
            return ResponseEntity.ok(Map.of("blocks", List.of(), "count", 0));
        }

        Optional<WLayerModel> modelOpt = modelRepository.findFirstByLayerDataId(layer.getLayerDataId());
        if (modelOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("blocks", List.of(), "count", 0));
        }

        List<LayerBlock> blocks = modelOpt.get().getContent();
        List<Map<String, Object>> blockDtos = blocks.stream()
                .map(this::toLayerBlockDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "blocks", blockDtos,
                "count", blockDtos.size()
        ));
    }

    /**
     * Add blocks to layer (MODEL layers).
     * POST /control/mcp/worlds/{worldId}/layers/{layerId}/blocks
     */
    @PostMapping("/worlds/{worldId}/layers/{layerId}/blocks")
    @Operation(summary = "Add blocks to layer (MODEL layers only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Blocks added"),
            @ApiResponse(responseCode = "400", description = "Invalid request or not a MODEL layer"),
            @ApiResponse(responseCode = "404", description = "Layer not found")
    })
    public ResponseEntity<?> addLayerBlocks(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Layer ID") @PathVariable String layerId,
            @RequestBody AddBlocksRequest request) {

        log.debug("MCP: Add layer blocks: worldId={}, layerId={}, count={}", worldId, layerId, request.blocks().size());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        var validation = validateId(layerId, "layerId");
        if (validation != null) return validation;

        if (request.blocks() == null || request.blocks().isEmpty()) {
            return bad("blocks required");
        }

        Optional<WLayer> layerOpt = layerService.findById(layerId);
        if (layerOpt.isEmpty() || !layerOpt.get().getWorldId().equals(worldId)) {
            return notFound("layer not found");
        }

        WLayer layer = layerOpt.get();
        if (layer.getLayerType() != LayerType.MODEL) {
            return bad("operation only supported for MODEL layers");
        }

        try {
            // Ensure layerDataId exists
            if (layer.getLayerDataId() == null) {
                layer.setLayerDataId(UUID.randomUUID().toString());
                layer.touchUpdate();
                layerService.save(layer);
            }

            // Load or create model
            WLayerModel model = modelRepository.findFirstByLayerDataId(layer.getLayerDataId())
                    .orElseGet(() -> {
                        WLayerModel newModel = WLayerModel.builder()
                                .worldId(worldId)
                                .layerDataId(layer.getLayerDataId())
                                .content(new ArrayList<>())
                                .build();
                        newModel.touchCreate();
                        return newModel;
                    });

            // Convert request blocks to LayerBlocks
            List<LayerBlock> newBlocks = request.blocks().stream()
                    .map(b -> {
                        de.mhus.nimbus.generated.types.Vector3Int pos = new de.mhus.nimbus.generated.types.Vector3Int();
                        pos.setX(b.x());
                        pos.setY(b.y());
                        pos.setZ(b.z());

                        Block block = Block.builder()
                                .position(pos)
                                .blockTypeId(b.blockId())
                                .build();

                        return LayerBlock.builder()
                                .block(block)
                                .group(b.group() != null ? b.group() : 0)
                                .build();
                    })
                    .collect(Collectors.toList());

            // Add to existing blocks
            List<LayerBlock> allBlocks = new ArrayList<>(model.getContent());
            allBlocks.addAll(newBlocks);
            model.setContent(allBlocks);
            model.touchUpdate();

            modelRepository.save(model);

            log.info("MCP: Added {} blocks to layer: id={}", newBlocks.size(), layerId);
            return ResponseEntity.ok(Map.of(
                    "added", newBlocks.size(),
                    "total", allBlocks.size()
            ));
        } catch (Exception e) {
            log.error("MCP: Failed to add blocks to layer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add blocks: " + e.getMessage()));
        }
    }

    // ==================== BLOCK TYPE OPERATIONS ====================

    /**
     * Get block types for a world.
     * GET /control/mcp/worlds/{worldId}/blocktypes
     */
    @GetMapping("/worlds/{worldId}/blocktypes")
    @Operation(summary = "Get block types for a world")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> getBlockTypes(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Limit results") @RequestParam(defaultValue = "100") int limit) {

        log.debug("MCP: Get block types: worldId={}, limit={}", worldId, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        List<WBlockType> blockTypes = blockTypeService.findByWorldId(wid);
        List<Map<String, Object>> blockTypeDtos = blockTypes.stream()
                .limit(limit)
                .map(this::toBlockTypeDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "blockTypes", blockTypeDtos,
                "count", blockTypeDtos.size(),
                "total", blockTypes.size()
        ));
    }

    // ==================== HELPER METHODS & DTOS ====================

    private Map<String, Object> toWorldDto(WWorld world) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", world.getId());
        dto.put("worldId", world.getWorldId());
        dto.put("regionId", world.getRegionId());
        dto.put("enabled", world.isEnabled());
        if (world.getPublicData() != null) {
            dto.put("name", world.getPublicData().getName());
            dto.put("description", world.getPublicData().getDescription());
        }
        dto.put("createdAt", world.getCreatedAt());
        dto.put("updatedAt", world.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toLayerDto(WLayer layer) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", layer.getId());
        dto.put("worldId", layer.getWorldId());
        dto.put("name", layer.getName());
        dto.put("layerType", layer.getLayerType().name());
        dto.put("layerDataId", layer.getLayerDataId());
        // Note: mountX/Y/Z removed - these are now in WLayerModel
        // groups is available both in WLayer (for GROUND layers) and WLayerModel (for MODEL layers)
        dto.put("allChunks", layer.isAllChunks());
        dto.put("affectedChunks", layer.getAffectedChunks());
        dto.put("order", layer.getOrder());
        dto.put("enabled", layer.isEnabled());
        dto.put("baseGround", layer.isBaseGround());
        dto.put("groups", layer.getGroups());
        dto.put("createdAt", layer.getCreatedAt());
        dto.put("updatedAt", layer.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toLayerBlockDto(LayerBlock layerBlock) {
        Map<String, Object> dto = new HashMap<>();
        if (layerBlock.getBlock() != null) {
            Block block = layerBlock.getBlock();
            if (block.getPosition() != null) {
                dto.put("x", (int) block.getPosition().getX());
                dto.put("y", (int) block.getPosition().getY());
                dto.put("z", (int) block.getPosition().getZ());
            }
            dto.put("blockId", block.getBlockTypeId());
        }
        dto.put("group", layerBlock.getGroup());
        dto.put("metadata", layerBlock.getMetadata());
        return dto;
    }

    private Map<String, Object> toBlockTypeDto(WBlockType blockType) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("blockId", blockType.getBlockId());
        dto.put("enabled", blockType.isEnabled());
        if (blockType.getPublicData() != null) {
            dto.put("description", blockType.getPublicData().getDescription());
        }
        return dto;
    }

    // Request DTOs
    // CreateLayerRequest moved to de.mhus.nimbus.world.shared.dto package

    public record AddBlocksRequest(List<BlockRequest> blocks) {
    }

    public record BlockRequest(int x, int y, int z, String blockId, Integer group) {
    }
}
