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
 * Manages arbitrary data storage with flexible scoping by region, world, and collection.
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
            String regionId,
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
            String regionId,
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
     * Get single entity by collection and name.
     * GET /control/anything/by-collection?collection=...&name=...
     */
    @GetMapping("/by-collection")
    @Operation(summary = "Get entity by collection and name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> getByCollection(
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("GET anything by collection: collection={}, name={}", collection, name);

        if (blank(collection)) return bad("collection required");
        if (blank(name)) return bad("name required");

        Optional<WAnything> opt = anythingService.findByCollectionAndName(collection, name);
        if (opt.isEmpty()) {
            log.warn("Entity not found: collection={}, name={}", collection, name);
            return notFound("entity not found");
        }

        log.debug("Returning entity: collection={}, name={}", collection, name);
        return ResponseEntity.ok(toDto(opt.get()));
    }

    /**
     * Get single entity by world, collection, and name.
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
            @Parameter(description = "World identifier") @RequestParam String worldId,
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("GET anything by world: worldId={}, collection={}, name={}", worldId, collection, name);

        if (blank(worldId)) return bad("worldId required");
        if (blank(collection)) return bad("collection required");
        if (blank(name)) return bad("name required");

        Optional<WAnything> opt = anythingService.findByWorldIdAndCollectionAndName(worldId, collection, name);
        if (opt.isEmpty()) {
            log.warn("Entity not found: worldId={}, collection={}, name={}", worldId, collection, name);
            return notFound("entity not found");
        }

        log.debug("Returning entity: worldId={}, collection={}, name={}", worldId, collection, name);
        return ResponseEntity.ok(toDto(opt.get()));
    }

    /**
     * Get single entity by region, collection, and name.
     * GET /control/anything/by-region?regionId=...&collection=...&name=...
     */
    @GetMapping("/by-region")
    @Operation(summary = "Get entity by region, collection, and name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> getByRegion(
            @Parameter(description = "Region identifier") @RequestParam String regionId,
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("GET anything by region: regionId={}, collection={}, name={}", regionId, collection, name);

        if (blank(regionId)) return bad("regionId required");
        if (blank(collection)) return bad("collection required");
        if (blank(name)) return bad("name required");

        Optional<WAnything> opt = anythingService.findByRegionIdAndCollectionAndName(regionId, collection, name);
        if (opt.isEmpty()) {
            log.warn("Entity not found: regionId={}, collection={}, name={}", regionId, collection, name);
            return notFound("entity not found");
        }

        log.debug("Returning entity: regionId={}, collection={}, name={}", regionId, collection, name);
        return ResponseEntity.ok(toDto(opt.get()));
    }

    /**
     * Get distinct collection names with optional filtering.
     * GET /control/anything/collections?regionId=...&worldId=...
     */
    @GetMapping("/collections")
    @Operation(summary = "Get distinct collection names")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success")
    })
    public ResponseEntity<?> getCollections(
            @Parameter(description = "Optional region identifier") @RequestParam(required = false) String regionId,
            @Parameter(description = "Optional world identifier") @RequestParam(required = false) String worldId) {

        log.debug("GET collections: regionId={}, worldId={}", regionId, worldId);

        List<String> collections = anythingService.findDistinctCollections(regionId, worldId);

        log.debug("Returning {} collections", collections.size());

        return ResponseEntity.ok(Map.of(
                "collections", collections,
                "count", collections.size()
        ));
    }

    /**
     * List all entities in a collection with optional filters.
     * GET /control/anything/list?collection=...&worldId=...&regionId=...&type=...&offset=0&limit=50
     */
    @GetMapping("/list")
    @Operation(summary = "List entities with flexible filtering")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Optional world identifier") @RequestParam(required = false) String worldId,
            @Parameter(description = "Optional region identifier") @RequestParam(required = false) String regionId,
            @Parameter(description = "Optional type filter") @RequestParam(required = false) String type,
            @Parameter(description = "Only enabled entities") @RequestParam(defaultValue = "true") boolean enabledOnly,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST anything: collection={}, worldId={}, regionId={}, type={}, enabledOnly={}, offset={}, limit={}",
                collection, worldId, regionId, type, enabledOnly, offset, limit);

        if (blank(collection)) return bad("collection required");

        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        List<WAnything> all;

        // Find by scope and type
        if (type != null && !type.isBlank()) {
            if (regionId != null && worldId != null) {
                all = anythingService.findByRegionIdAndWorldIdAndCollectionAndType(regionId, worldId, collection, type);
            } else if (regionId != null) {
                all = anythingService.findByRegionIdAndCollectionAndType(regionId, collection, type);
            } else if (worldId != null) {
                all = anythingService.findByWorldIdAndCollectionAndType(worldId, collection, type);
            } else {
                all = anythingService.findByCollectionAndType(collection, type);
            }
        } else if (enabledOnly) {
            // Find by scope with enabled filter
            if (regionId != null && worldId != null) {
                all = anythingService.findByRegionIdAndWorldIdAndCollectionAndEnabled(regionId, worldId, collection, true);
            } else if (regionId != null) {
                all = anythingService.findByRegionIdAndCollectionAndEnabled(regionId, collection, true);
            } else if (worldId != null) {
                all = anythingService.findByWorldIdAndCollectionAndEnabled(worldId, collection, true);
            } else {
                all = anythingService.findByCollectionAndEnabled(collection, true);
            }
        } else {
            // Find by scope without filters
            if (regionId != null && worldId != null) {
                all = anythingService.findByRegionIdAndWorldIdAndCollection(regionId, worldId, collection);
            } else if (regionId != null) {
                all = anythingService.findByRegionIdAndCollection(regionId, collection);
            } else if (worldId != null) {
                all = anythingService.findByWorldIdAndCollection(worldId, collection);
            } else {
                all = anythingService.findByCollection(collection);
            }
        }

        int totalCount = all.size();

        // Apply pagination
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

        log.debug("CREATE anything: collection={}, name={}, worldId={}, regionId={}",
                request.collection(), request.name(), request.worldId(), request.regionId());

        if (blank(request.collection())) {
            return bad("collection required");
        }

        if (blank(request.name())) {
            return bad("name required");
        }

        try {
            WAnything saved;

            // Create with appropriate scope
            if (request.regionId() != null && request.worldId() != null) {
                saved = anythingService.createWithRegionIdAndWorldId(
                        request.regionId(), request.worldId(), request.collection(),
                        request.name(), request.title(), request.description(), request.type(), request.data());
            } else if (request.regionId() != null) {
                saved = anythingService.createWithRegionId(
                        request.regionId(), request.collection(),
                        request.name(), request.title(), request.description(), request.type(), request.data());
            } else if (request.worldId() != null) {
                saved = anythingService.createWithWorldId(
                        request.worldId(), request.collection(),
                        request.name(), request.title(), request.description(), request.type(), request.data());
            } else {
                saved = anythingService.create(
                        request.collection(), request.name(),
                        request.title(), request.description(), request.type(), request.data());
            }

            log.info("Created entity: collection={}, name={}", request.collection(), request.name());
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

        if (blank(id)) return bad("id required");

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
     * Delete entity by collection and name.
     * DELETE /control/anything/by-collection?collection=...&name=...
     */
    @DeleteMapping("/by-collection")
    @Operation(summary = "Delete entity by collection and name")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entity deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> deleteByCollection(
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("DELETE anything by collection: collection={}, name={}", collection, name);

        if (blank(collection)) return bad("collection required");
        if (blank(name)) return bad("name required");

        anythingService.deleteByCollectionAndName(collection, name);
        log.info("Deleted entity: collection={}, name={}", collection, name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete entity by world, collection, and name.
     * DELETE /control/anything/by-world?worldId=...&collection=...&name=...
     */
    @DeleteMapping("/by-world")
    @Operation(summary = "Delete entity by world, collection, and name")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entity deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> deleteByWorld(
            @Parameter(description = "World identifier") @RequestParam String worldId,
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("DELETE anything by world: worldId={}, collection={}, name={}", worldId, collection, name);

        if (blank(worldId)) return bad("worldId required");
        if (blank(collection)) return bad("collection required");
        if (blank(name)) return bad("name required");

        anythingService.deleteByWorldIdAndCollectionAndName(worldId, collection, name);
        log.info("Deleted entity: worldId={}, collection={}, name={}", worldId, collection, name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete entity by region, collection, and name.
     * DELETE /control/anything/by-region?regionId=...&collection=...&name=...
     */
    @DeleteMapping("/by-region")
    @Operation(summary = "Delete entity by region, collection, and name")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entity deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> deleteByRegion(
            @Parameter(description = "Region identifier") @RequestParam String regionId,
            @Parameter(description = "Collection identifier") @RequestParam String collection,
            @Parameter(description = "Name identifier") @RequestParam String name) {

        log.debug("DELETE anything by region: regionId={}, collection={}, name={}", regionId, collection, name);

        if (blank(regionId)) return bad("regionId required");
        if (blank(collection)) return bad("collection required");
        if (blank(name)) return bad("name required");

        anythingService.deleteByRegionIdAndCollectionAndName(regionId, collection, name);
        log.info("Deleted entity: regionId={}, collection={}, name={}", regionId, collection, name);
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private AnythingDto toDto(WAnything entity) {
        return new AnythingDto(
                entity.getId(),
                entity.getRegionId(),
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
