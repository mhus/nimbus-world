package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for Item CRUD operations.
 * Items are inventory/template objects without position (reusable across worlds).
 * For placed items with position, see EItemPositionController.
 * <p>
 * Endpoints:
 * - GET /control/worlds/{worldId}/items - Search items
 * - GET /control/worlds/{worldId}/item/{itemId} - Get single item
 * - POST /control/worlds/{worldId}/items - Create item
 * - PUT /control/worlds/{worldId}/item/{itemId} - Update item
 * - DELETE /control/worlds/{worldId}/item/{itemId} - Delete item
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Items", description = "Item management (inventory/template items)")
public class EItemController extends BaseEditorController {

    private final WItemService itemService;

    // DTOs
    public record ItemSearchResult(
            String itemId,
            String name,
            String texture
    ) {
    }

    public record CreateItemRequest(
            String id,
            String itemType,
            String name,
            String description,
            de.mhus.nimbus.generated.types.ItemModifier modifier,
            java.util.Map<String, Object> parameters
    ) {
    }

    public record UpdateItemRequest(
            String itemType,
            String name,
            String description,
            de.mhus.nimbus.generated.types.ItemModifier modifier,
            java.util.Map<String, Object> parameters
    ) {
    }

    /**
     * Search items (max 100 results).
     * GET /control/worlds/{worldId}/items?query={searchTerm}
     */
    @GetMapping("/control/worlds/{worldId}/items")
    @Operation(summary = "Search items")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> search(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false, defaultValue = "") String query) {

        log.debug("SEARCH items: worldId={}, query={}", worldId, query);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("invalid worldId")
        );
        final int maxResults = 100;

        List<WItem> all = itemService.findEnabledByWorldIdAndQuery(wid, query);

        List<ItemSearchResult> results = all.stream()
                .limit(maxResults)
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        log.debug("Returning {} items", results.size());
        return ResponseEntity.ok(Map.of("items", results));
    }

    /**
     * Get full item data.
     * GET /control/worlds/{worldId}/item/{itemId}
     */
    @GetMapping("/control/worlds/{worldId}/item/{itemId}")
    @Operation(summary = "Get item by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item found"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Item identifier") @PathVariable String itemId) {

        log.debug("GET item: worldId={}, itemId={}", worldId, itemId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("invalid worldId")
        );
        var validation = validateId(itemId, "itemId");
        if (validation != null) return validation;

        Optional<WItem> opt = itemService.findByItemId(wid, itemId);
        if (opt.isEmpty()) {
            log.warn("Item not found: worldId={}, itemId={}", worldId, itemId);
            return notFound("item not found");
        }

        log.debug("Returning item: itemId={}", itemId);
        // Return full WItem entity with metadata
        return ResponseEntity.ok(opt.get());
    }

    /**
     * Create a new item.
     * POST /control/worlds/{worldId}/items
     * Body: Item object
     */
    @PostMapping("/control/worlds/{worldId}/items")
    @Operation(summary = "Create new item")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Item already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateItemRequest request) {

        log.debug("CREATE item: worldId={}, itemId={}", worldId, request.id());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("invalid worldId")
        );
        if (blank(request.name())) {
            return bad("name is required");
        }

        if (blank(request.itemType())) {
            return bad("itemType is required");
        }

        // Generate ID if not provided
        String itemId = request.id();
        if (blank(itemId)) {
            itemId = "item_" + System.currentTimeMillis() + "_" +
                    Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 7);
        }

        // Check if already exists
        if (itemService.findByItemId(wid, itemId).isPresent()) {
            return conflict("item already exists");
        }

        try {
            // Build Item DTO
            Item item = Item.builder()
                    .id(itemId)
                    .itemType(request.itemType())
                    .name(request.name())
                    .description(request.description())
                    .modifier(request.modifier())
                    .parameters(request.parameters())
                    .build();

            WItem saved = itemService.save(wid, itemId, item);
            log.info("Created item: itemId={}", itemId);

            // Return full WItem entity with metadata
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
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
     * Update an existing item.
     * PUT /control/worlds/{worldId}/item/{itemId}
     * Body: Partial Item object
     */
    @PutMapping("/control/worlds/{worldId}/item/{itemId}")
    @Operation(summary = "Update item")
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
                () -> new IllegalArgumentException("invalid worldId")
        );
        var validation = validateId(itemId, "itemId");
        if (validation != null) return validation;

        Optional<WItem> existing = itemService.findByItemId(wid, itemId);
        if (existing.isEmpty()) {
            log.warn("Item not found for update: worldId={}, itemId={}", worldId, itemId);
            return notFound("item not found");
        }

        try {
            // Merge updates with existing item
            Item existingData = existing.get().getPublicData();
            Item updatedItem = Item.builder()
                    .id(itemId) // Ensure ID stays the same
                    .itemType(request.itemType() != null ? request.itemType() : existingData.getItemType())
                    .name(request.name() != null ? request.name() : existingData.getName())
                    .description(request.description() != null ? request.description() : existingData.getDescription())
                    .modifier(request.modifier() != null ? request.modifier() : existingData.getModifier())
                    .parameters(request.parameters() != null ? request.parameters() : existingData.getParameters())
                    .build();

            Optional<WItem> updated = itemService.update(wid, itemId, updatedItem);
            if (updated.isEmpty()) {
                return notFound("item disappeared during update");
            }

            log.info("Updated item: itemId={}", itemId);
            // Return full WItem entity with metadata
            return ResponseEntity.ok(updated.get());
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
     * Delete an item.
     * DELETE /control/worlds/{worldId}/item/{itemId}
     */
    @DeleteMapping("/control/worlds/{worldId}/item/{itemId}")
    @Operation(summary = "Delete item")
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
                () -> new IllegalArgumentException("invalid worldId")
        );
        var validation = validateId(itemId, "itemId");
        if (validation != null) return validation;

        boolean deleted = itemService.delete(wid, itemId);
        if (!deleted) {
            log.warn("Item not found for deletion: worldId={}, itemId={}", worldId, itemId);
            return notFound("item not found");
        }
        log.info("Deleted item: itemId={}", itemId);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private ItemSearchResult toSearchResult(WItem item) {
        Item publicData = item.getPublicData();
        if (publicData == null) {
            return new ItemSearchResult(item.getItemId(), item.getItemId(), null);
        }

        // Extract texture from modifier
        String texture = publicData.getModifier() != null ? publicData.getModifier().getTexture() : null;

        return new ItemSearchResult(
                publicData.getId(),
                publicData.getName() != null ? publicData.getName() : publicData.getId(),
                texture
        );
    }
}
