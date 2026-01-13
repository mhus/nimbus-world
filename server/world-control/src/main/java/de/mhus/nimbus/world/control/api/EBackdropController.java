package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Backdrop;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WBackdrop;
import de.mhus.nimbus.world.shared.world.WBackdropService;
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
 * REST Controller for Backdrop CRUD operations.
 * Base path: /control/worlds/{worldId}/backdrops
 * <p>
 * Backdrops are visual elements rendered at chunk boundaries (fog, sky, etc.).
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/backdrops")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Backdrops", description = "Backdrop configuration management")
public class EBackdropController extends BaseEditorController {

    private final WBackdropService backdropService;

    // DTOs
    public record BackdropDto(
            String backdropId,
            Backdrop publicData,
            String worldId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateBackdropRequest(String backdropId, Backdrop publicData) {
    }

    public record UpdateBackdropRequest(Backdrop publicData, Boolean enabled) {
    }

    /**
     * Get single backdrop by ID.
     * GET /control/worlds/{worldId}/backdrop/{backdropId}
     */
    @GetMapping("/{backdropId}")
    @Operation(summary = "Get backdrop by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backdrop found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Backdrop not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Backdrop identifier") @PathVariable String backdropId) {

        log.debug("GET backdrop: worldId={}, backdropId={}", worldId, backdropId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(backdropId, "backdropId");
        if (validation != null) return validation;

        Optional<WBackdrop> opt = backdropService.findByBackdropId(wid, backdropId);
        if (opt.isEmpty()) {
            log.warn("Backdrop not found: backdropId={}", backdropId);
            return notFound("backdrop not found");
        }

        log.debug("Returning backdrop: backdropId={}", backdropId);
        // Return publicData only (match test_server format)
        return ResponseEntity.ok(opt.get().getPublicData());
    }

    /**
     * List all backdrops for a world with optional search filter and pagination.
     * GET /control/worlds/{worldId}/backdrop?query=...&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List all backdrops")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST backdrops: worldId={}, query={}, offset={}, limit={}", worldId, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        List<WBackdrop> all = backdropService.findByWorldIdAndQuery(wid, query);

        int totalCount = all.size();

        // Apply pagination and include backdropId in response
        List<Map<String, Object>> backdropList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(backdrop -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("backdropId", backdrop.getBackdropId());
                    result.put("publicData", backdrop.getPublicData());
                    result.put("enabled", backdrop.isEnabled());
                    return result;
                })
                .collect(Collectors.toList());

        log.debug("Returning {} backdrops (total: {})", backdropList.size(), totalCount);

        // TypeScript compatible format with backdropId included
        return ResponseEntity.ok(Map.of(
                "backdrops", backdropList,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Create new backdrop.
     * POST /control/worlds/{worldId}/backdrop
     */
    @PostMapping
    @Operation(summary = "Create new backdrop")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Backdrop created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Backdrop already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateBackdropRequest request) {

        log.debug("CREATE backdrop: worldId={}, backdropId={}", worldId, request.backdropId());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        if (blank(request.backdropId())) {
            return bad("backdropId required");
        }

        if (request.publicData() == null) {
            return bad("publicData required");
        }

        // Check if backdrop already exists
        if (backdropService.findByBackdropId(wid, request.backdropId()).isPresent()) {
            return conflict("backdrop already exists");
        }

        try {
            WBackdrop saved = backdropService.save(wid, request.backdropId(), request.publicData());
            log.info("Created backdrop: backdropId={}", request.backdropId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating backdrop: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating backdrop", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing backdrop.
     * PUT /control/worlds/{worldId}/backdrop/{backdropId}
     */
    @PutMapping("/{backdropId}")
    @Operation(summary = "Update backdrop")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backdrop updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Backdrop not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Backdrop identifier") @PathVariable String backdropId,
            @RequestBody UpdateBackdropRequest request) {

        log.debug("UPDATE backdrop: worldId={}, backdropId={}", worldId, backdropId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(backdropId, "backdropId");
        if (validation != null) return validation;

        if (request.publicData() == null && request.enabled() == null) {
            return bad("at least one field required for update");
        }

        Optional<WBackdrop> updated = backdropService.update(wid, backdropId, backdrop -> {
            if (request.publicData() != null) {
                backdrop.setPublicData(request.publicData());
            }
            if (request.enabled() != null) {
                backdrop.setEnabled(request.enabled());
            }
        });

        if (updated.isEmpty()) {
            log.warn("Backdrop not found for update: backdropId={}", backdropId);
            return notFound("backdrop not found");
        }

        log.info("Updated backdrop: backdropId={}", backdropId);
        return ResponseEntity.ok(toDto(updated.get()));
    }

    /**
     * Delete backdrop.
     * DELETE /control/worlds/{worldId}/backdrop/{backdropId}
     */
    @DeleteMapping("/{backdropId}")
    @Operation(summary = "Delete backdrop")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Backdrop deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Backdrop not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Backdrop identifier") @PathVariable String backdropId) {

        log.debug("DELETE backdrop: worldId={}, backdropId={}", worldId, backdropId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(backdropId, "backdropId");
        if (validation != null) return validation;

        boolean deleted = backdropService.delete(wid, backdropId);
        if (!deleted) {
            log.warn("Backdrop not found for deletion: backdropId={}", backdropId);
            return notFound("backdrop not found");
        }

        log.info("Deleted backdrop: backdropId={}", backdropId);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private BackdropDto toDto(WBackdrop entity) {
        return new BackdropDto(
                entity.getBackdropId(),
                entity.getPublicData(),
                entity.getWorldId(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
