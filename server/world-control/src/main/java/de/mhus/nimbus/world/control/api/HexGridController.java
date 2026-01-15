package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
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
 * REST Controller for managing WHexGrid entities.
 * Provides CRUD operations for hexagonal grid areas within worlds.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/hexgrid")
@RequiredArgsConstructor
public class HexGridController extends BaseEditorController {

    private final WHexGridService hexGridService;
    private final de.mhus.nimbus.world.shared.layer.WDirtyChunkService dirtyChunkService;
    private final de.mhus.nimbus.world.shared.world.WWorldService worldService;

    // DTOs

    /**
     * Request DTO for creating or updating hex grids.
     */
    public record HexGridRequest(
            HexGrid publicData,
            Map<String, String> parameters,
            Map<String, Map<String, String>> areas,
            Boolean enabled
    ) {}

    /**
     * Response DTO for hex grid data.
     */
    public record HexGridResponse(
            String id,
            String worldId,
            String position,
            HexGrid publicData,
            Map<String, String> parameters,
            Map<String, Map<String, String>> areas,
            Instant createdAt,
            Instant updatedAt,
            boolean enabled
    ) {}

    private HexGridResponse toResponse(WHexGrid hexGrid) {
        return new HexGridResponse(
                hexGrid.getId(),
                hexGrid.getWorldId(),
                hexGrid.getPosition(),
                hexGrid.getPublicData(),
                hexGrid.getParameters(),
                hexGrid.getAreas(),
                hexGrid.getCreatedAt(),
                hexGrid.getUpdatedAt(),
                hexGrid.isEnabled()
        );
    }

    /**
     * List all hex grids in a world
     * GET /control/worlds/{worldId}/hexgrid
     */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable String worldId) {
        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        try {
            List<HexGridResponse> result = hexGridService.findByWorldId(worldId).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * List only enabled hex grids in a world
     * GET /control/worlds/{worldId}/hexgrid/enabled
     */
    @GetMapping("/enabled")
    public ResponseEntity<?> listEnabled(@PathVariable String worldId) {
        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        try {
            List<HexGridResponse> result = hexGridService.findAllEnabled(worldId).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get hex grid by position (q:r)
     * GET /control/worlds/{worldId}/hexgrid/{q}/{r}
     */
    @GetMapping("/{q}/{r}")
    public ResponseEntity<?> get(
            @PathVariable String worldId,
            @PathVariable int q,
            @PathVariable int r) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        HexVector2 position = HexVector2.builder().q(q).r(r).build();

        return hexGridService.findByWorldIdAndPosition(worldId, position)
                .<ResponseEntity<?>>map(hexGrid -> ResponseEntity.ok(toResponse(hexGrid)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Hex grid not found at position: " + q + ":" + r)));
    }

    /**
     * Find hex grid by chunk coordinates
     * GET /control/worlds/{worldId}/hexgrid/by-chunk/{cx}/{cz}
     *
     * Returns the hex grid that contains the given chunk, or the calculated hex position if not found
     */
    @GetMapping("/by-chunk/{cx}/{cz}")
    public ResponseEntity<?> getByChunk(
            @PathVariable String worldId,
            @PathVariable int cx,
            @PathVariable int cz) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        try {
            // Get world to get chunk size
            var world = worldService.getByWorldId(worldId).orElseThrow(
                    () -> new IllegalArgumentException("World not found: " + worldId)
            );

            if (world.getPublicData() == null || world.getPublicData().getChunkSize() <= 0) {
                return bad("World chunkSize not configured");
            }

            int chunkSize = world.getPublicData().getChunkSize();

            // Convert chunk coordinates to flat world coordinates (center of chunk)
            int flatX = cx * chunkSize + (chunkSize / 2);
            int flatZ = cz * chunkSize + (chunkSize / 2);

            // Convert flat world coordinates to hex coordinates using HexMathUtil
            Vector2Int flatPos = TypeUtil.vector2int(flatX, flatZ);

            HexVector2 hexPos = de.mhus.nimbus.world.shared.world.HexMathUtil.flatToHex(
                flatPos,
                world.getPublicData().getHexGridSize()
            );

            // Try to find hex grid at this position
            var hexGridOpt = hexGridService.findByWorldIdAndPosition(worldId, hexPos);

            if (hexGridOpt.isPresent()) {
                // Found hex grid
                return ResponseEntity.ok(Map.of(
                    "found", true,
                    "hexGrid", toResponse(hexGridOpt.get()),
                    "hexPosition", Map.of("q", hexPos.getQ(), "r", hexPos.getR()),
                    "chunkPosition", Map.of("cx", cx, "cz", cz)
                ));
            } else {
                // Not found, return calculated hex position
                return ResponseEntity.ok(Map.of(
                    "found", false,
                    "hexPosition", Map.of("q", hexPos.getQ(), "r", hexPos.getR()),
                    "chunkPosition", Map.of("cx", cx, "cz", cz)
                ));
            }

        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Failed to find hex grid by chunk: " + e.getMessage());
        }
    }

    /**
     * Create new hex grid
     * POST /control/worlds/{worldId}/hexgrid
     */
    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String worldId,
            @RequestBody HexGridRequest request) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        if (request.publicData() == null) {
            return bad("publicData is required");
        }

        if (request.publicData().getPosition() == null) {
            return bad("publicData.position is required");
        }

        try {
            WHexGrid created = hexGridService.create(
                    worldId,
                    request.publicData(),
                    request.parameters(),
                    request.areas()
            );

            // Apply enabled flag if specified
            if (request.enabled() != null && !request.enabled()) {
                hexGridService.disable(worldId, request.publicData().getPosition());
                created = hexGridService.findByWorldIdAndPosition(worldId, request.publicData().getPosition())
                        .orElseThrow();
            }

            HexVector2 pos = request.publicData().getPosition();
            return ResponseEntity.created(
                    URI.create("/control/worlds/" + worldId + "/hexgrid/" + pos.getQ() + "/" + pos.getR()))
                    .body(toResponse(created));

        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Failed to create hex grid: " + e.getMessage());
        }
    }

    /**
     * Update hex grid
     * PUT /control/worlds/{worldId}/hexgrid/{q}/{r}
     */
    @PutMapping("/{q}/{r}")
    public ResponseEntity<?> update(
            @PathVariable String worldId,
            @PathVariable int q,
            @PathVariable int r,
            @RequestBody HexGridRequest request) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        HexVector2 position = HexVector2.builder().q(q).r(r).build();

        try {
            var updated = hexGridService.update(worldId, position, hexGrid -> {
                if (request.publicData() != null) {
                    hexGrid.setPublicData(request.publicData());
                }
                if (request.parameters() != null) {
                    hexGrid.setParameters(request.parameters());
                }
                if (request.areas() != null) {
                    hexGrid.setAreas(request.areas());
                }
                if (request.enabled() != null) {
                    hexGrid.setEnabled(request.enabled());
                }
            });

            return updated
                    .<ResponseEntity<?>>map(h -> ResponseEntity.ok(toResponse(h)))
                    .orElseGet(() -> notFound("Hex grid not found at position: " + q + ":" + r));

        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Failed to update hex grid: " + e.getMessage());
        }
    }

    /**
     * Patch hex grid (partial update)
     * PATCH /control/worlds/{worldId}/hexgrid/{q}/{r}
     */
    @PatchMapping("/{q}/{r}")
    public ResponseEntity<?> patch(
            @PathVariable String worldId,
            @PathVariable int q,
            @PathVariable int r,
            @RequestBody Map<String, Object> updates) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        HexVector2 position = HexVector2.builder().q(q).r(r).build();

        try {
            var updated = hexGridService.update(worldId, position, hexGrid -> {
                // Only update fields that are present in the request
                if (updates.containsKey("parameters")) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> params = (Map<String, String>) updates.get("parameters");
                    hexGrid.setParameters(params);
                }
                if (updates.containsKey("areas")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, String>> areasMap = (Map<String, Map<String, String>>) updates.get("areas");
                    hexGrid.setAreas(areasMap);
                }
                if (updates.containsKey("enabled")) {
                    hexGrid.setEnabled((Boolean) updates.get("enabled"));
                }
                // Note: publicData updates should use PUT for safety
            });

            return updated
                    .<ResponseEntity<?>>map(h -> ResponseEntity.ok(toResponse(h)))
                    .orElseGet(() -> notFound("Hex grid not found at position: " + q + ":" + r));

        } catch (ClassCastException e) {
            return bad("Invalid data type in update: " + e.getMessage());
        } catch (Exception e) {
            return bad("Failed to patch hex grid: " + e.getMessage());
        }
    }

    /**
     * Delete hex grid
     * DELETE /control/worlds/{worldId}/hexgrid/{q}/{r}
     */
    @DeleteMapping("/{q}/{r}")
    public ResponseEntity<?> delete(
            @PathVariable String worldId,
            @PathVariable int q,
            @PathVariable int r) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        HexVector2 position = HexVector2.builder().q(q).r(r).build();

        try {
            boolean deleted = hexGridService.delete(worldId, position);
            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                return notFound("Hex grid not found at position: " + q + ":" + r);
            }
        } catch (Exception e) {
            return bad("Failed to delete hex grid: " + e.getMessage());
        }
    }

    /**
     * Disable hex grid (soft delete)
     * POST /control/worlds/{worldId}/hexgrid/{q}/{r}/disable
     */
    @PostMapping("/{q}/{r}/disable")
    public ResponseEntity<?> disable(
            @PathVariable String worldId,
            @PathVariable int q,
            @PathVariable int r) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        HexVector2 position = HexVector2.builder().q(q).r(r).build();

        try {
            boolean disabled = hexGridService.disable(worldId, position);
            if (disabled) {
                return ResponseEntity.ok(Map.of("message", "Hex grid disabled", "position", q + ":" + r));
            } else {
                return notFound("Hex grid not found at position: " + q + ":" + r);
            }
        } catch (Exception e) {
            return bad("Failed to disable hex grid: " + e.getMessage());
        }
    }

    /**
     * Enable hex grid
     * POST /control/worlds/{worldId}/hexgrid/{q}/{r}/enable
     */
    @PostMapping("/{q}/{r}/enable")
    public ResponseEntity<?> enable(
            @PathVariable String worldId,
            @PathVariable int q,
            @PathVariable int r) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        HexVector2 position = HexVector2.builder().q(q).r(r).build();

        try {
            boolean enabled = hexGridService.enable(worldId, position);
            if (enabled) {
                return ResponseEntity.ok(Map.of("message", "Hex grid enabled", "position", q + ":" + r));
            } else {
                return notFound("Hex grid not found at position: " + q + ":" + r);
            }
        } catch (Exception e) {
            return bad("Failed to enable hex grid: " + e.getMessage());
        }
    }

    /**
     * Mark all chunks affected by this hex grid as dirty.
     * POST /control/worlds/{worldId}/hexgrid/{q}/{r}/dirty
     */
    @PostMapping("/{q}/{r}/dirty")
    public ResponseEntity<?> markAffectedChunksDirty(
            @PathVariable String worldId,
            @PathVariable int q,
            @PathVariable int r) {

        if (Strings.isBlank(worldId)) {
            return bad("worldId is required");
        }

        HexVector2 position = HexVector2.builder().q(q).r(r).build();

        try {
            // Get hex grid entity
            var hexGridOpt = hexGridService.findByWorldIdAndPosition(worldId, position);
            if (hexGridOpt.isEmpty()) {
                return notFound("Hex grid not found at position: " + q + ":" + r);
            }

            WHexGrid hexGrid = hexGridOpt.get();

            // Get world entity for chunk size calculation
            var world = worldService.getByWorldId(worldId).orElseThrow(
                    () -> new IllegalArgumentException("World not found: " + worldId)
            );

            // Validate world configuration
            if (world.getPublicData() == null) {
                return bad("World has no publicData configured");
            }
            if (world.getPublicData().getHexGridSize() <= 0) {
                return bad("World hexGridSize is not configured (value: " + world.getPublicData().getHexGridSize() + "). Please configure hexGridSize in world settings.");
            }
            if (world.getPublicData().getChunkSize() <= 0) {
                return bad("World chunkSize is not configured (value: " + world.getPublicData().getChunkSize() + "). Please configure chunkSize in world settings.");
            }

            // Get all affected chunk keys
            java.util.Set<String> affectedChunks = hexGrid.getAffectedChunkKeys(world);

            if (affectedChunks.isEmpty()) {
                return bad("No chunks affected by this hex grid");
            }

            // Mark all chunks as dirty
            dirtyChunkService.markChunksDirty(worldId, new java.util.ArrayList<>(affectedChunks), "hexgrid_manual_dirty");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "position", q + ":" + r,
                    "chunksMarked", affectedChunks.size(),
                    "message", "Marked " + affectedChunks.size() + " chunks as dirty"
            ));
        } catch (Exception e) {
            return bad("Failed to mark chunks dirty: " + e.getMessage());
        }
    }
}
