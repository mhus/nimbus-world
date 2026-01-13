package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.ItemRef;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WChest;
import de.mhus.nimbus.world.shared.world.WChestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing WChest entities.
 * Provides endpoints for user-related, region-related, and world-related chests.
 */
@RestController
@RequestMapping("/control/regions/{regionId}/chests")
@RequiredArgsConstructor
public class WChestController extends BaseEditorController {

    private final WChestService chestService;

    // DTOs
    public record ChestRequest(
            String name,
            String displayName,
            String description,
            String worldId,
            String userId,
            WChest.ChestType type,
            List<ItemRef> items
    ) {}

    public record ChestResponse(
            String id,
            String regionId,
            String worldId,
            String name,
            String displayName,
            String description,
            String userId,
            WChest.ChestType type,
            List<ItemRef> items,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ItemRefRequest(
            ItemRef itemRef
    ) {}

    private ChestResponse toResponse(WChest chest) {
        return new ChestResponse(
                chest.getId(),
                chest.getRegionId(),
                chest.getWorldId(),
                chest.getName(),
                chest.getTitle(),
                chest.getDescription(),
                chest.getUserId(),
                chest.getType(),
                chest.getItems(),
                chest.getCreatedAt(),
                chest.getUpdatedAt()
        );
    }

    /**
     * List all chests in a region
     * GET /control/regions/{regionId}/chests
     *
     * Query parameters:
     * - type: Filter by chest type (REGION, WORLD, USER)
     * - userId: Filter by user ID (for USER type chests)
     * - worldId: Filter by world ID (for WORLD type chests)
     */
    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable String regionId,
            @RequestParam(required = false) WChest.ChestType type,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String worldId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        try {
            List<WChest> chests;

            // Apply filters based on query parameters
            if (type != null && userId != null) {
                // Filter by type and userId
                chests = chestService.findByRegionIdAndUserId(regionId, userId).stream()
                        .filter(c -> c.getType() == type)
                        .toList();
            } else if (type != null && worldId != null) {
                // Filter by type and worldId
                chests = chestService.findByWorldIdAndType(worldId, type);
            } else if (type != null) {
                // Filter by type only
                chests = chestService.findByRegionIdAndType(regionId, type);
            } else if (userId != null) {
                // Filter by userId only
                chests = chestService.findByRegionIdAndUserId(regionId, userId);
            } else if (worldId != null) {
                // Filter by worldId only
                chests = chestService.findByWorldId(worldId);
            } else {
                // No filter, all chests in region
                chests = chestService.findByRegionId(regionId);
            }

            List<ChestResponse> result = chests.stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * List all user-related chests in a region
     * GET /control/regions/{regionId}/chests/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> listUserChests(
            @PathVariable String regionId,
            @PathVariable String userId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(userId, "userId");
        if (error2 != null) return error2;

        try {
            List<ChestResponse> result = chestService.findByRegionIdAndUserId(regionId, userId).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * List all region-related chests (type = REGION)
     * GET /control/regions/{regionId}/chests/region
     */
    @GetMapping("/region")
    public ResponseEntity<?> listRegionChests(
            @PathVariable String regionId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        try {
            List<ChestResponse> result = chestService.findByRegionIdAndType(regionId, WChest.ChestType.REGION).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * List all world-related chests for a specific world
     * GET /control/regions/{regionId}/chests/world/{worldId}
     */
    @GetMapping("/world/{worldId}")
    public ResponseEntity<?> listWorldChests(
            @PathVariable String regionId,
            @PathVariable String worldId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(worldId, "worldId");
        if (error2 != null) return error2;

        try {
            List<ChestResponse> result = chestService.findByWorldIdAndType(worldId, WChest.ChestType.WORLD).stream()
                    .filter(c -> regionId.equals(c.getRegionId()))
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get chest by name
     * GET /control/regions/{regionId}/chests/{name}
     */
    @GetMapping("/{name}")
    public ResponseEntity<?> get(
            @PathVariable String regionId,
            @PathVariable String name) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(name, "name");
        if (error2 != null) return error2;

        return chestService.getByRegionIdAndName(regionId, name)
                .<ResponseEntity<?>>map(chest -> ResponseEntity.ok(toResponse(chest)))
                .orElseGet(() -> notFound("Chest not found: " + name));
    }

    /**
     * Create new chest
     * POST /control/regions/{regionId}/chests
     */
    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String regionId,
            @RequestBody ChestRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        if (blank(request.name())) {
            return bad("name is required");
        }

        if (request.type() == null) {
            return bad("type is required");
        }

        // Validate type-specific requirements
        if (request.type() == WChest.ChestType.USER && blank(request.userId())) {
            return bad("userId is required for USER type chests");
        }

        if (request.type() == WChest.ChestType.WORLD && blank(request.worldId())) {
            return bad("worldId is required for WORLD type chests");
        }

        try {
            WChest created = chestService.createChest(
                    regionId,
                    request.worldId(),
                    request.name(),
                    request.displayName(),
                    request.description(),
                    request.userId(),
                    request.type()
            );

            // Add initial item references if provided
            if (request.items() != null && !request.items().isEmpty()) {
                for (ItemRef itemRef : request.items()) {
                    chestService.addItem(created.getId(), itemRef);
                }
                created = chestService.getByRegionIdAndName(regionId, request.name()).orElseThrow();
            }

            return ResponseEntity.created(URI.create("/control/regions/" + regionId + "/chests/" + created.getName()))
                    .body(toResponse(created));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update chest
     * PUT /control/regions/{regionId}/chests/{name}
     */
    @PutMapping("/{name}")
    public ResponseEntity<?> update(
            @PathVariable String regionId,
            @PathVariable String name,
            @RequestBody ChestRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(name, "name");
        if (error2 != null) return error2;

        WChest existing = chestService.getByRegionIdAndName(regionId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.updateChest(existing.getId(), chest -> {
                if (request.displayName() != null) chest.setTitle(request.displayName());
                if (request.description() != null) chest.setDescription(request.description());
                if (request.worldId() != null) chest.setWorldId(request.worldId());
                if (request.userId() != null) chest.setUserId(request.userId());
                if (request.type() != null) chest.setType(request.type());
                if (request.items() != null) chest.setItems(request.items());
            });

            WChest updated = chestService.getByRegionIdAndName(regionId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Add item reference to chest
     * POST /control/regions/{regionId}/chests/{name}/items
     */
    @PostMapping("/{name}/items")
    public ResponseEntity<?> addItem(
            @PathVariable String regionId,
            @PathVariable String name,
            @RequestBody ItemRefRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(name, "name");
        if (error2 != null) return error2;

        if (request.itemRef() == null) {
            return bad("itemRef is required");
        }

        WChest existing = chestService.getByRegionIdAndName(regionId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.addItem(existing.getId(), request.itemRef());
            WChest updated = chestService.getByRegionIdAndName(regionId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update item amount in chest
     * PATCH /control/regions/{regionId}/chests/{name}/items/{itemId}
     */
    @PatchMapping("/{name}/items/{itemId}")
    public ResponseEntity<?> updateItemAmount(
            @PathVariable String regionId,
            @PathVariable String name,
            @PathVariable String itemId,
            @RequestBody Map<String, Integer> body) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(name, "name");
        if (error2 != null) return error2;

        var error3 = validateId(itemId, "itemId");
        if (error3 != null) return error3;

        Integer newAmount = body.get("amount");
        if (newAmount == null || newAmount <= 0) {
            return bad("amount is required and must be greater than 0");
        }

        WChest existing = chestService.getByRegionIdAndName(regionId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.updateItemAmount(existing.getId(), itemId, newAmount);
            WChest updated = chestService.getByRegionIdAndName(regionId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Remove item from chest
     * DELETE /control/regions/{regionId}/chests/{name}/items/{itemId}
     */
    @DeleteMapping("/{name}/items/{itemId}")
    public ResponseEntity<?> removeItem(
            @PathVariable String regionId,
            @PathVariable String name,
            @PathVariable String itemId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(name, "name");
        if (error2 != null) return error2;

        var error3 = validateId(itemId, "itemId");
        if (error3 != null) return error3;

        WChest existing = chestService.getByRegionIdAndName(regionId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.removeItem(existing.getId(), itemId);
            WChest updated = chestService.getByRegionIdAndName(regionId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Delete chest
     * DELETE /control/regions/{regionId}/chests/{name}
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<?> delete(
            @PathVariable String regionId,
            @PathVariable String name) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(name, "name");
        if (error2 != null) return error2;

        if (chestService.getByRegionIdAndName(regionId, name).isEmpty()) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.deleteChest(regionId, name);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }
}
