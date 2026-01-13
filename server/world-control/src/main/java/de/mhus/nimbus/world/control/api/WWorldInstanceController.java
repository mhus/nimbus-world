package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing World Instances.
 * World Instances are copies of worlds that can be played independently.
 * This controller is for EDITOR use only - instances can only be viewed and deleted here.
 * Creation happens through game mechanics.
 */
@RestController
@RequestMapping("/control/instances")
@RequiredArgsConstructor
public class WWorldInstanceController extends BaseEditorController {

    private final WWorldInstanceService instanceService;

    // DTOs
    public record InstanceResponse(
            String id,
            String instanceId,
            String worldId,
            String title,
            String description,
            String creator,
            List<String> players,
            Instant createdAt,
            Instant updatedAt,
            boolean enabled
    ) {}

    private InstanceResponse toResponse(WWorldInstance instance) {
        return new InstanceResponse(
                instance.getId(),
                instance.getInstanceId(),
                instance.getWorldId(),
                instance.getTitle(),
                instance.getDescription(),
                instance.getCreator(),
                instance.getPlayers(),
                instance.getCreatedAt(),
                instance.getUpdatedAt(),
                instance.isEnabled()
        );
    }

    /**
     * List all world instances with optional filtering.
     * GET /control/instances
     *
     * Query parameters:
     * - worldId: Filter by worldId
     * - creator: Filter by creator playerId
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String worldId,
            @RequestParam(required = false) String creator) {

        try {
            List<WWorldInstance> instances;

            if (worldId != null && !worldId.isBlank()) {
                instances = instanceService.findByWorldId(worldId);
            } else if (creator != null && !creator.isBlank()) {
                instances = instanceService.findByCreator(creator);
            } else {
                instances = instanceService.findAll();
            }

            List<InstanceResponse> result = instances.stream()
                    .map(this::toResponse)
                    .toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get world instance by instanceId.
     * GET /control/instances/{instanceId}
     */
    @GetMapping("/{instanceId}")
    public ResponseEntity<?> get(@PathVariable String instanceId) {
        var error = validateId(instanceId, "instanceId");
        if (error != null) return error;

        return instanceService.findByInstanceId(instanceId)
                .<ResponseEntity<?>>map(instance -> ResponseEntity.ok(toResponse(instance)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Instance not found: " + instanceId)));
    }

    /**
     * Delete world instance.
     * DELETE /control/instances/{instanceId}
     */
    @DeleteMapping("/{instanceId}")
    public ResponseEntity<?> delete(@PathVariable String instanceId) {
        var error = validateId(instanceId, "instanceId");
        if (error != null) return error;

        if (!instanceService.existsByInstanceId(instanceId)) {
            return notFound("Instance not found: " + instanceId);
        }

        try {
            instanceService.delete(instanceId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get statistics about instances.
     * GET /control/instances/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @RequestParam(required = false) String worldId,
            @RequestParam(required = false) String creator) {

        try {
            long count;

            if (worldId != null && !worldId.isBlank()) {
                count = instanceService.countByWorldId(worldId);
            } else if (creator != null && !creator.isBlank()) {
                count = instanceService.countByCreator(creator);
            } else {
                count = instanceService.findAll().size();
            }

            Map<String, Object> stats = Map.of(
                    "totalCount", count,
                    "worldId", worldId != null ? worldId : "",
                    "creator", creator != null ? creator : ""
            );

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }
}
