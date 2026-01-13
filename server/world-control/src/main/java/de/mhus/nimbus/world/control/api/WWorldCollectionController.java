package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WWorldCollection;
import de.mhus.nimbus.world.shared.world.WWorldCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing World Collections.
 * World Collections group related worlds and are identified by worldId starting with '@'.
 */
@RestController
@RequestMapping("/control/collections")
@RequiredArgsConstructor
public class WWorldCollectionController extends BaseEditorController {

    private final WWorldCollectionService collectionService;

    // DTOs
    public record CollectionRequest(
            String worldId,
            String title,
            String description
    ) {}

    public record CollectionResponse(
            String id,
            String worldId,
            String title,
            String description,
            Instant createdAt,
            Instant updatedAt,
            boolean enabled
    ) {}

    private CollectionResponse toResponse(WWorldCollection collection) {
        return new CollectionResponse(
                collection.getId(),
                collection.getWorldId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getCreatedAt(),
                collection.getUpdatedAt(),
                collection.isEnabled()
        );
    }

    /**
     * List all world collections.
     * GET /control/collections
     */
    @GetMapping
    public ResponseEntity<?> list() {
        try {
            List<CollectionResponse> result = collectionService.findAll().stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get world collection by worldId.
     * GET /control/collections/{worldId}
     */
    @GetMapping("/{worldId}")
    public ResponseEntity<?> get(@PathVariable String worldId) {
        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        if (!worldId.startsWith("@")) {
            return bad("Collection worldId must start with '@'");
        }

        return collectionService.findByWorldId(worldId)
                .<ResponseEntity<?>>map(collection -> ResponseEntity.ok(toResponse(collection)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Collection not found: " + worldId)));
    }

    /**
     * Create new world collection.
     * POST /control/collections
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CollectionRequest request) {
        if (blank(request.worldId())) {
            return bad("worldId is required");
        }

        if (!request.worldId().startsWith("@")) {
            return bad("Collection worldId must start with '@'");
        }

        if (blank(request.title())) {
            return bad("title is required");
        }

        try {
            WWorldCollection created = collectionService.create(
                    request.worldId(),
                    request.title(),
                    request.description()
            );

            return ResponseEntity.created(URI.create("/control/collections/" + created.getWorldId()))
                    .body(toResponse(created));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update world collection.
     * PUT /control/collections/{worldId}
     */
    @PutMapping("/{worldId}")
    public ResponseEntity<?> update(
            @PathVariable String worldId,
            @RequestBody CollectionRequest request) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        if (!worldId.startsWith("@")) {
            return bad("Collection worldId must start with '@'");
        }

        WWorldCollection existing = collectionService.findByWorldId(worldId).orElse(null);
        if (existing == null) {
            return notFound("Collection not found: " + worldId);
        }

        try {
            if (request.title() != null) existing.setTitle(request.title());
            if (request.description() != null) existing.setDescription(request.description());

            WWorldCollection updated = collectionService.save(existing);
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Delete world collection.
     * DELETE /control/collections/{worldId}
     */
    @DeleteMapping("/{worldId}")
    public ResponseEntity<?> delete(@PathVariable String worldId) {
        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        if (!worldId.startsWith("@")) {
            return bad("Collection worldId must start with '@'");
        }

        if (!collectionService.existsByWorldId(worldId)) {
            return notFound("Collection not found: " + worldId);
        }

        try {
            collectionService.delete(worldId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }
}
