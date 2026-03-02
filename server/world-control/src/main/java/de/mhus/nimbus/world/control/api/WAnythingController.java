package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
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
 * REST Controller for WAnythingEntity CRUD operations.
 * Base path: /control/anything
 * <p>
 * Manages arbitrary data storage scoped by worldId and collection.
 * Region scoping uses worldId format "@region:regionId".
 */
@RestController
@RequestMapping("/control/anything")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Anything", description = "Flexible data storage management")
public class WAnythingController extends BaseEditorController {

    private final WAnythingService anythingService;

    @PostConstruct
    public void init() {
        log.info("WAnythingController initialized");
    }

    // DTOs
    public record AnythingDto(
            String id,
            String worldId,
            String collection,
            String name,
            String title,
            String description,
            String type,
            Object data,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateAnythingRequest(
            String worldId,
            String collection,
            String name,
            String title,
            String description,
            String type,
            Object data
    ) {
    }

    public record UpdateAnythingRequest(
            String title,
            String description,
            String type,
            Object data,
            Boolean enabled
    ) {
    }

    /**
     * Get single entity by worldId, collection, and name.
     * GET /control/anything/by-world?worldId=...&collection=...&name=...
     */
    @GetMapping("/by-world")
    @Operation(summary = "Get entity by world, collection, and name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> getByWorld(
            @Parameter(description = "World identifier (supports @region:regionId)") @RequestParam String worldId,
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("GET anything: worldId={}, collection={}, name={}", worldId, collection, name);

        if (Strings.isBlank(worldId)) return bad("worldId required");
        if (Strings.isBlank(collection)) return bad("collection required");
        if (Strings.isBlank(name)) return bad("name required");

        Optional<WAnything> opt = anythingService.findByWorldIdAndCollectionAndName(worldId, collection, name);
        if (opt.isEmpty()) {
            log.warn("Entity not found: worldId={}, collection={}, name={}", worldId, collection, name);
            return notFound("entity not found");
        }

        return ResponseEntity.ok(toDto(opt.get()));
    }

    /**
     * Get distinct collection names for a world.
     * GET /control/anything/collections?worldId=...
     */
    @GetMapping("/collections")
    @Operation(summary = "Get distinct collection names")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> getCollections(
            @Parameter(description = "World identifier (supports @region:regionId)") @RequestParam String worldId) {

        log.debug("GET collections: worldId={}", worldId);

        if (Strings.isBlank(worldId)) return bad("worldId required");

        List<String> collections = anythingService.findDistinctCollections(worldId);

        return ResponseEntity.ok(Map.of(
                "collections", collections,
                "count", collections.size()
        ));
    }

    /**
     * List all entities in a collection.
     * GET /control/anything/list?worldId=...&collection=...&type=...&offset=0&limit=50
     */
    @GetMapping("/list")
    @Operation(summary = "List entities with flexible filtering")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier (supports @region:regionId)") @RequestParam String worldId,
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Optional type filter") @RequestParam(required = false) String type,
            @Parameter(description = "Only enabled entities") @RequestParam(defaultValue = "true") boolean enabledOnly,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST anything: worldId={}, collection={}, type={}, enabledOnly={}, offset={}, limit={}",
                worldId, collection, type, enabledOnly, offset, limit);

        if (Strings.isBlank(worldId)) return bad("worldId required");
        if (Strings.isBlank(collection)) return bad("collection required");

        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        List<WAnything> all;

        if (!Strings.isBlank(type)) {
            all = anythingService.findByWorldIdAndCollectionAndType(worldId, collection, type);
        } else if (enabledOnly) {
            all = anythingService.findByWorldIdAndCollectionAndEnabled(worldId, collection, true);
        } else {
            all = anythingService.findByWorldIdAndCollection(worldId, collection);
        }

        int totalCount = all.size();

        List<AnythingDto> entityList = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());

        log.debug("Returning {} entities (total: {})", entityList.size(), totalCount);

        return ResponseEntity.ok(Map.of(
                "entities", entityList,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Create new entity.
     * POST /control/anything
     */
    @PostMapping
    @Operation(summary = "Create new entity")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entity created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Entity already exists")
    })
    public ResponseEntity<?> create(@RequestBody CreateAnythingRequest request) {

        log.debug("CREATE anything: worldId={}, collection={}, name={}",
                request.worldId(), request.collection(), request.name());

        if (Strings.isBlank(request.worldId())) return bad("worldId required");
        if (Strings.isBlank(request.collection())) return bad("collection required");
        if (Strings.isBlank(request.name())) return bad("name required");

        try {
            WAnything saved = anythingService.create(
                    request.worldId(), request.collection(),
                    request.name(), request.title(), request.description(), request.type(), request.data());

            log.info("Created entity: worldId={}, collection={}, name={}",
                    request.worldId(), request.collection(), request.name());
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));

        } catch (IllegalStateException e) {
            log.warn("Entity already exists: {}", e.getMessage());
            return conflict(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing entity by ID.
     * PUT /control/anything/{id}
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update entity by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "Entity ID") @PathVariable String id,
            @RequestBody UpdateAnythingRequest request) {

        log.debug("UPDATE anything: id={}", id);

        if (Strings.isBlank(id)) return bad("id required");

        if (request.title() == null && request.description() == null && request.type() == null &&
                request.data() == null && request.enabled() == null) {
            return bad("at least one field required for update");
        }

        Optional<WAnything> updated = anythingService.update(id, entity -> {
            if (request.title() != null) {
                entity.setTitle(request.title());
            }
            if (request.description() != null) {
                entity.setDescription(request.description());
            }
            if (request.type() != null) {
                entity.setType(request.type());
            }
            if (request.data() != null) {
                entity.setData(request.data());
            }
            if (request.enabled() != null) {
                entity.setEnabled(request.enabled());
            }
        });

        if (updated.isEmpty()) {
            log.warn("Entity not found for update: id={}", id);
            return notFound("entity not found");
        }

        log.info("Updated entity: id={}", id);
        return ResponseEntity.ok(toDto(updated.get()));
    }

    /**
     * Delete entity by worldId, collection, and name.
     * DELETE /control/anything/by-world?worldId=...&collection=...&name=...
     */
    @DeleteMapping("/by-world")
    @Operation(summary = "Delete entity by world, collection, and name")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entity deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> deleteByWorld(
            @Parameter(description = "World identifier (supports @region:regionId)") @RequestParam String worldId,
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("DELETE anything: worldId={}, collection={}, name={}", worldId, collection, name);

        if (Strings.isBlank(worldId)) return bad("worldId required");
        if (Strings.isBlank(collection)) return bad("collection required");
        if (Strings.isBlank(name)) return bad("name required");

        anythingService.deleteByWorldIdAndCollectionAndName(worldId, collection, name);
        log.info("Deleted entity: worldId={}, collection={}, name={}", worldId, collection, name);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private AnythingDto toDto(WAnything entity) {
        return new AnythingDto(
                entity.getId(),
                entity.getWorldId(),
                entity.getCollection(),
                entity.getName(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getType(),
                entity.getData(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
