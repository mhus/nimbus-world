package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.world.shared.access.AccessValidator;
import de.mhus.nimbus.world.shared.world.WItemType;
import de.mhus.nimbus.world.shared.world.WItemTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for ItemType templates (read-only).
 * Returns only publicData from entities.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "ItemTypes", description = "ItemType templates for items and inventory")
public class ItemTypeController {

    private final WItemTypeService service;
    private final AccessValidator accessUtil;

    /**
     * Get ItemType by type.
     * GET /player/world/itemtypes/{itemType}
     */
    @GetMapping("/player/world/itemtypes/{itemType}")
    @Operation(summary = "Get ItemType by type", description = "Returns ItemType template for a specific item type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ItemType found"),
            @ApiResponse(responseCode = "404", description = "ItemType not found")
    })
    public ResponseEntity<?> getItemType(
            HttpServletRequest request,
            @PathVariable String itemType) {

        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        log.debug("GET itemType: worldId={}, itemType={}", worldId, itemType);

        if (itemType == null || itemType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "itemType is required"));
        }

        return service.findByItemType(worldId, itemType)
                        .map(WItemType::getPublicData)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> {
                            log.warn("ItemType not found: worldId={}, itemType={}", worldId, itemType);
                            return ResponseEntity.notFound().build();
                        });
    }

    /**
     * Get all ItemTypes for a world.
     * GET /player/worlds/{worldId}/itemtypes
     */
//    @Deprecated
//    @GetMapping("/player/world/itemtypes")
//    @Operation(summary = "Get all ItemTypes", description = "Returns all enabled ItemType templates for a world")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "List of ItemTypes")
//    })
//    public ResponseEntity<?> getAllItemTypes(HttpServletRequest request) {
//
//        var worldId = accessUtil.getWorldId(request).orElseThrow(
//                () -> new IllegalStateException("World ID not found in request")
//        );
//
//        log.debug("GET all itemTypes: worldId={}", worldId);
//
//        List<ItemType> itemTypes = service.findByWorldId(worldId).stream()
//                .filter(WItemType::isEnabled)
//                .map(WItemType::getPublicData)
//                .toList();
//
//        return ResponseEntity.ok(Map.of(
//                "itemTypes", itemTypes,
//                "count", itemTypes.size()));
//    }
}
