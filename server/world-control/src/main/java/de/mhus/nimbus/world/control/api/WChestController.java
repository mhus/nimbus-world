package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.ItemRef;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WChest;
import de.mhus.nimbus.world.shared.world.WChestService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
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
@RequestMapping("/control/world/{worldId}/chests")
@RequiredArgsConstructor
public class WChestController extends BaseEditorController {

    private final WChestService chestService;

    // DTOs
    public record ChestRequest(
            String name,
            String title,
            String description,
            String playerId,
            WChest.ChestType type,
            Boolean bank,
            String pin,
            Integer capacity,
            String keyId,
            Integer lockPickingDifficulty,
            List<ItemRef> items
    ) {}

    public record ChestResponse(
            String id,
            String worldId,
            String name,
            String title,
            String description,
            String playerId,
            WChest.ChestType type,
            boolean bank,
            String pin,
            int capacity,
            String keyId,
            int lockPickingDifficulty,
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
                chest.getWorldId(),
                chest.getName(),
                chest.getTitle(),
                chest.getDescription(),
                chest.getPlayerId(),
                chest.getType(),
                chest.isBank(),
                chest.getPin(),
                chest.getCapacity(),
                chest.getKeyId(),
                chest.getLockPickingDifficulty(),
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
     * - playerId: Filter by user ID (for USER type chests)
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) WChest.ChestType type,
            @RequestParam(required = false) String playerId,
            @PathVariable(required = false) String worldId) {

        try {
            List<WChest> chests;

            if (type != null) {
                chests = chestService.findByWorldIdAndType(worldId, type);
            } else if (playerId != null) {
                chests = chestService.findByWorldIdAndPlayerId(worldId, playerId);
            } else {
                chests = chestService.findByWorldId(worldId);
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
     * GET /control/regions/{regionId}/chests/user/{playerId}
     */
    @GetMapping("/user/{playerId}")
    public ResponseEntity<?> listUserChests(
            @PathVariable String worldId,
            @PathVariable String playerId) {

        var error2 = validateId(playerId, "playerId");
        if (error2 != null) return error2;

        try {
            List<ChestResponse> result = chestService.findByWorldIdAndPlayerId(worldId, playerId).stream()
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
            @PathVariable String worldId) {

        // in this case worldId is a @region: collection
        try {
            List<ChestResponse> result = chestService.findByWorldIdAndType(worldId, WChest.ChestType.REGION).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get chest by title
     * GET /control/regions/{regionId}/chests/{title}
     */
    @GetMapping("/{name}")
    public ResponseEntity<?> get(
            @PathVariable String worldId,
            @PathVariable String name) {

        var error2 = validateId(name, "title");
        if (error2 != null) return error2;

        return chestService.getByWorldIdAndName(worldId, name)
                .<ResponseEntity<?>>map(chest -> ResponseEntity.ok(toResponse(chest)))
                .orElseGet(() -> notFound("Chest not found: " + name));
    }

    /**
     * Create new chest
     * POST /control/world/{worldId}/chests
     */
    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String worldId,
            @RequestBody ChestRequest request) {

        if (Strings.isBlank(request.name())) {
            return bad("title is required");
        }

        if (request.type() == null) {
            return bad("type is required");
        }

        // Validate type-specific requirements
        if (request.type() == WChest.ChestType.PLAYER && Strings.isBlank(request.playerId())) {
            return bad("playerId is required for USER type chests");
        }

        if (request.type() == WChest.ChestType.WORLD && Strings.isBlank(worldId)) {
            return bad("worldId is required for WORLD type chests");
        }

        try {
            WChest created = chestService.createChest(
                    worldId,
                    request.name(),
                    request.title(),
                    request.description(),
                    request.playerId(),
                    request.type()
            );

            // Set additional fields
            chestService.updateChest(created.getId(), chest -> {
                if (request.bank() != null) chest.setBank(request.bank());
                if (request.pin() != null) chest.setPin(request.pin());
                if (request.capacity() != null) chest.setCapacity(request.capacity());
                if (request.keyId() != null) chest.setKeyId(request.keyId());
                if (request.lockPickingDifficulty() != null) chest.setLockPickingDifficulty(request.lockPickingDifficulty());
            });

            // Add initial item references if provided
            if (request.items() != null && !request.items().isEmpty()) {
                for (ItemRef itemRef : request.items()) {
                    chestService.addItem(created.getId(), itemRef);
                }
                created = chestService.getByWorldIdAndName(worldId, request.name()).orElseThrow();
            }

            return ResponseEntity.created(URI.create("/control/world/" + worldId + "/chests/" + created.getName()))
                    .body(toResponse(created));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update chest
     * PUT /control/regions/{regionId}/chests/{title}
     */
    @PutMapping("/{name}")
    public ResponseEntity<?> update(
            @PathVariable String worldId,
            @PathVariable String name,
            @RequestBody ChestRequest request) {

        var error = validateId(worldId, "regionId");
        if (error != null) return error;

        var error2 = validateId(name, "title");
        if (error2 != null) return error2;

        WChest existing = chestService.getByWorldIdAndName(worldId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.updateChest(existing.getId(), chest -> {
                if (request.title() != null) chest.setTitle(request.title());
                if (request.description() != null) chest.setDescription(request.description());
                chest.setWorldId(worldId);
                if (request.playerId() != null) chest.setPlayerId(request.playerId());
                if (request.type() != null) chest.setType(request.type());
                if (request.bank() != null) chest.setBank(request.bank());
                if (request.pin() != null) chest.setPin(request.pin());
                if (request.capacity() != null) chest.setCapacity(request.capacity());
                if (request.keyId() != null) chest.setKeyId(request.keyId());
                if (request.lockPickingDifficulty() != null) chest.setLockPickingDifficulty(request.lockPickingDifficulty());
                if (request.items() != null) chest.setItems(request.items());
            });

            WChest updated = chestService.getByWorldIdAndName(worldId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Add item reference to chest
     * POST /control/regions/{regionId}/chests/{title}/items
     */
    @PostMapping("/{name}/items")
    public ResponseEntity<?> addItem(
            @PathVariable String worldId,
            @PathVariable String name,
            @RequestBody ItemRefRequest request) {

        var error2 = validateId(name, "title");
        if (error2 != null) return error2;

        if (request.itemRef() == null) {
            return bad("itemRef is required");
        }

        WChest existing = chestService.getByWorldIdAndName(worldId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.addItem(existing.getId(), request.itemRef());
            WChest updated = chestService.getByWorldIdAndName(worldId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update item amount in chest
     * PATCH /control/regions/{regionId}/chests/{title}/items/{itemId}
     */
    @PatchMapping("/{name}/items/{itemId}")
    public ResponseEntity<?> updateItemAmount(
            @PathVariable String worldId,
            @PathVariable String name,
            @PathVariable String itemId,
            @RequestBody Map<String, Integer> body) {

        var error2 = validateId(name, "title");
        if (error2 != null) return error2;

        var error3 = validateId(itemId, "itemId");
        if (error3 != null) return error3;

        Integer newAmount = body.get("amount");
        if (newAmount == null || newAmount <= 0) {
            return bad("amount is required and must be greater than 0");
        }

        WChest existing = chestService.getByWorldIdAndName(worldId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.updateItemAmount(existing.getId(), itemId, newAmount);
            WChest updated = chestService.getByWorldIdAndName(worldId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Remove item from chest
     * DELETE /control/regions/{regionId}/chests/{title}/items/{itemId}
     */
    @DeleteMapping("/{name}/items/{itemId}")
    public ResponseEntity<?> removeItem(
            @PathVariable String worldId,
            @PathVariable String name,
            @PathVariable String itemId) {

        var error2 = validateId(name, "title");
        if (error2 != null) return error2;

        var error3 = validateId(itemId, "itemId");
        if (error3 != null) return error3;

        WChest existing = chestService.getByWorldIdAndName(worldId, name).orElse(null);
        if (existing == null) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.removeItem(existing.getId(), itemId);
            WChest updated = chestService.getByWorldIdAndName(worldId, name).orElseThrow();
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Delete chest
     * DELETE /control/regions/{regionId}/chests/{title}
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<?> delete(
            @PathVariable String worldId,
            @PathVariable String name) {

        var error2 = validateId(name, "title");
        if (error2 != null) return error2;

        if (chestService.getByWorldIdAndName(worldId, name).isEmpty()) {
            return notFound("Chest not found: " + name);
        }

        try {
            chestService.deleteChest(worldId, name);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }
}
