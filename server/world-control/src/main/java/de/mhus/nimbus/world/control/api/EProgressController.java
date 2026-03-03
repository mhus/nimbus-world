package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WProgress;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for WProgress CRUD operations (Editor).
 * Base path: /control/worlds/{worldId}/progress
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/progress")
@RequiredArgsConstructor
@Slf4j
public class EProgressController extends BaseEditorController {

    private final WProgressService progressService;

    // DTOs
    public record ProgressResponse(
            String id,
            String worldId,
            String playerId,
            String quest,
            String type,
            Map<String, Object> progressData,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ProgressRequest(
            String playerId,
            String quest,
            String type,
            Map<String, Object> progressData
    ) {}

    private ProgressResponse toResponse(WProgress p) {
        return new ProgressResponse(
                p.getId(),
                p.getWorldId(),
                p.getPlayerId(),
                p.getQuest(),
                p.getType(),
                p.getProgressData(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    /**
     * List all progress entries for a world with optional filters and pagination.
     * GET /control/worlds/{worldId}/progress?playerId=...&type=...&quest=...&query=...&offset=0&limit=50
     */
    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable String worldId,
            @RequestParam(required = false) String playerId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String quest,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        try {
            List<WProgress> all;

            if (Strings.isNotBlank(playerId) && Strings.isNotBlank(type)) {
                all = progressService.findByWorldIdAndPlayerIdAndType(worldId, playerId, type);
            } else if (Strings.isNotBlank(playerId) && Strings.isNotBlank(quest)) {
                all = progressService.findByWorldIdAndPlayerIdAndQuest(worldId, playerId, quest);
            } else if (Strings.isNotBlank(playerId)) {
                all = progressService.findByWorldIdAndPlayerId(worldId, playerId);
            } else {
                all = progressService.findByWorldId(worldId);
            }

            // Apply text search filter
            if (Strings.isNotBlank(query)) {
                String q = query.toLowerCase();
                all = all.stream()
                        .filter(p ->
                                (p.getPlayerId() != null && p.getPlayerId().toLowerCase().contains(q)) ||
                                (p.getType() != null && p.getType().toLowerCase().contains(q)) ||
                                (p.getQuest() != null && p.getQuest().toLowerCase().contains(q))
                        )
                        .collect(Collectors.toList());
            }

            int totalCount = all.size();

            List<ProgressResponse> page = all.stream()
                    .skip(offset)
                    .limit(limit)
                    .map(this::toResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "items", page,
                    "count", totalCount,
                    "limit", limit,
                    "offset", offset
            ));
        } catch (Exception e) {
            log.error("Failed to list progress", e);
            return bad(e.getMessage());
        }
    }

    /**
     * Get single progress entry by ID.
     * GET /control/worlds/{worldId}/progress/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> get(
            @PathVariable String worldId,
            @PathVariable String id) {

        var validation = validateId(id, "id");
        if (validation != null) return validation;

        return progressService.findById(id)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(toResponse(p)))
                .orElseGet(() -> notFound("Progress not found: " + id));
    }

    /**
     * Create or update progress entry.
     * POST /control/worlds/{worldId}/progress
     */
    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String worldId,
            @RequestBody ProgressRequest request) {

        if (Strings.isBlank(request.playerId())) {
            return bad("playerId is required");
        }
        if (Strings.isBlank(request.type())) {
            return bad("type is required");
        }

        try {
            WProgress saved = progressService.save(
                    worldId,
                    request.playerId(),
                    request.type(),
                    request.quest(),
                    request.progressData()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create progress", e);
            return bad(e.getMessage());
        }
    }

    /**
     * Update progress entry by ID.
     * PUT /control/worlds/{worldId}/progress/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String worldId,
            @PathVariable String id,
            @RequestBody ProgressRequest request) {

        var validation = validateId(id, "id");
        if (validation != null) return validation;

        var existing = progressService.findById(id);
        if (existing.isEmpty()) {
            return notFound("Progress not found: " + id);
        }

        try {
            WProgress progress = existing.get();
            if (request.playerId() != null) progress.setPlayerId(request.playerId());
            if (request.type() != null) progress.setType(request.type());
            if (request.quest() != null) progress.setQuest(request.quest());
            if (request.progressData() != null) progress.setProgressData(request.progressData());
            progress.touchUpdate();

            WProgress saved = progressService.save(
                    progress.getWorldId(),
                    progress.getPlayerId(),
                    progress.getType(),
                    progress.getQuest(),
                    progress.getProgressData()
            );
            return ResponseEntity.ok(toResponse(saved));
        } catch (Exception e) {
            log.error("Failed to update progress", e);
            return bad(e.getMessage());
        }
    }

    /**
     * Delete progress entry.
     * DELETE /control/worlds/{worldId}/progress/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String worldId,
            @PathVariable String id) {

        var validation = validateId(id, "id");
        if (validation != null) return validation;

        if (!progressService.delete(id)) {
            return notFound("Progress not found: " + id);
        }

        return ResponseEntity.noContent().build();
    }
}
