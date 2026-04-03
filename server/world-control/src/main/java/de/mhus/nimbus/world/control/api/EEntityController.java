package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.access.RequireWorldRole;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.EntitySchedulePhase;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityService;
import de.mhus.nimbus.world.shared.world.WEntityType;
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
@RequireWorldRole(WorldRoles.EDITOR)
public class EEntityController extends BaseEditorController {

    private final WEntityService entityService;

    // DTOs
    public record EntityDto(
            String entityId,
            Entity publicData,
            String worldId,
            String modelId,
            boolean enabled,
            WEntityType type,
            String portraitPath,
            Map<String, String> server,
            List<Integer> epoches,
            List<EntitySchedulePhase> schedule,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateEntityRequest(String entityId, Entity publicData, String modelId, WEntityType type, String portraitPath, Map<String, String> server, List<Integer> epoches, List<EntitySchedulePhase> schedule) {
    }

    public record UpdateEntityRequest(Entity publicData, String modelId, Boolean enabled, WEntityType type, String portraitPath, Map<String, String> server, List<Integer> epoches, List<EntitySchedulePhase> schedule) {
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

        Optional<WEntity> opt = entityService.findByWorldIdAndName(wid, entityId);
        if (opt.isEmpty()) {
            log.warn("Entity not found: worldId={}, entityId={}", worldId, entityId);
            return notFound("entity not found");
        }

        log.debug("Returning entity: entityId={}", entityId);
        return ResponseEntity.ok(toDto(opt.get()));
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
            @Parameter(description = "Filter by epoch") @RequestParam(required = false) Integer epoch,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST entities: worldId={}, query={}, epoch={}, offset={}, limit={}", worldId, query, epoch, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // Get all Entities for this world with query filter
        List<WEntity> all = entityService.findByWorldIdAndQuery(wid, query);

        // Filter by epoch if specified
        if (epoch != null) {
            all = all.stream()
                    .filter(e -> e.getEpoches() != null && e.getEpoches().contains(epoch))
                    .collect(Collectors.toList());
        }

        int totalCount = all.size();

        // Apply pagination
        List<EntityDto> dtoList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());

        log.debug("Returning {} entities (total: {})", dtoList.size(), totalCount);

        return ResponseEntity.ok(Map.of(
                "entities", dtoList,
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
        if (Strings.isBlank(request.entityId())) {
            return bad("entityId required");
        }

        if (request.publicData() == null) {
            return bad("publicData required");
        }

        // Check if Entity already exists
        if (entityService.findByWorldIdAndName(wid, request.entityId()).isPresent()) {
            return conflict("entity already exists");
        }

        try {
            WEntity saved = entityService.save(
                    wid,
                    request.entityId(),
                    request.publicData(),
                    request.modelId()
            );
            if (request.type() != null || request.portraitPath() != null || request.server() != null || request.epoches() != null) {
                entityService.update(wid, request.entityId(), entity -> {
                    if (request.type() != null) {
                        entity.setType(request.type());
                    }
                    if (request.portraitPath() != null) {
                        entity.setPortraitPath(request.portraitPath());
                    }
                    if (request.server() != null) {
                        entity.setServer(request.server());
                    }
                    if (request.epoches() != null) {
                        entity.setEpoches(request.epoches());
                    }
                    if (request.schedule() != null) {
                        entity.setSchedule(request.schedule());
                    }
                });
            }

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

        if (request.publicData() == null && request.modelId() == null && request.enabled() == null && request.type() == null && request.portraitPath() == null) {
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
            if (request.type() != null) {
                entity.setType(request.type());
            }
            if (request.portraitPath() != null) {
                entity.setPortraitPath(request.portraitPath());
            }
            if (request.server() != null) {
                entity.setServer(request.server());
            }
            if (request.epoches() != null) {
                entity.setEpoches(request.epoches());
            }
            if (request.schedule() != null) {
                entity.setSchedule(request.schedule());
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
                entity.getName(),
                entity.getPublicData(),
                entity.getWorldId(),
                entity.getModelId(),
                entity.isEnabled(),
                entity.getType(),
                entity.getPortraitPath(),
                entity.getServer(),
                entity.getEpoches() != null ? entity.getEpoches() : List.of(),
                entity.getSchedule() != null ? entity.getSchedule() : List.of(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

}
