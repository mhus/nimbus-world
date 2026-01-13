package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.WItemPosition;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for ItemPosition CRUD operations.
 * Base path: /control/worlds/{worldId}/item-positions
 * <p>
 * ItemPositions are placed items in the world with position and rendering data (ItemBlockRef).
 * They reference Items but have their own position in chunks.
 * Note: universeId is not used in this context (universe/region servers are not relevant here).
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/item-positions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "ItemPositions", description = "Item position management (placed items in world)")
public class EItemPositionController extends BaseEditorController {

    private final WItemPositionService itemRegistryService;

    // DTOs
    public record ItemPositionDto(
            String itemId,
            ItemBlockRef publicData,
            String worldId,
            String chunk,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateItemRequest(ItemBlockRef itemBlockRef) {
    }

    public record UpdateItemRequest(ItemBlockRef itemBlockRef) {
    }

    /**
     * Get single Item by ID.
     * GET /control/worlds/{worldId}/items/{itemId}
     */
    @GetMapping("/{itemId}")
    @Operation(summary = "Get Item by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Item identifier") @PathVariable String itemId) {

        log.debug("GET item: worldId={}, itemId={}", worldId, itemId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        var validation = validateId(itemId, "itemId");
        if (validation != null) return validation;

        // Use worldId as universeId (per user decision)
        Optional<WItemPosition> opt = itemRegistryService.findItem(wid, itemId);
        if (opt.isEmpty()) {
            log.warn("Item not found: worldId={}, itemId={}", worldId, itemId);
            return notFound("item not found");
        }

        log.debug("Returning item: itemId={}", itemId);
        // Return publicData only (match test_server format)
        return ResponseEntity.ok(opt.get().getPublicData());
    }

    /**
     * List/search Items for a world with optional chunk filter and pagination.
     * GET /control/worlds/{worldId}/items?query=...&cx=0&cz=0&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List/search Items")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Chunk X coordinate") @RequestParam(required = false) Integer cx,
            @Parameter(description = "Chunk Z coordinate") @RequestParam(required = false) Integer cz,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST items: worldId={}, query={}, cx={}, cz={}, offset={}, limit={}",
                worldId, query, cx, cz, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // Use worldId as universeId (per user decision)
        List<WItemPosition> all;

        // If chunk coordinates provided, filter by chunk
        if (cx != null && cz != null) {
            List<ItemBlockRef> chunkItems = itemRegistryService.getItemsInChunk(wid, cx, cz);
            // Convert ItemBlockRef back to WItemPosition for filtering (simplified approach)
            all = itemRegistryService.getAllItems(wid).stream()
                    .filter(item -> {
                        String chunkKey = BlockUtil.toChunkKey(cx, cz);
                        return chunkKey.equals(item.getChunk());
                    })
                    .collect(Collectors.toList());
        } else {
            all = itemRegistryService.getAllItems(wid);
        }

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        int totalCount = all.size();

        // Apply pagination
        List<ItemBlockRef> publicDataList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(WItemPosition::getPublicData)
                .collect(Collectors.toList());

        log.debug("Returning {} items (total: {})", publicDataList.size(), totalCount);

        // TypeScript compatible format (match test_server response)
        return ResponseEntity.ok(Map.of(
                "items", publicDataList,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Create new Item.
     * POST /control/worlds/{worldId}/items
     */
    @PostMapping
    @Operation(summary = "Create new Item")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Item already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateItemRequest request) {

        log.debug("CREATE item: worldId={}, itemId={}", worldId,
                request.itemBlockRef() != null ? request.itemBlockRef().getId() : "null");

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        if (request.itemBlockRef() == null) {
            return bad("itemBlockRef required");
        }

        ItemBlockRef itemBlockRef = request.itemBlockRef();

        if (blank(itemBlockRef.getId())) {
            return bad("itemBlockRef.id required");
        }

        if (itemBlockRef.getPosition() == null) {
            return bad("itemBlockRef.position required");
        }

        // Check if Item already exists
        // Use worldId as universeId (per user decision)
        if (itemRegistryService.findItem(wid, itemBlockRef.getId()).isPresent()) {
            return conflict("item already exists");
        }

        try {
            WItemPosition saved = itemRegistryService.saveItemPosition(wid, itemBlockRef);
            log.info("Created item: itemId={}, chunk={}", itemBlockRef.getId(), saved.getChunk());
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating item: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing Item.
     * PUT /control/worlds/{worldId}/items/{itemId}
     */
    @PutMapping("/{itemId}")
    @Operation(summary = "Update Item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Item identifier") @PathVariable String itemId,
            @RequestBody UpdateItemRequest request) {

        log.debug("UPDATE item: worldId={}, itemId={}", worldId, itemId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(itemId, "itemId");
        if (validation != null) return validation;

        if (request.itemBlockRef() == null) {
            return bad("itemBlockRef required for update");
        }

        // Ensure itemId in path matches itemBlockRef.id
        ItemBlockRef itemBlockRef = request.itemBlockRef();
        if (!itemId.equals(itemBlockRef.getId())) {
            // Force ID to match path parameter
            itemBlockRef = ItemBlockRef.builder()
                    .id(itemId)
                    .texture(itemBlockRef.getTexture())
                    .position(itemBlockRef.getPosition())
                    .scaleX(itemBlockRef.getScaleX())
                    .scaleY(itemBlockRef.getScaleY())
                    .offset(itemBlockRef.getOffset())
                    .amount(itemBlockRef.getAmount())
                    .build();
        }

        // Use worldId as universeId (per user decision)
        Optional<WItemPosition> existing = itemRegistryService.findItem(wid, itemId);
        if (existing.isEmpty()) {
            log.warn("Item not found for update: worldId={}, itemId={}", worldId, itemId);
            return notFound("item not found");
        }

        try {
            // Save will update if exists
            WItemPosition saved = itemRegistryService.saveItemPosition(wid, itemBlockRef);
            log.info("Updated item: itemId={}, chunk={}", itemId, saved.getChunk());
            return ResponseEntity.ok(toDto(saved));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error updating item: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error updating item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Delete Item.
     * DELETE /control/worlds/{worldId}/items/{itemId}
     */
    @DeleteMapping("/{itemId}")
    @Operation(summary = "Delete Item")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Item deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Item identifier") @PathVariable String itemId) {

        log.debug("DELETE item: worldId={}, itemId={}", worldId, itemId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(itemId, "itemId");
        if (validation != null) return validation;

        // Use worldId as universeId (per user decision)
        boolean deleted = itemRegistryService.deleteItemPosition(wid, itemId);
        if (!deleted) {
            log.warn("Item not found for deletion: worldId={}, itemId={}", worldId, itemId);
            return notFound("item not found");
        }

        log.info("Deleted item: itemId={}", itemId);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private ItemPositionDto toDto(WItemPosition item) {
        return new ItemPositionDto(
                item.getItemId(),
                item.getPublicData(),
                item.getWorldId(),
                item.getChunk(),
                item.isEnabled(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private List<WItemPosition> filterByQuery(List<WItemPosition> items, String query) {
        String lowerQuery = query.toLowerCase();
        return items.stream()
                .filter(item -> {
                    String itemId = item.getItemId();
                    ItemBlockRef publicData = item.getPublicData();
                    return (itemId != null && itemId.toLowerCase().contains(lowerQuery)) ||
                            (publicData != null && publicData.getTexture() != null &&
                                    publicData.getTexture().toLowerCase().contains(lowerQuery));
                })
                .collect(Collectors.toList());
    }
}
