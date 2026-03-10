package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.*;
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
    private final WWorldService worldService;

    // DTOs
    public record InstanceResponse(
            String id,
            String instanceId,
            String worldId,
            String title,
            String description,
            String creator,
            List<String> players,
            List<String> activePlayers,
            InstanceAccessType accessType,
            InstanceDurationType durationType,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt,
            boolean enabled,
            int epoch
    ) {}

    public record InstanceUpdateRequest(
            String title,
            String description,
            InstanceAccessType accessType,
            InstanceDurationType durationType,
            Instant expiresAt,
            Boolean enabled
    ) {}

    private InstanceResponse toResponse(WWorldInstance instance) {
        return new InstanceResponse(
                instance.getId(),
                instance.getInstanceId(),
                instance.getWorldId(),
                instance.getTitle(),
                instance.getDescription(),
                instance.getCreator(),
                instance.getPlayers() != null ? instance.getPlayers() : List.of(),
                instance.getActivePlayers() != null ? instance.getActivePlayers() : List.of(),
                instance.getAccessType() != null ? instance.getAccessType() : InstanceAccessType.PRIVATE,
                instance.getDurationType() != null ? instance.getDurationType() : InstanceDurationType.SHORT,
                instance.getExpiresAt(),
                instance.getCreatedAt(),
                instance.getUpdatedAt(),
                instance.isEnabled(),
                instance.getEpoch()
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
     * Update world instance properties.
     * PUT /control/instances/{instanceId}
     */
    @PutMapping("/{instanceId}")
    public ResponseEntity<?> update(
            @PathVariable String instanceId,
            @RequestBody InstanceUpdateRequest request) {

        var error = validateId(instanceId, "instanceId");
        if (error != null) return error;

        try {
            var updated = instanceService.update(instanceId, instance -> {
                if (request.title() != null) instance.setTitle(request.title());
                if (request.description() != null) instance.setDescription(request.description());
                if (request.accessType() != null) instance.setAccessType(request.accessType());
                if (request.durationType() != null) instance.setDurationType(request.durationType());
                if (request.expiresAt() != null) instance.setExpiresAt(request.expiresAt());
                if (request.enabled() != null) instance.setEnabled(request.enabled());
            });

            return updated
                    .<ResponseEntity<?>>map(instance -> ResponseEntity.ok(toResponse(instance)))
                    .orElseGet(() -> notFound("Instance not found: " + instanceId));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
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
     * Switch epoch for a world instance.
     * PUT /control/instances/{instanceId}/epoch
     * Validates that the epoch exists in the world's epoch definitions.
     */
    @PutMapping("/{instanceId}/epoch")
    public ResponseEntity<?> switchEpoch(
            @PathVariable String instanceId,
            @RequestBody Map<String, Integer> body) {

        var error = validateId(instanceId, "instanceId");
        if (error != null) return error;

        Integer newEpoch = body.get("epoch");
        if (newEpoch == null) {
            return bad("epoch is required");
        }

        try {
            // Find instance to get worldId
            var instanceOpt = instanceService.findByInstanceId(instanceId);
            if (instanceOpt.isEmpty()) {
                return notFound("Instance not found: " + instanceId);
            }

            // Validate epoch exists in world definition
            String worldId = instanceOpt.get().getWorldId();
            var worldOpt = worldService.getByWorldId(worldId);
            if (worldOpt.isEmpty()) {
                return bad("World not found: " + worldId);
            }

            List<WEpochMeta> epoches = worldOpt.get().getEpoches();
            boolean epochExists = epoches != null && epoches.stream()
                    .anyMatch(e -> e.getEpoch() == newEpoch);
            if (!epochExists) {
                return bad("Epoch " + newEpoch + " does not exist in world " + worldId);
            }

            boolean updated = instanceService.switchInstanceEpoch(instanceId, newEpoch);
            if (!updated) {
                return bad("Epoch switch failed for instance: " + instanceId);
            }

            // Return updated instance
            var updatedInstance = instanceService.findByInstanceId(instanceId);
            return updatedInstance
                    .<ResponseEntity<?>>map(inst -> ResponseEntity.ok(toResponse(inst)))
                    .orElseGet(() -> bad("Instance disappeared after epoch switch"));
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
                count = instanceService.count();
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
