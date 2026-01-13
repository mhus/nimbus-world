package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessValidator;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Items (read-only).
 * Returns only publicData from entities.
 * For edit operations, use world-control service.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Items", description = "Item management (read-only)")
public class ItemController {

    private final WItemService itemService;
    private final AccessValidator accessUtil;

    // DTO for search results
    public record ItemSearchResult(
            String itemId,
            String name,
            String texture
    ) {}

    /**
     * Search items (max 100 results).
     * GET /player/worlds/{worldId}/items?query={searchTerm}
     */
//    @Deprecated
//    @GetMapping("/player/world/items")
//    @Operation(summary = "Search items", description = "Returns items matching the search query")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "Success"),
//            @ApiResponse(responseCode = "400", description = "Invalid parameters")
//    })
//    public ResponseEntity<?> search(
//            HttpServletRequest request,
//            @Parameter(description = "Search query") @RequestParam(required = false, defaultValue = "") String query) {
//
//        var worldId = accessUtil.getWorldId(request).orElseThrow(
//                () -> new IllegalStateException("World ID not found in request")
//        );
//
//        log.debug("SEARCH items: worldId={}, query={}", worldId, query);
//
//        List<WItem> all = itemService.findEnabledByWorldId(worldId);
//        String lowerQuery = query.toLowerCase();
//        final int maxResults = 100;
//
//        List<ItemSearchResult> results = all.stream()
//                .filter(item -> {
//                    if (query.isBlank()) return true;
//                    Item publicData = item.getPublicData();
//                    if (publicData == null) return false;
//
//                    // Match query against itemId, name, or description
//                    return (publicData.getId() != null && publicData.getId().toLowerCase().contains(lowerQuery)) ||
//                            (publicData.getName() != null && publicData.getName().toLowerCase().contains(lowerQuery)) ||
//                            (publicData.getDescription() != null && publicData.getDescription().toLowerCase().contains(lowerQuery));
//                })
//                .limit(maxResults)
//                .map(this::toSearchResult)
//                .collect(Collectors.toList());
//
//        log.debug("Returning {} items", results.size());
//        return ResponseEntity.ok(Map.of("items", results));
//    }

    /**
     * Get full item data.
     * GET /player/worlds/{worldId}/item/{itemId}
     */
    @GetMapping("/player/worlds/{worldId}/item/{itemId}")
    @Operation(summary = "Get item by ID", description = "Returns full item data for a specific itemId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item found"),
            @ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Item identifier") @PathVariable String itemId) {

        log.debug("GET item: worldId={}, itemId={}", worldId, itemId);

        if (worldId == null || worldId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "worldId is required"));
        }

        if (itemId == null || itemId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "itemId is required"));
        }

        WorldId wid = WorldId.of(worldId).orElse(null);
        if (wid == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid worldId"));
        }

        return itemService.findByItemId(wid, itemId)
                .map(WItem::getPublicData)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Item not found: worldId={}, itemId={}", worldId, itemId);
                    return ResponseEntity.notFound().build();
                });
    }

    // Helper method
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
