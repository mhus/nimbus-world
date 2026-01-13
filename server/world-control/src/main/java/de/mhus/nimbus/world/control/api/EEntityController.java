package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityService;
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
 * REST Controller for Entity CRUD operations.
 * Base path: /control/worlds/{worldId}/entities
 * <p>
 * Entities are instances placed in the world based on EntityModel templates.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/entities")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Entities", description = "Entity instance management")
public class EEntityController extends BaseEditorController {

    private final WEntityService entityService;

    // DTOs
    public record EntityDto(
            String entityId,
            Entity publicData,
            String worldId,
            String modelId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateEntityRequest(String entityId, Entity publicData, String modelId) {
    }

    public record UpdateEntityRequest(Entity publicData, String modelId, Boolean enabled) {
    }

    /**
     * Get single Entity by ID.
     * GET /control/worlds/{worldId}/entity/{entityId}
     */
    @GetMapping("/{entityId}")
    @Operation(summary = "Get Entity by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Entity identifier") @PathVariable String entityId) {

        log.debug("GET entity: worldId={}, entityId={}", worldId, entityId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(entityId, "entityId");
        if (validation != null) return validation;

        Optional<WEntity> opt = entityService.findByWorldIdAndEntityId(wid, entityId);
        if (opt.isEmpty()) {
            log.warn("Entity not found: worldId={}, entityId={}", worldId, entityId);
            return notFound("entity not found");
        }

        log.debug("Returning entity: entityId={}", entityId);
        // Return publicData only (match test_server format)
        return ResponseEntity.ok(opt.get().getPublicData());
    }

    /**
     * List all Entities for a world with optional search filter and pagination.
     * GET /control/worlds/{worldId}/entity?query=...&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List all Entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST entities: worldId={}, query={}, offset={}, limit={}", worldId, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // Get all Entities for this world with query filter
        List<WEntity> all = entityService.findByWorldIdAndQuery(wid, query);

        int totalCount = all.size();

        // Apply pagination
        List<Entity> publicDataList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(WEntity::getPublicData)
                .collect(Collectors.toList());

        log.debug("Returning {} entities (total: {})", publicDataList.size(), totalCount);

        // TypeScript compatible format (match test_server response)
        return ResponseEntity.ok(Map.of(
                "entities", publicDataList,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Create new Entity.
     * POST /control/worlds/{worldId}/entity
     */
    @PostMapping
    @Operation(summary = "Create new Entity")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entity created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Entity already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateEntityRequest request) {

        log.debug("CREATE entity: worldId={}, entityId={}", worldId, request.entityId());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        if (blank(request.entityId())) {
            return bad("entityId required");
        }

        if (request.publicData() == null) {
            return bad("publicData required");
        }

        // Check if Entity already exists
        if (entityService.findByWorldIdAndEntityId(wid, request.entityId()).isPresent()) {
            return conflict("entity already exists");
        }

        try {
            WEntity saved = entityService.save(
                    wid,
                    request.entityId(),
                    request.publicData(),
                    request.modelId()
            );

            log.info("Created entity: entityId={}", request.entityId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating entity: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing Entity.
     * PUT /control/worlds/{worldId}/entity/{entityId}
     */
    @PutMapping("/{entityId}")
    @Operation(summary = "Update Entity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Entity identifier") @PathVariable String entityId,
            @RequestBody UpdateEntityRequest request) {

        log.debug("UPDATE entity: worldId={}, entityId={}", worldId, entityId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(entityId, "entityId");
        if (validation != null) return validation;

        if (request.publicData() == null && request.modelId() == null && request.enabled() == null) {
            return bad("at least one field required for update");
        }

        Optional<WEntity> updated = entityService.update(wid, entityId, entity -> {
            if (request.publicData() != null) {
                entity.setPublicData(request.publicData());
            }
            if (request.modelId() != null) {
                entity.setModelId(request.modelId());
            }
            if (request.enabled() != null) {
                entity.setEnabled(request.enabled());
            }
        });

        if (updated.isEmpty()) {
            log.warn("Entity not found for update: worldId={}, entityId={}", worldId, entityId);
            return notFound("entity not found");
        }

        log.info("Updated entity: entityId={}", entityId);
        return ResponseEntity.ok(toDto(updated.get()));
    }

    /**
     * Delete Entity.
     * DELETE /control/worlds/{worldId}/entity/{entityId}
     */
    @DeleteMapping("/{entityId}")
    @Operation(summary = "Delete Entity")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entity deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Entity identifier") @PathVariable String entityId) {

        log.debug("DELETE entity: worldId={}, entityId={}", worldId, entityId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validateId(entityId, "entityId");
        if (validation != null) return validation;

        boolean deleted = entityService.delete(wid, entityId);
        if (!deleted) {
            log.warn("Entity not found for deletion: worldId={}, entityId={}", worldId, entityId);
            return notFound("entity not found");
        }

        log.info("Deleted entity: entityId={}", entityId);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private EntityDto toDto(WEntity entity) {
        return new EntityDto(
                entity.getEntityId(),
                entity.getPublicData(),
                entity.getWorldId(),
                entity.getModelId(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

}
