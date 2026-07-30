package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.access.RequireWorldRole;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.ItemTier;
import de.mhus.nimbus.world.shared.world.RarityCategory;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.ResponseEntity.badRequest;

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
@RequireWorldRole(WorldRoles.EDITOR)
public class EItemController extends BaseEditorController {

    private final WItemService itemService;

    // DTOs
    public record ItemSearchResult(
            String itemId,
            String itemType,
            String type,
            String title,
            String texture
    ) {
    }

    public record CreateItemRequest(
            String id,
            String itemType,
            String type,
            String name,
            String description,
            String texture,
            Double scaleX,
            Double scaleY,
            String pose,
            Boolean exclusive,
            Boolean generic,
            java.util.Map<String, String> parameters,
            java.util.Map<String, String> server
    ) {
    }

    public record UpdateItemRequest(
            String itemType,
            String type,
            String title,
            String description,
            String texture,
            Double scaleX,
            Double scaleY,
            String pose,
            Boolean exclusive,
            Boolean generic,
            java.util.Map<String, String> parameters,
            java.util.Map<String, String> server,
            // Trading/price fields
            String itemTier,
            String rarityCategory,
            Double basePrice,
            Double materialPrice,
            Double craftingCost,
            Double usageBonus,
            Double rarityBonus
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

        List<WItem> limited = all.stream().limit(maxResults).collect(Collectors.toList());

        List<ItemSearchResult> results = limited.stream()
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
        if (Strings.isBlank(request.name())) {
            return bad("title is required");
        }

        if (Strings.isBlank(request.itemType())) {
            return bad("itemType is required");
        }

        try {
            // Build Item DTO
            Item item = Item.builder()
                    .itemType(request.itemType())
                    .type(request.type())
                    .name(request.name())
                    .description(request.description())
                    .texture(request.texture())
                    .scaleX(request.scaleX())
                    .scaleY(request.scaleY())
                    .pose(request.pose())
                    .exclusive(request.exclusive())
                    .generic(request.generic())
                    .parameters(request.parameters())
                    .build();

            WItem saved = itemService.create(wid, item);
            if (request.server() != null) {
                saved.setServer(request.server());
                itemService.saveEntity(saved);
            }
            log.info("Created item: itemId={}", saved.getName());

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
                    .name(itemId) // Ensure ID stays the same
                    .itemType(request.itemType() != null ? request.itemType() : existingData.getItemType())
                    .type(request.type() != null ? request.type() : existingData.getType())
                    .title(request.title() != null ? request.title() : existingData.getTitle())
                    .description(request.description() != null ? request.description() : existingData.getDescription())
                    .texture(request.texture() != null ? request.texture() : existingData.getTexture())
                    .scaleX(request.scaleX() != null ? request.scaleX() : existingData.getScaleX())
                    .scaleY(request.scaleY() != null ? request.scaleY() : existingData.getScaleY())
                    .pose(request.pose() != null ? request.pose() : existingData.getPose())
                    .exclusive(request.exclusive() != null ? request.exclusive() : existingData.getExclusive())
                    .generic(request.generic() != null ? request.generic() : existingData.getGeneric())
                    .parameters(request.parameters() != null ? request.parameters() : existingData.getParameters())
                    .build();

            Optional<WItem> updated = itemService.update(wid, itemId, updatedItem);
            if (updated.isEmpty()) {
                return notFound("item disappeared during update");
            }

            WItem result = updated.get();
            boolean needsSave = false;
            if (request.server() != null) {
                result.setServer(request.server());
                needsSave = true;
            }
            // Trading/price fields
            if (!Strings.isBlank(request.itemTier())) {
                result.setItemTier(ItemTier.valueOf(request.itemTier().toUpperCase().trim()));
                needsSave = true;
            }
            if (!Strings.isBlank(request.rarityCategory())) {
                result.setRarityCategory(RarityCategory.valueOf(request.rarityCategory().toUpperCase().trim()));
                needsSave = true;
            }
            if (request.basePrice() != null) { result.setBasePrice(request.basePrice()); needsSave = true; }
            if (request.materialPrice() != null) { result.setMaterialPrice(request.materialPrice()); needsSave = true; }
            if (request.craftingCost() != null) { result.setCraftingCost(request.craftingCost()); needsSave = true; }
            if (request.usageBonus() != null) { result.setUsageBonus(request.usageBonus()); needsSave = true; }
            if (request.rarityBonus() != null) { result.setRarityBonus(request.rarityBonus()); needsSave = true; }
            if (needsSave) {
                result = itemService.saveEntity(result);
            }

            log.info("Updated item: itemId={}", itemId);
            // Return full WItem entity with metadata
            return ResponseEntity.ok(result);
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
     * Duplicate an existing item.
     * POST /control/worlds/{worldId}/item/{itemId}/duplicate
     */
    public record DuplicateItemRequest(String name) {
    }

    @PostMapping("/control/worlds/{worldId}/item/{itemId}/duplicate")
    @Operation(summary = "Duplicate item")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item duplicated"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<?> duplicate(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Item identifier") @PathVariable String itemId,
            @RequestBody(required = false) DuplicateItemRequest request) {

        log.debug("DUPLICATE item: worldId={}, itemId={}", worldId, itemId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("invalid worldId")
        );
        var validation = validateId(itemId, "itemId");
        if (validation != null) return validation;

        String newName = request != null ? request.name() : null;
        if (Strings.isBlank(newName)) {
            return badRequest().body(Map.of("error", "name is required for duplication"));
        }

        try {
            WItem duplicated = itemService.duplicate(wid, itemId, newName);
            log.info("Duplicated item: {} -> {}", itemId, duplicated.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(duplicated);
        } catch (IllegalArgumentException e) {
            log.warn("Error duplicating item: {}", e.getMessage());
            return notFound(e.getMessage());
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
            return new ItemSearchResult(item.getName(), null, null, null, null);
        }

        return new ItemSearchResult(
                item.getName(),
                publicData.getItemType(),
                publicData.getType(),
                publicData.getTitle(),
                publicData.getTexture()
        );
    }
}
