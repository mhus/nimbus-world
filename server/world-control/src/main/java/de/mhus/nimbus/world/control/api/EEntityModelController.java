package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WEntityModel;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
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
 * REST Controller for EntityModel CRUD operations.
 * Base path: /control/worlds/{worldId}/entitymodels
 * <p>
 * EntityModels define 3D models, animations, and physics properties for entities.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/entitymodels")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "EntityModels", description = "EntityModel template management")
public class EEntityModelController extends BaseEditorController {

    private final WEntityModelService entityModelService;

    // DTOs
    public record EntityModelDto(
            String modelId,
            EntityModel publicData,
            String worldId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateEntityModelRequest(String modelId, EntityModel publicData) {
    }

    public record UpdateEntityModelRequest(EntityModel publicData, Boolean enabled) {
    }

    /**
     * Get single EntityModel by ID.
     * GET /control/worlds/{worldId}/entitymodel/{modelId}
     */
    @GetMapping("/{modelId}")
    @Operation(summary = "Get EntityModel by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "EntityModel found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "EntityModel not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Model identifier") @PathVariable String modelId) {

        log.debug("GET entitymodel: worldId={}, modelId={}", worldId, modelId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(modelId, "modelId");
        if (validation != null) return validation;

        Optional<WEntityModel> opt = entityModelService.findByModelId(wid, modelId);
        if (opt.isEmpty()) {
            log.warn("EntityModel not found: modelId={}", modelId);
            return notFound("entitymodel not found");
        }

        log.debug("Returning entitymodel: modelId={}", modelId);
        // Return publicData only (match test_server format)
        return ResponseEntity.ok(opt.get().getPublicData());
    }

    /**
     * List all EntityModels for a world with optional search filter and pagination.
     * GET /control/worlds/{worldId}/entitymodel?query=...&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List all EntityModels")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST entitymodels: worldId={}, query={}, offset={}, limit={}", worldId, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // Get all EntityModels for this world with query filter
        List<WEntityModel> all = entityModelService.findByWorldIdAndQuery(wid, query);

        int totalCount = all.size();

        // Apply pagination
        List<EntityModel> publicDataList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(WEntityModel::getPublicData)
                .collect(Collectors.toList());

        log.debug("Returning {} entitymodels (total: {})", publicDataList.size(), totalCount);

        // TypeScript compatible format (match test_server response)
        return ResponseEntity.ok(Map.of(
                "entityModels", publicDataList,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Create new EntityModel.
     * POST /control/worlds/{worldId}/entitymodel
     */
    @PostMapping
    @Operation(summary = "Create new EntityModel")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "EntityModel created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "EntityModel already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateEntityModelRequest request) {

        log.debug("CREATE entitymodel: worldId={}, modelId={}", worldId, request.modelId());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(request.modelId())) {
            return bad("modelId required");
        }

        if (request.publicData() == null) {
            return bad("publicData required");
        }

        // Check if EntityModel already exists
        if (entityModelService.findByModelId(wid, request.modelId()).isPresent()) {
            return conflict("entitymodel already exists");
        }

        try {
            WEntityModel saved = entityModelService.save(wid, request.modelId(), request.publicData());
            log.info("Created entitymodel: modelId={}", request.modelId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating entitymodel: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating entitymodel", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing EntityModel.
     * PUT /control/worlds/{worldId}/entitymodel/{modelId}
     */
    @PutMapping("/{modelId}")
    @Operation(summary = "Update EntityModel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "EntityModel updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "EntityModel not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Model identifier") @PathVariable String modelId,
            @RequestBody UpdateEntityModelRequest request) {

        log.debug("UPDATE entitymodel: worldId={}, modelId={}", worldId, modelId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(modelId, "modelId");
        if (validation != null) return validation;

        if (request.publicData() == null && request.enabled() == null) {
            return bad("at least one field required for update");
        }

        Optional<WEntityModel> updated = entityModelService.update(wid, modelId, model -> {
            if (request.publicData() != null) {
                model.setPublicData(request.publicData());
            }
            if (request.enabled() != null) {
                model.setEnabled(request.enabled());
            }
            model.setWorldId(worldId);
        });

        if (updated.isEmpty()) {
            log.warn("EntityModel not found for update: modelId={}", modelId);
            return notFound("entitymodel not found");
        }

        log.info("Updated entitymodel: modelId={}", modelId);
        return ResponseEntity.ok(toDto(updated.get()));
    }

    /**
     * Delete EntityModel.
     * DELETE /control/worlds/{worldId}/entitymodel/{modelId}
     */
    @DeleteMapping("/{modelId}")
    @Operation(summary = "Delete EntityModel")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "EntityModel deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "EntityModel not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Model identifier") @PathVariable String modelId) {

        log.debug("DELETE entitymodel: worldId={}, modelId={}", worldId, modelId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(modelId, "modelId");
        if (validation != null) return validation;

        boolean deleted = entityModelService.delete(wid, modelId);
        if (!deleted) {
            log.warn("EntityModel not found for deletion: modelId={}", modelId);
            return notFound("entitymodel not found");
        }

        log.info("Deleted entitymodel: modelId={}", modelId);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private EntityModelDto toDto(WEntityModel entity) {
        return new EntityModelDto(
                entity.getModelId(),
                entity.getPublicData(),
                entity.getWorldId(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
