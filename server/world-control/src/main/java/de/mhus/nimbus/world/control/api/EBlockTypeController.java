package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.BlockType;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import de.mhus.nimbus.world.shared.world.WorldCollection;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.mhus.nimbus.world.shared.world.BlockUtil.extractGroupFromBlockId;

/**
 * REST Controller for BlockType CRUD operations.
 * Base path: /control/worlds/{worldId}/blocktypes
 * <p>
 * BlockTypes are templates that define how blocks look and behave.
 * BlockType IDs have the format {group}:{key} (e.g., "core:stone", "w:123").
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/blocktypes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "BlockTypes", description = "BlockType template management")
public class EBlockTypeController extends BaseEditorController {

    private final WBlockTypeService blockTypeService;
    private final de.mhus.nimbus.shared.engine.EngineMapper engineMapper;

    // DTOs
    public record BlockTypeDto(
            String blockId,
            BlockType publicData,
            String worldId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateBlockTypeRequest(String blockId, BlockType publicData, String blockTypeGroup) {
    }

    public record UpdateBlockTypeRequest(BlockType publicData, String blockTypeGroup, Boolean enabled) {
    }

    /**
     * Get single BlockType by ID.
     * GET /control/worlds/{worldId}/blocktypes/type/{blockId}
     */
    @GetMapping("/type/{*blockId}")
    @Operation(summary = "Get BlockType by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "BlockType found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "BlockType not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Block identifier") @PathVariable String blockId) {

        // Strip leading slash from wildcard pattern {*blockId}
        if (blockId != null && blockId.startsWith("/")) {
            blockId = blockId.substring(1);
        }

        // Extract ID from format "w/310" -> "310" or "310" -> "310"
        // In DB: blockId stores only the number, blockTypeGroup stores "w"
        if (blockId != null && blockId.contains("/")) {
            String[] parts = blockId.split("/", 2);
            if (parts.length == 2) {
                blockId = parts[1];  // Use the ID part after the slash
            }
        }

        log.debug("GET blocktype: worldId={}, blockId={}", worldId, blockId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(blockId, "blockId");
        if (validation != null) return validation;

        Optional<WBlockType> opt = blockTypeService.findByBlockId(wid, blockId);
        if (opt.isEmpty()) {
            log.warn("BlockType not found: blockId={}", blockId);
            return notFound("blocktype not found");
        }

        log.debug("Returning blocktype: blockId={}", blockId);
        // Return publicData with full ID (e.g., "r:wfr" not just "wfr")
        return ResponseEntity.ok(opt.get().getPublicDataWithFullId());
    }

    /**
     * List all BlockTypes for a world with optional search filter and pagination.
     * GET /control/worlds/{worldId}/blocktypes?query=...&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List all BlockTypes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST blocktypes: worldId={}, query={}, offset={}, limit={}", worldId, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // Get all BlockTypes for this world
        List<WBlockType> all = blockTypeService.findByWorldIdAndQuery(wid, query);

        int totalCount = all.size();

        // Apply pagination and return with full IDs (e.g., "r:wfr" not just "wfr")
        List<BlockType> publicDataList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(WBlockType::getPublicDataWithFullId)
                .collect(Collectors.toList());

        log.debug("Returning {} blocktypes (total: {})", publicDataList.size(), totalCount);

        // TypeScript compatible format (match test_server response)
        return ResponseEntity.ok(Map.of(
                "blockTypes", publicDataList,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Get BlockTypes by group.
     * GET /control/worlds/{worldId}/blocktypeschunk/{groupName}
     * <p>
     * This is a special endpoint to load BlockTypes grouped by their group prefix
     * (e.g., "core" for "core:stone", "w" for "w/123").
     */
    @GetMapping("../blocktypeschunk/{groupName}")
    @Operation(summary = "Get BlockTypes by group")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> getByGroup(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "BlockType group name") @PathVariable String groupName) {

        log.debug("GET blocktypes by group: worldId={}, groupName={}", worldId, groupName);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(groupName)) {
            return bad("groupName required");
        }

        // Validate group name format (lowercase alphanumeric, dash, underscore)
        if (!groupName.matches("^[a-z0-9_-]+$")) {
            return bad("groupName must be lowercase alphanumeric with dash or underscore");
        }

        List<WBlockType> blockTypes = blockTypeService.findByBlockTypeGroup(wid, groupName);

        // Map to DTOs with full IDs (e.g., "r:wfr" not just "wfr")
        List<BlockType> publicDataList = blockTypes.stream()
                .map(WBlockType::getPublicDataWithFullId)
                .collect(Collectors.toList());

        log.debug("Returning {} blocktypes for group: {}", publicDataList.size(), groupName);

        // Return array directly (TypeScript test_server format)
        return ResponseEntity.ok(publicDataList);
    }

    /**
     * Create new BlockType.
     * POST /control/worlds/{worldId}/blocktypes/type
     */
    @PostMapping("/type")
    @Operation(summary = "Create new BlockType")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "BlockType created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "BlockType already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateBlockTypeRequest request) {

        log.debug("CREATE blocktype: worldId={}, blockId={}", worldId, request.blockId());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(request.blockId())) {
            return bad("blockId required");
        }

        if (request.publicData() == null) {
            return bad("publicData required");
        }

        // Check if BlockType already exists
        if (blockTypeService.findByBlockId(wid, request.blockId()).isPresent()) {
            return conflict("blocktype already exists");
        }

        try {
            // Extract or set blockTypeGroup
            final String blockTypeGroup = blank(request.blockTypeGroup())
                    ? extractGroupFromBlockId(request.blockId())
                    : request.blockTypeGroup();

            WBlockType saved = blockTypeService.save(wid, request.blockId(), request.publicData());

            // Reload to get updated entity
            saved = blockTypeService.findByBlockId(wid, request.blockId()).orElse(saved);

            log.info("Created blocktype: blockId={}, group={}", request.blockId(), blockTypeGroup);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("blockId", saved.getBlockId()));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating blocktype: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating blocktype", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing BlockType.
     * PUT /control/worlds/{worldId}/blocktypes/type/{blockId}
     */
    @PutMapping("/type/{*blockId}")
    @Operation(summary = "Update BlockType")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "BlockType updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "BlockType not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Block identifier") @PathVariable String blockId,
            @RequestBody UpdateBlockTypeRequest request) {

        // Strip leading slash from wildcard pattern {*blockId}
        if (blockId != null && blockId.startsWith("/")) {
            blockId = blockId.substring(1);
        }

        // Extract ID from format "w/310" -> "310" or "310" -> "310"
        if (blockId != null && blockId.contains("/")) {
            String[] parts = blockId.split("/", 2);
            if (parts.length == 2) {
                blockId = parts[1];
            }
        }

        log.debug("UPDATE blocktype: worldId={}, blockId={}", worldId, blockId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(blockId, "blockId");
        if (validation != null) return validation;

        if (request.publicData() == null && request.blockTypeGroup() == null && request.enabled() == null) {
            return bad("at least one field required for update");
        }

        // Find the BlockType first (with COW fallback for external collections)
        Optional<WBlockType> existing = blockTypeService.findByBlockId(wid, blockId);
        if (existing.isEmpty()) {
            log.warn("BlockType not found for update: blockId={}", blockId);
            return notFound("blocktype not found");
        }

        // Use the actual worldId from the existing entity (important for external collections)
        String actualWorldId = existing.get().getWorldId();
        WorldId actualWid = WorldId.of(actualWorldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId in entity: " + actualWorldId)
        );

        final String finalBlockId = blockId;
        Optional<WBlockType> updated = blockTypeService.update(actualWid, blockId, blockType -> {
            if (request.publicData() != null) {
                // Ensure publicData.id has full blockId with prefix (e.g., "r:wfr" not just "wfr")
                BlockType publicData = request.publicData();
                var collection = WorldCollection.of(actualWid, finalBlockId);
                String fullBlockId = collection.typeString() + ":" + collection.path();

                // Create a deep copy with corrected ID (important for MongoDB to detect change)
                try {
                    BlockType correctedPublicData = engineMapper.readValue(
                        engineMapper.writeValueAsString(publicData),
                        BlockType.class
                    );
                    correctedPublicData.setId(fullBlockId);
                    blockType.setPublicData(correctedPublicData);
                } catch (Exception e) {
                    log.error("Failed to clone publicData", e);
                    publicData.setId(fullBlockId);
                    blockType.setPublicData(publicData);
                }
            }
            if (request.enabled() != null) {
                blockType.setEnabled(request.enabled());
            }
            // Note: Do NOT change worldId - it should remain the same
        });

        if (updated.isEmpty()) {
            log.warn("BlockType not found for update: blockId={}", blockId);
            return notFound("blocktype not found");
        }

        log.info("Updated blocktype: blockId={}", blockId);
        return ResponseEntity.ok(toDto(updated.get()));
    }

    /**
     * Delete BlockType.
     * DELETE /control/worlds/{worldId}/blocktypes/type/{blockId}
     */
    @DeleteMapping("/type/{*blockId}")
    @Operation(summary = "Delete BlockType")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "BlockType deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "BlockType not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Block identifier") @PathVariable String blockId) {

        // Strip leading slash from wildcard pattern {*blockId}
        if (blockId != null && blockId.startsWith("/")) {
            blockId = blockId.substring(1);
        }

        // Extract ID from format "w/310" -> "310" or "310" -> "310"
        if (blockId != null && blockId.contains("/")) {
            String[] parts = blockId.split("/", 2);
            if (parts.length == 2) {
                blockId = parts[1];
            }
        }

        log.debug("DELETE blocktype: worldId={}, blockId={}", worldId, blockId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(blockId, "blockId");
        if (validation != null) return validation;

        boolean deleted = blockTypeService.delete(wid, blockId);
        if (!deleted) {
            log.warn("BlockType not found for deletion: blockId={}", blockId);
            return notFound("blocktype not found");
        }

        log.info("Deleted blocktype: blockId={}", blockId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Duplicate BlockType with a new ID.
     * POST /control/worlds/{worldId}/blocktypes/duplicate/{sourceBlockId}
     * Body: { "newBlockId": "..." }
     *
     * Creates a copy of an existing BlockType with a new ID.
     */
    @PostMapping("/duplicate/{*sourceBlockId}")
    @Operation(summary = "Duplicate BlockType",
               description = "Creates a copy of an existing BlockType with a new ID")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "BlockType duplicated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Source BlockType not found"),
            @ApiResponse(responseCode = "409", description = "New BlockType ID already exists")
    })
    public ResponseEntity<?> duplicate(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Source BlockType identifier") @PathVariable String sourceBlockId,
            @RequestBody Map<String, String> body) {

        String newBlockId = body.get("newBlockId");

        // Strip leading slash from wildcard pattern
        if (sourceBlockId != null && sourceBlockId.startsWith("/")) {
            sourceBlockId = sourceBlockId.substring(1);
        }

        // Extract ID from format "w/310" -> "310" or "310" -> "310"
        if (sourceBlockId != null && sourceBlockId.contains("/")) {
            String[] parts = sourceBlockId.split("/", 2);
            if (parts.length == 2) {
                sourceBlockId = parts[1];
            }
        }
        if (newBlockId != null && newBlockId.contains("/")) {
            String[] parts = newBlockId.split("/", 2);
            if (parts.length == 2) {
                newBlockId = parts[1];
            }
        }

        log.debug("DUPLICATE blocktype: worldId={}, sourceBlockId={}, newBlockId={}",
                  worldId, sourceBlockId, newBlockId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sourceBlockId, "sourceBlockId");
        if (validation != null) return validation;

        validation = validateId(newBlockId, "newBlockId");
        if (validation != null) return validation;

        if (sourceBlockId.equals(newBlockId)) {
            return bad("sourceBlockId and newBlockId must be different");
        }

        // Check if source BlockType exists
        Optional<WBlockType> sourceOpt = blockTypeService.findByBlockId(wid, sourceBlockId);
        if (sourceOpt.isEmpty()) {
            log.warn("Source BlockType not found for duplication: blockId={}", sourceBlockId);
            return notFound("source blocktype not found");
        }

        // Check if new BlockType ID already exists
        if (blockTypeService.findByBlockId(wid, newBlockId).isPresent()) {
            return conflict("blocktype already exists with id: " + newBlockId);
        }

        try {
            WBlockType source = sourceOpt.get();

            // Create a deep copy of the publicData
            BlockType sourcePublicData = source.getPublicData();
            BlockType newPublicData = engineMapper.readValue(
                engineMapper.writeValueAsString(sourcePublicData),
                BlockType.class
            );

            // Set the new ID
            newPublicData.setId(newBlockId);

            // Update description to indicate it's a copy
            String originalDescription = newPublicData.getDescription() != null
                    ? newPublicData.getDescription()
                    : "";
            newPublicData.setDescription(originalDescription + " (Copy)");

            // Extract blockTypeGroup from newBlockId
            String blockTypeGroup = extractGroupFromBlockId(newBlockId);

            // Save the new BlockType
            WBlockType saved = blockTypeService.save(wid, newBlockId, newPublicData);

            // Set the blockTypeGroup and enabled state
            blockTypeService.update(wid, newBlockId, blockType -> {
                blockType.setEnabled(source.isEnabled());
            });

            // Reload to get updated entity
            saved = blockTypeService.findByBlockId(wid, newBlockId).orElse(saved);

            log.info("Duplicated blocktype: sourceBlockId={}, newBlockId={}, group={}",
                     sourceBlockId, newBlockId, blockTypeGroup);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "blockId", saved.getBlockId(),
                    "message", "BlockType duplicated successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to duplicate blocktype", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to duplicate blocktype: " + e.getMessage()));
        }
    }

    /**
     * Create BlockType from custom Block instance.
     * POST /control/worlds/{worldId}/blocktypes/fromBlock/{blockTypeId}
     *
     * Converts a custom Block (JSON payload) into a BlockType template.
     * The blockTypeId is provided in the URL path.
     */
    @PostMapping("/fromBlock/{*blockTypeId}")
    @Operation(summary = "Create BlockType from custom Block",
               description = "Converts a custom Block instance into a reusable BlockType template")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "BlockType created from Block"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "BlockType already exists")
    })
    public ResponseEntity<?> createFromBlock(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "New BlockType identifier") @PathVariable String blockTypeId,
            @RequestBody Map<String, Object> blockPayload) {

        // Strip leading slash from wildcard pattern {*blockTypeId}
        if (blockTypeId != null && blockTypeId.startsWith("/")) {
            blockTypeId = blockTypeId.substring(1);
        }

        log.debug("CREATE blocktype from block: worldId={}, blockTypeId={}", worldId, blockTypeId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(blockTypeId)) {
            return bad("blockTypeId required");
        }

        if (blockPayload == null || blockPayload.isEmpty()) {
            return bad("block payload required");
        }

        // Check if BlockType already exists
        if (blockTypeService.findByBlockId(wid, blockTypeId).isPresent()) {
            return conflict("blocktype already exists with id: " + blockTypeId);
        }

        try {
            // Convert Block to BlockType
            BlockType blockType = convertBlockToBlockType(blockPayload);

            // Set the ID in the BlockType
            blockType.setId(blockTypeId);

            // Extract blockTypeGroup from blockTypeId (e.g., "custom" from "custom:stone" or "w" from "w/123")
            String blockTypeGroup = extractGroupFromBlockId(blockTypeId);

            // Save BlockType
            WBlockType saved = blockTypeService.save(wid, blockTypeId, blockType);

            // Reload to get updated entity
            saved = blockTypeService.findByBlockId(wid, blockTypeId).orElse(saved);

            log.info("Created blocktype from block: blockTypeId={}, group={}", blockTypeId, blockTypeGroup);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "blockId", saved.getBlockId(),
                    "message", "BlockType created successfully"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating blocktype from block: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating blocktype from block", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Converts a custom Block (from JSON) into a BlockType.
     * Extracts modifiers, visibility settings, and other properties from the Block.
     */
    private BlockType convertBlockToBlockType(Map<String, Object> blockPayload) {
        try {
            // First, deserialize the blockPayload to a Block object to validate structure
            de.mhus.nimbus.generated.types.Block block = engineMapper.readValue(
                    engineMapper.writeValueAsString(blockPayload),
                    de.mhus.nimbus.generated.types.Block.class
            );

            // Create new BlockType from Block data
            BlockType blockType = new BlockType();

            // Set default description
            blockType.setDescription("Custom block converted to BlockType");

            // Copy modifiers - this is the main content we want to preserve
            if (block.getModifiers() != null && !block.getModifiers().isEmpty()) {
                blockType.setModifiers(block.getModifiers());
            }

            // Set initialStatus from Block's status if present
            if (block.getStatus() != 0) {
                blockType.setInitialStatus(block.getStatus());
            }

            log.debug("Converted Block to BlockType: {} modifiers",
                     blockType.getModifiers() != null ? blockType.getModifiers().size() : 0);

            return blockType;
        } catch (Exception e) {
            log.error("Failed to convert Block to BlockType", e);
            throw new IllegalArgumentException("Invalid block structure: " + e.getMessage(), e);
        }
    }

    // Helper methods

    private BlockTypeDto toDto(WBlockType entity) {
        return new BlockTypeDto(
                entity.getBlockId(),
                entity.getPublicDataWithFullId(),
                entity.getWorldId(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }


}
