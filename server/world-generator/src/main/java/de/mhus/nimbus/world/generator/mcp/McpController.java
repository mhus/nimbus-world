package de.mhus.nimbus.world.generator.mcp;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.dto.CreateLayerRequest;
import de.mhus.nimbus.world.shared.job.JobExecutorRegistry;
import de.mhus.nimbus.world.shared.job.JobStatus;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.job.WJobService;
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
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP (Model Context Protocol) REST API Controller.
 * Provides unified access to world control operations for MCP clients.
 * <p>
 * Base path: /generator/mcp
 */
@RestController
@RequestMapping("/generator/mcp")
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
    private final McpJobExecutor mcpJobExecutor;
    private final JobExecutorRegistry executorRegistry;
    private final WJobService jobService;
    private final de.mhus.nimbus.world.shared.world.SAssetService assetService;
    private final de.mhus.nimbus.world.shared.world.WDocumentService documentService;

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

        tools.add(createToolDescriptor(
                "get_block_type",
                "Get a specific block type by ID (supports collection prefix like 'm:sand')",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "blockId", Map.of(
                                "type", "string",
                                "description", "Block type ID, optionally with collection prefix (e.g., 'sand', 'm:sand', 'n:air')",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "create_cube_block_type",
                "Create a new cube block type with textures",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "blockTypeId", Map.of(
                                "type", "string",
                                "description", "Unique block type ID (e.g., 'stone', 'm:sand')",
                                "required", true
                        ),
                        "title", Map.of(
                                "type", "string",
                                "description", "Display name of the block type",
                                "required", true
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "Description of the block type",
                                "required", false
                        ),
                        "textures", Map.of(
                                "type", "object",
                                "description", "Texture definitions (ALL, TOP, BOTTOM, NORTH, SOUTH, EAST, WEST)",
                                "required", true
                        ),
                        "type", Map.of(
                                "type", "string",
                                "description", "Block type: GROUND, WATER, STRUCTURE, DECORATION, etc.",
                                "required", false,
                                "default", "BLOCK"
                        ),
                        "solid", Map.of(
                                "type", "boolean",
                                "description", "Whether the block is solid (cannot walk through)",
                                "required", false,
                                "default", true
                        ),
                        "autoJump", Map.of(
                                "type", "number",
                                "description", "Auto-jump height (useful for GROUND types, 0 = disabled)",
                                "required", false,
                                "default", 0.0
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "create_billboard_block_type",
                "Create a new billboard block type (flat sprite facing camera, ideal for plants)",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "blockTypeId", Map.of(
                                "type", "string",
                                "description", "Unique block type ID (e.g., 'grass', 'm:flower')",
                                "required", true
                        ),
                        "title", Map.of(
                                "type", "string",
                                "description", "Display name of the block type",
                                "required", true
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "Description of the block type",
                                "required", false
                        ),
                        "texture", Map.of(
                                "type", "object",
                                "description", "Single texture definition with path (e.g., {\"path\": \"m:textures/plants/grass.png\"})",
                                "required", true
                        ),
                        "type", Map.of(
                                "type", "string",
                                "description", "Block type: DECORATION, PLANT, etc.",
                                "required", false,
                                "default", "DECORATION"
                        ),
                        "solid", Map.of(
                                "type", "boolean",
                                "description", "Whether the block is solid (usually false for billboards)",
                                "required", false,
                                "default", false
                        ),
                        "autoJump", Map.of(
                                "type", "number",
                                "description", "Auto-jump height (usually 0 for billboards)",
                                "required", false,
                                "default", 0.0
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "search_readme",
                "Search README/HowTo documents by title or content",
                Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "Search query for title or content",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "get_readme",
                "Get a specific README/HowTo document by name",
                Map.of(
                        "name", Map.of(
                                "type", "string",
                                "description", "Document name (technical identifier)",
                                "required", true
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "search_assets",
                "Search assets by collection, file type and query",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID",
                                "required", true
                        ),
                        "collection", Map.of(
                                "type", "string",
                                "description", "Collection prefix: 'w' (World), 'r' (Region), 'rp' (Region Public), 'm' (Minecraft-like Shared), 'n' (Nimbus Shared), 'p' (Public Shared)",
                                "required", false,
                                "enum", List.of("w", "r", "rp", "m", "n", "p")
                        ),
                        "fileType", Map.of(
                                "type", "string",
                                "description", "File extension (e.g., 'png', 'jpg', 'json')",
                                "required", false
                        ),
                        "query", Map.of(
                                "type", "string",
                                "description", "Search query for asset path/name (e.g., 'sand', 'stone')",
                                "required", false
                        ),
                        "offset", Map.of(
                                "type", "number",
                                "description", "Pagination offset",
                                "required", false,
                                "default", 0
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

        // Job execution tools
        tools.add(createToolDescriptor(
                "execute_job_world_scoped",
                "Execute a job synchronously in a specific world (worldId in path)",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID in path",
                                "required", true
                        ),
                        "executor", Map.of(
                                "type", "string",
                                "description", "Executor name to use",
                                "required", true
                        ),
                        "layer", Map.of(
                                "type", "string",
                                "description", "Optional layer name",
                                "required", false
                        ),
                        "parameters", Map.of(
                                "type", "object",
                                "description", "Executor-specific parameters",
                                "required", false
                        ),
                        "timeoutSeconds", Map.of(
                                "type", "integer",
                                "description", "Timeout in seconds",
                                "required", false,
                                "default", 300
                        )
                )
        ));

        tools.add(createToolDescriptor(
                "execute_job_dynamic",
                "Execute a job synchronously with dynamic world selection (worldId in body)",
                Map.of(
                        "worldId", Map.of(
                                "type", "string",
                                "description", "World ID in request body",
                                "required", true
                        ),
                        "executor", Map.of(
                                "type", "string",
                                "description", "Executor name to use",
                                "required", true
                        ),
                        "layer", Map.of(
                                "type", "string",
                                "description", "Optional layer name",
                                "required", false
                        ),
                        "parameters", Map.of(
                                "type", "object",
                                "description", "Executor-specific parameters",
                                "required", false
                        ),
                        "timeoutSeconds", Map.of(
                                "type", "integer",
                                "description", "Timeout in seconds",
                                "required", false,
                                "default", 300
                        )
                )
        ));

        Map<String, Object> response = new HashMap<>();
        response.put("server", serverInfo);
        response.put("tools", tools);
        response.put("toolCount", tools.size());

        // Add REST API endpoints info
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("GET /generator/mcp", "Get this server info");
        endpoints.put("GET /generator/mcp/worlds", "List worlds");
        endpoints.put("GET /generator/mcp/worlds/{worldId}", "Get world");
        endpoints.put("GET /generator/mcp/worlds/{worldId}/blocktypes", "Get block types");
        endpoints.put("GET /generator/mcp/worlds/{worldId}/blocktypes/{blockId}", "Get specific block type");
        endpoints.put("POST /generator/mcp/worlds/{worldId}/blocktypes", "Create cube block type");
        endpoints.put("POST /generator/mcp/worlds/{worldId}/blocktypes/billboard", "Create billboard block type");
        endpoints.put("GET /generator/mcp/worlds/{worldId}/assets/search", "Search assets");
        endpoints.put("GET /generator/mcp/worlds/{worldId}/layers", "List layers");
        endpoints.put("GET /generator/mcp/worlds/{worldId}/layers/{layerId}", "Get layer");
        endpoints.put("POST /generator/mcp/worlds/{worldId}/layers", "Create layer");
        endpoints.put("GET /generator/mcp/worlds/{worldId}/layers/{layerId}/blocks", "Get layer blocks");
        endpoints.put("POST /generator/mcp/worlds/{worldId}/layers/{layerId}/blocks", "Add layer blocks");
        endpoints.put("POST /generator/mcp/worlds/{worldId}/jobs/execute", "Execute job synchronously (world-scoped)");
        endpoints.put("GET /generator/mcp/worlds/{worldId}/jobs/{jobId}/status", "Get job status (world-scoped)");
        endpoints.put("POST /generator/mcp/jobs/execute", "Execute job synchronously (dynamic world selection)");
        endpoints.put("GET /generator/mcp/jobs/{jobId}/status", "Get job status (dynamic)");
        endpoints.put("GET /generator/mcp/readme/search", "Search README documents");
        endpoints.put("GET /generator/mcp/readme/{name}", "Get specific README document");
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

        if (Strings.isBlank(request.name())) {
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
                                .group(b.group())
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

    // ==================== ASSET OPERATIONS ====================

    /**
     * Search assets by collection, file type and query.
     * Collections: 'w' (World), 'r' (Region), 'rp' (Region Public), 'm' (Minecraft-like Shared), 'n' (Nimbus Shared), 'p' (Public Shared).
     * GET /generator/mcp/worlds/{worldId}/assets/search
     */
    @GetMapping("/worlds/{worldId}/assets/search")
    @Operation(summary = "Search assets by collection, file type and query",
               description = "Search for assets in different collections. Collections: 'w' (World - direct to world), " +
                             "'r' (Region - bound to region), 'rp' (Region Public - available without login), " +
                             "'m' (Minecraft-like Shared - textures, BlockTypes, models), " +
                             "'n' (Nimbus Shared - basis textures, BlockTypes, models like n:0 Air or n:o Ocean Water), " +
                             "'p' (Public Shared - public assets without login)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> searchAssets(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Collection prefix: 'w', 'r', 'rp', 'm', 'n', 'p'") @RequestParam(required = false) String collection,
            @Parameter(description = "File extension (e.g., 'png', 'jpg', 'json')") @RequestParam(required = false) String fileType,
            @Parameter(description = "Search query for asset path/name (e.g., 'sand', 'stone')") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Maximum number of results") @RequestParam(defaultValue = "100") int limit) {

        log.debug("MCP: Search assets: worldId={}, collection={}, fileType={}, query={}, offset={}, limit={}",
                worldId, collection, fileType, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        // Combine collection and query - service will parse collection prefix using WorldCollection
        String searchQuery = query;
        if (Strings.isNotBlank(collection)) {
            searchQuery = Strings.isNotBlank(query) ? collection + ":" + query : collection + ":";
        }

        // Service uses WorldCollection internally to parse collection prefix from query
        var result = assetService.searchAssets(wid, searchQuery, fileType, offset, limit);

        List<Map<String, Object>> assetDtos = result.assets().stream()
                .map(this::toAssetDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "assets", assetDtos,
                "count", assetDtos.size(),
                "total", result.totalCount(),
                "offset", result.offset(),
                "limit", result.limit()
        ));
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

    /**
     * Get a specific block type by ID.
     * Supports collection prefix (e.g., 'm:sand', 'n:air').
     * GET /generator/mcp/worlds/{worldId}/blocktypes/{blockId}
     */
    @GetMapping("/worlds/{worldId}/blocktypes/{blockId}")
    @Operation(summary = "Get a specific block type by ID",
               description = "Get block type by ID. Supports collection prefix (e.g., 'm:sand' for Minecraft-like collection, " +
                             "'n:air' for Nimbus collection). Without prefix, searches in world collection.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Block type found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Block type not found")
    })
    public ResponseEntity<?> getBlockType(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Block type ID (e.g., 'sand', 'm:sand', 'n:air')") @PathVariable String blockId) {

        log.debug("MCP: Get block type: worldId={}, blockId={}", worldId, blockId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        // Service uses WorldCollection internally to parse collection prefix
        Optional<WBlockType> blockTypeOpt = blockTypeService.findByBlockId(wid, blockId);

        if (blockTypeOpt.isEmpty()) {
            return notFound("block type not found");
        }

        return ResponseEntity.ok(toBlockTypeDto(blockTypeOpt.get()));
    }

    /**
     * Create a new cube block type.
     * POST /generator/mcp/worlds/{worldId}/blocktypes
     */
    @PostMapping("/worlds/{worldId}/blocktypes")
    @Operation(summary = "Create a new cube block type",
               description = "Creates a cube block type with textures. Textures must be pre-selected or created. " +
                             "Supports collection prefix in blockTypeId (e.g., 'm:stone').")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Block type created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation failed"),
            @ApiResponse(responseCode = "409", description = "Block type ID already exists")
    })
    public ResponseEntity<?> createCubeBlockType(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @RequestBody CreateCubeBlockTypeRequest request) {

        log.debug("MCP: Create cube block type: worldId={}, blockTypeId={}", worldId, request.blockTypeId());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        // Validate required parameters
        if (Strings.isBlank(request.blockTypeId())) {
            return bad("blockTypeId is required");
        }
        if (Strings.isBlank(request.title())) {
            return bad("title is required");
        }
        if (request.textures() == null || request.textures().isEmpty()) {
            return bad("textures are required");
        }

        // Check if block type already exists
        Optional<WBlockType> existing = blockTypeService.findByBlockId(wid, request.blockTypeId());
        if (existing.isPresent()) {
            return conflict("block type with ID '" + request.blockTypeId() + "' already exists");
        }

        try {
            // Create BlockType DTO
            de.mhus.nimbus.generated.types.BlockType publicData = de.mhus.nimbus.generated.types.BlockType.builder()
                    .id(request.blockTypeId())
                    .title(request.title())
                    .description(request.description())
                    .type(parseBlockTypeType(request.type()))
                    .initialStatus(0)
                    .modifiers(new HashMap<>())
                    .build();

            // Create modifier '0' with CUBE shape
            de.mhus.nimbus.generated.types.BlockModifier modifier = de.mhus.nimbus.generated.types.BlockModifier.builder()
                    .visibility(de.mhus.nimbus.generated.types.VisibilityModifier.builder()
                            .shape(de.mhus.nimbus.generated.types.Shape.CUBE.getTsIndex())
                            .textures(request.textures())
                            .build())
                    .physics(de.mhus.nimbus.generated.types.PhysicsModifier.builder()
                            .solid(request.solid() != null ? request.solid() : true)
                            .autoJump(request.autoJump() != null ? request.autoJump() : 0.0)
                            .build())
                    .build();

            publicData.getModifiers().put(0, modifier);

            // Save using WBlockTypeService
            WBlockType saved = blockTypeService.save(wid, request.blockTypeId(), publicData);

            log.info("MCP: Created cube block type: id={}, blockTypeId={}", saved.getId(), saved.getBlockId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", saved.getId(),
                    "blockTypeId", saved.getBlockId(),
                    "worldId", saved.getWorldId()
            ));

        } catch (Exception e) {
            log.error("MCP: Failed to create cube block type", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create block type: " + e.getMessage()));
        }
    }

    /**
     * Create a new billboard block type.
     * POST /generator/mcp/worlds/{worldId}/blocktypes/billboard
     */
    @PostMapping("/worlds/{worldId}/blocktypes/billboard")
    @Operation(summary = "Create a new billboard block type",
               description = "Creates a billboard block type (flat sprite facing camera). Ideal for plants, grass, flowers. " +
                             "Texture is always transparent with back-face culling enabled. " +
                             "Supports collection prefix in blockTypeId (e.g., 'm:grass').")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Block type created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation failed"),
            @ApiResponse(responseCode = "409", description = "Block type ID already exists")
    })
    public ResponseEntity<?> createBillboardBlockType(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @RequestBody CreateBillboardBlockTypeRequest request) {

        log.debug("MCP: Create billboard block type: worldId={}, blockTypeId={}", worldId, request.blockTypeId());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        // Validate required parameters
        if (Strings.isBlank(request.blockTypeId())) {
            return bad("blockTypeId is required");
        }
        if (Strings.isBlank(request.title())) {
            return bad("title is required");
        }
        if (request.texture() == null) {
            return bad("texture is required");
        }

        // Check if block type already exists
        Optional<WBlockType> existing = blockTypeService.findByBlockId(wid, request.blockTypeId());
        if (existing.isPresent()) {
            return conflict("block type with ID '" + request.blockTypeId() + "' already exists");
        }

        try {
            // Create BlockType DTO
            de.mhus.nimbus.generated.types.BlockType publicData = de.mhus.nimbus.generated.types.BlockType.builder()
                    .id(request.blockTypeId())
                    .title(request.title())
                    .description(request.description())
                    .type(parseBlockTypeType(request.type() != null ? request.type() : "DECORATION"))
                    .initialStatus(0)
                    .modifiers(new HashMap<>())
                    .build();

            // Prepare texture with transparent=true and backFaceCulling=true
            Map<String, Object> textureConfig = new HashMap<>(request.texture());
            textureConfig.put("transparent", true);
            textureConfig.put("backFaceCulling", true);

            // Create textures map with single texture (key 0 = ALL)
            Map<Integer, Object> textures = new HashMap<>();
            textures.put(0, textureConfig);

            // Create modifier '0' with BILLBOARD shape
            de.mhus.nimbus.generated.types.BlockModifier modifier = de.mhus.nimbus.generated.types.BlockModifier.builder()
                    .visibility(de.mhus.nimbus.generated.types.VisibilityModifier.builder()
                            .shape(de.mhus.nimbus.generated.types.Shape.BILLBOARD.getTsIndex())
                            .textures(textures)
                            .build())
                    .physics(de.mhus.nimbus.generated.types.PhysicsModifier.builder()
                            .solid(request.solid() != null ? request.solid() : false)
                            .autoJump(request.autoJump() != null ? request.autoJump() : 0.0)
                            .build())
                    .build();

            publicData.getModifiers().put(0, modifier);

            // Save using WBlockTypeService
            WBlockType saved = blockTypeService.save(wid, request.blockTypeId(), publicData);

            log.info("MCP: Created billboard block type: id={}, blockTypeId={}", saved.getId(), saved.getBlockId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", saved.getId(),
                    "blockTypeId", saved.getBlockId(),
                    "worldId", saved.getWorldId()
            ));

        } catch (Exception e) {
            log.error("MCP: Failed to create billboard block type", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create block type: " + e.getMessage()));
        }
    }

    /**
     * Parse BlockTypeType from string.
     */
    private de.mhus.nimbus.generated.types.BlockTypeType parseBlockTypeType(String type) {
        if (type == null || type.isBlank()) {
            return de.mhus.nimbus.generated.types.BlockTypeType.BLOCK;
        }
        try {
            return de.mhus.nimbus.generated.types.BlockTypeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid block type: {}, using BLOCK as default", type);
            return de.mhus.nimbus.generated.types.BlockTypeType.BLOCK;
        }
    }

    // ==================== JOB EXECUTION OPERATIONS ====================

    /**
     * Execute job synchronously (world-scoped: worldId in path).
     * POST /generator/mcp/worlds/{worldId}/jobs/execute
     */
    @PostMapping("/worlds/{worldId}/jobs/execute")
    @Operation(summary = "Execute job synchronously (world-scoped)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job completed successfully"),
            @ApiResponse(responseCode = "202", description = "Job still running (timeout exceeded)"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Executor or world not found"),
            @ApiResponse(responseCode = "500", description = "Job execution failed")
    })
    public ResponseEntity<?> executeJobWorldScoped(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @RequestBody McpJobExecuteRequest request) {

        log.debug("MCP: Execute job (world-scoped): worldId={} executor={}", worldId, request.executor());

        // worldId from path overrides request body
        return executeJobInternal(worldId, request);
    }

    /**
     * Get job status (world-scoped: worldId in path).
     * GET /generator/mcp/worlds/{worldId}/jobs/{jobId}/status
     */
    @GetMapping("/worlds/{worldId}/jobs/{jobId}/status")
    @Operation(summary = "Get job status (world-scoped)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job status retrieved"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    public ResponseEntity<?> getJobStatusWorldScoped(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Job ID") @PathVariable String jobId) {

        log.debug("MCP: Get job status (world-scoped): worldId={} jobId={}", worldId, jobId);
        return getJobStatusInternal(jobId);
    }

    /**
     * Execute job synchronously (dynamic: worldId in request body).
     * POST /generator/mcp/jobs/execute
     */
    @PostMapping("/jobs/execute")
    @Operation(summary = "Execute job synchronously (dynamic world selection)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job completed successfully"),
            @ApiResponse(responseCode = "202", description = "Job still running (timeout exceeded)"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Executor or world not found"),
            @ApiResponse(responseCode = "500", description = "Job execution failed")
    })
    public ResponseEntity<?> executeJobDynamic(@RequestBody McpJobExecuteRequest request) {
        log.debug("MCP: Execute job (dynamic): worldId={} executor={}", request.worldId(), request.executor());

        // worldId must be in request body
        if (request.worldId() == null || request.worldId().isBlank()) {
            return bad("worldId is required in request body");
        }

        return executeJobInternal(request.worldId(), request);
    }

    /**
     * Get job status (dynamic).
     * GET /generator/mcp/jobs/{jobId}/status
     */
    @GetMapping("/jobs/{jobId}/status")
    @Operation(summary = "Get job status (dynamic)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job status retrieved"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    public ResponseEntity<?> getJobStatusDynamic(@Parameter(description = "Job ID") @PathVariable String jobId) {
        log.debug("MCP: Get job status (dynamic): jobId={}", jobId);
        return getJobStatusInternal(jobId);
    }

    /**
     * Internal implementation for job execution.
     */
    private ResponseEntity<?> executeJobInternal(String worldId, McpJobExecuteRequest request) {
        // Validate worldId
        WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("Invalid worldId: " + worldId)
        );

        // Validate executor exists
        if (!executorRegistry.hasExecutor(request.executor())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Executor not found: " + request.executor()));
        }

        // Calculate timeout
        long timeoutMs = 300000; // Default: 5 minutes
        if (request.timeoutSeconds() != null) {
            timeoutMs = request.timeoutSeconds() * 1000L;
        }

        // Validate timeout bounds
        if (timeoutMs > 600000) { // Max: 10 minutes
            return bad("timeout exceeds maximum of 600 seconds");
        }

        try {
            // Execute job
            McpJobExecutor.JobExecutionResult result = mcpJobExecutor.builder()
                    .worldId(worldId)
                    .layer(request.layer())
                    .executor(request.executor())
                    .parameters(request.parameters())
                    .timeout(timeoutMs)
                    .build(mcpJobExecutor)
                    .executeAndWait();

            // Handle result
            return switch (result.status()) {
                case SUCCESS -> ResponseEntity.ok(new McpJobExecuteResponse(
                        result.jobId(),
                        "COMPLETED",
                        result.result(),
                        null,
                        result.durationMs(),
                        result.startedAt(),
                        result.completedAt()
                ));
                case FAILURE -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new McpJobExecuteResponse(
                                result.jobId(),
                                "FAILED",
                                null,
                                result.error(),
                                result.durationMs(),
                                result.startedAt(),
                                result.completedAt()
                        ));
                case TIMEOUT -> {
                    // Should not happen as timeout throws exception
                    yield ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(new McpJobTimeoutResponse(
                                    result.jobId(),
                                    "RUNNING",
                                    "Job exceeded timeout",
                                    "/generator/mcp/jobs/" + result.jobId() + "/status"
                            ));
                }
            };

        } catch (McpJobTimeoutException e) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new McpJobTimeoutResponse(
                            e.getJobId(),
                            "RUNNING",
                            e.getMessage(),
                            "/generator/mcp/jobs/" + e.getJobId() + "/status"
                    ));

        } catch (McpJobException e) {
            log.error("MCP: Job execution failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Job execution failed: " + e.getMessage()));
        }
    }

    /**
     * Internal implementation for job status retrieval.
     */
    private ResponseEntity<?> getJobStatusInternal(String jobId) {
        Optional<WJob> jobOpt = jobService.getJob(jobId);

        if (jobOpt.isEmpty()) {
            return notFound("job not found");
        }

        WJob job = jobOpt.get();
        Long duration = null;
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            duration = job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli();
        }

        return ResponseEntity.ok(new McpJobExecuteResponse(
                job.getId(),
                job.getStatus(),
                job.getResult(),
                job.getErrorMessage(),
                duration,
                job.getStartedAt(),
                job.getCompletedAt()
        ));
    }

    // ==================== README/DOCUMENTATION OPERATIONS ====================

    /**
     * Search README/HowTo documents by title or content.
     * Searches in '@shared:n' world, collection 'mcp'.
     * GET /generator/mcp/readme/search
     */
    @GetMapping("/readme/search")
    @Operation(summary = "Search README/HowTo documents",
               description = "Search for README, HowTo and documentation documents by title or content. " +
                             "Documents are stored in the Nimbus shared collection (@shared:n) under collection 'mcp'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> searchReadme(
            @Parameter(description = "Search query for title or content") @RequestParam String query) {

        log.debug("MCP: Search readme: query={}", query);

        if (Strings.isBlank(query)) {
            return bad("query parameter is required");
        }

        try {
            // Use Nimbus shared collection '@shared:n'
            WorldId nimbusShared = WorldId.of(WorldId.COLLECTION_SHARED, "n")
                    .orElseThrow(() -> new IllegalStateException("Failed to create Nimbus shared worldId"));

            // Get all documents from 'mcp' collection
            List<de.mhus.nimbus.world.shared.world.WDocument> allDocs =
                    documentService.findByCollection(nimbusShared, "mcp");

            // Filter by title or content containing query (case-insensitive)
            String queryLower = query.toLowerCase();
            List<de.mhus.nimbus.world.shared.world.WDocument> filtered = allDocs.stream()
                    .filter(doc -> {
                        boolean matchTitle = doc.getTitle() != null &&
                                doc.getTitle().toLowerCase().contains(queryLower);
                        boolean matchContent = doc.getContent() != null &&
                                doc.getContent().toLowerCase().contains(queryLower);
                        return matchTitle || matchContent;
                    })
                    .collect(Collectors.toList());

            // Convert to DTOs (name, title, summary only)
            List<Map<String, Object>> results = filtered.stream()
                    .map(doc -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("name", doc.getName());
                        dto.put("title", doc.getTitle());
                        dto.put("summary", doc.getSummary());
                        return dto;
                    })
                    .collect(Collectors.toList());

            log.debug("MCP: Found {} documents matching query '{}'", results.size(), query);

            return ResponseEntity.ok(Map.of(
                    "documents", results,
                    "count", results.size(),
                    "query", query
            ));

        } catch (Exception e) {
            log.error("MCP: Failed to search readme documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to search documents: " + e.getMessage()));
        }
    }

    /**
     * Get a specific README/HowTo document by name.
     * Loads from '@shared:n' world, collection 'mcp'.
     * GET /generator/mcp/readme/{name}
     */
    @GetMapping("/readme/{name}")
    @Operation(summary = "Get specific README/HowTo document",
               description = "Get a specific README, HowTo or documentation document by its technical name. " +
                             "Returns the full document including content. " +
                             "Documents are stored in the Nimbus shared collection (@shared:n) under collection 'mcp'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<?> getReadme(
            @Parameter(description = "Document name (technical identifier)") @PathVariable String name) {

        log.debug("MCP: Get readme: name={}", name);

        if (Strings.isBlank(name)) {
            return bad("name parameter is required");
        }

        try {
            // Use Nimbus shared collection '@shared:n'
            WorldId nimbusShared = WorldId.of(WorldId.COLLECTION_SHARED, "n")
                    .orElseThrow(() -> new IllegalStateException("Failed to create Nimbus shared worldId"));

            // Find document by name
            Optional<de.mhus.nimbus.world.shared.world.WDocument> docOpt =
                    documentService.findByName(nimbusShared, "mcp", name);

            if (docOpt.isEmpty()) {
                return notFound("document not found: " + name);
            }

            de.mhus.nimbus.world.shared.world.WDocument doc = docOpt.get();

            // Return full document
            Map<String, Object> result = new HashMap<>();
            result.put("name", doc.getName());
            result.put("title", doc.getTitle());
            result.put("summary", doc.getSummary());
            result.put("content", doc.getContent());
            result.put("format", doc.getFormat());
            result.put("language", doc.getLanguage());
            result.put("type", doc.getType());
            result.put("createdAt", doc.getCreatedAt());
            result.put("updatedAt", doc.getUpdatedAt());

            log.debug("MCP: Found document: name={}, title={}", doc.getName(), doc.getTitle());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("MCP: Failed to get readme document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get document: " + e.getMessage()));
        }
    }

    // ==================== HELPER METHODS & DTOS ====================

    private Map<String, Object> toWorldDto(WWorld world) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", world.getId());
        dto.put("worldId", world.getWorldId());
        dto.put("regionId", world.getRegionId());
        dto.put("enabled", world.isEnabled());
        if (world.getPublicData() != null) {
            dto.put("name", world.getPublicData().getTitle());
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

    private Map<String, Object> toAssetDto(de.mhus.nimbus.world.shared.world.SAsset asset) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", asset.getId());
        dto.put("worldId", asset.getWorldId());
        dto.put("path", asset.getPath());
        dto.put("name", asset.getName());
        dto.put("size", asset.getSize());
        dto.put("enabled", asset.isEnabled());
        dto.put("compressed", asset.isCompressed());
        if (asset.getPublicData() != null) {
            dto.put("description", asset.getPublicData().getDescription());
            dto.put("mimeType", asset.getPublicData().getMimeType());
        }
        dto.put("createdAt", asset.getCreatedAt());
        dto.put("createdBy", asset.getCreatedBy());
        return dto;
    }

    // Request DTOs
    // CreateLayerRequest moved to de.mhus.nimbus.world.shared.dto package

    public record McpJobExecuteRequest(
        String executor,
        String worldId,
        String layer,
        Map<String, String> parameters,
        Integer timeoutSeconds
    ) {}

    public record McpJobExecuteResponse(
        String jobId,
        String status,
        String result,
        String error,
        Long durationMs,
        Instant startedAt,
        Instant completedAt
    ) {}

    public record McpJobTimeoutResponse(
        String jobId,
        String status,
        String message,
        String pollUrl
    ) {}

    public record AddBlocksRequest(List<BlockRequest> blocks) {
    }

    public record BlockRequest(int x, int y, int z, String blockId, String group) {
    }

    public record CreateCubeBlockTypeRequest(
            String blockTypeId,
            String title,
            String description,
            Map<Integer, Object> textures,
            String type,
            Boolean solid,
            Double autoJump
    ) {}

    public record CreateBillboardBlockTypeRequest(
            String blockTypeId,
            String title,
            String description,
            Map<String, Object> texture,
            String type,
            Boolean solid,
            Double autoJump
    ) {}
}
