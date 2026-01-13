package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.dto.CreateLayerRequest;
import de.mhus.nimbus.world.shared.dto.LayerDto;
import de.mhus.nimbus.world.shared.dto.UpdateLayerRequest;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
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
 * REST Controller for Layer CRUD operations.
 * Base path: /control/worlds/{worldId}/layers
 * <p>
 * Layers are used to organize and manage world content in separate overlays.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/layers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Layers", description = "Layer management for world content organization")
public class ELayerController extends BaseEditorController {

    private final WLayerService layerService;
    private final de.mhus.nimbus.world.shared.job.WJobService jobService;
    private final de.mhus.nimbus.world.shared.layer.WDirtyChunkService dirtyChunkService;
    private final de.mhus.nimbus.world.shared.world.WChunkRepository chunkRepository;

    // DTOs moved to de.mhus.nimbus.world.shared.dto package for TypeScript generation

    /**
     * Get single Layer by ID.
     * GET /control/worlds/{worldId}/layers/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get Layer by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Layer found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Layer not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Layer identifier") @PathVariable String id) {

        log.debug("GET layer: worldId={}, id={}", worldId, id);

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLayer> opt = layerService.findById(id);
        if (opt.isEmpty()) {
            log.warn("Layer not found: id={}", id);
            return notFound("layer not found");
        }

        WLayer layer = opt.get();
        if (!layer.getWorldId().equals(worldId)) {
            log.warn("Layer worldId mismatch: expected={}, actual={}", worldId, layer.getWorldId());
            return notFound("layer not found");
        }

        log.debug("Returning layer: id={}", id);
        return ResponseEntity.ok(toDto(layer));
    }

    /**
     * List all Layers for a world with optional search filter and pagination.
     * GET /control/worlds/{worldId}/layers?query=...&offset=0&limit=50
     */
    @GetMapping
    @Operation(summary = "List all Layers")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Pagination offset") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Pagination limit") @RequestParam(defaultValue = "50") int limit) {

        log.debug("LIST layers: worldId={}, query={}, offset={}, limit={}", worldId, query, offset, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validatePagination(offset, limit);
        if (validation != null) return validation;

        // IMPORTANT: Filter out instances - layers are per world/zone only
        String lookupWorldId = wid.withoutInstance().getId();

        // Get all Layers for this world with query filter
        List<WLayer> all = layerService.findByWorldIdAndQuery(lookupWorldId, query);

        int totalCount = all.size();

        // Apply pagination
        List<LayerDto> layerDtos = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());

        log.debug("Returning {} layers (total: {})", layerDtos.size(), totalCount);

        return ResponseEntity.ok(Map.of(
                "layers", layerDtos,
                "count", totalCount,
                "limit", limit,
                "offset", offset
        ));
    }

    /**
     * Create new Layer.
     * POST /control/worlds/{worldId}/layers
     */
    @PostMapping
    @Operation(summary = "Create new Layer")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Layer created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Layer name already exists")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @RequestBody CreateLayerRequest request) {

        log.debug("CREATE layer: worldId={}, name={}", worldId, request.name());

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        if (blank(request.name())) {
            return bad("name required");
        }

        if (request.layerType() == null) {
            return bad("layerType required");
        }

        // IMPORTANT: Filter out instances - layers are per world/zone only
        String lookupWorldId = wid.withoutInstance().getId();

        // Check if Layer with same name already exists
        if (layerService.findByWorldIdAndName(lookupWorldId, request.name()).isPresent()) {
            return conflict("layer name already exists");
        }

        try {
            // Use service method which generates layerDataId automatically
            WLayer layer = layerService.createLayer(
                    lookupWorldId,
                    request.name(),
                    request.layerType(),
                    request.order() != null ? request.order() : 0,
                    request.allChunks() != null ? request.allChunks() : true,
                    request.affectedChunks(),
                    request.baseGround() != null ? request.baseGround() : false
            );

            // Set enabled flag if provided
            if (request.enabled() != null) {
                layer.setEnabled(request.enabled());
                layer = layerService.save(layer);
            }

            // Set groups if provided
            if (request.groups() != null) {
                layer.setGroups(request.groups());
                layer = layerService.save(layer);
            }

            log.info("Created layer: id={}, name={}, type={}, layerDataId={}",
                    layer.getId(), layer.getName(), layer.getLayerType(), layer.getLayerDataId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", layer.getId()));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating layer: {}", e.getMessage());
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating layer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Update existing Layer.
     * PUT /control/worlds/{worldId}/layers/{id}
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update Layer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Layer updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Layer not found")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Layer identifier") @PathVariable String id,
            @RequestBody UpdateLayerRequest request) {

        log.debug("UPDATE layer: worldId={}, id={}", worldId, id);

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLayer> opt = layerService.findById(id);
        if (opt.isEmpty()) {
            log.warn("Layer not found for update: id={}", id);
            return notFound("layer not found");
        }

        WLayer layer = opt.get();
        if (!layer.getWorldId().equals(worldId)) {
            log.warn("Layer worldId mismatch: expected={}, actual={}", worldId, layer.getWorldId());
            return notFound("layer not found");
        }

        // Apply updates
        boolean changed = false;
        if (request.name() != null && !request.name().isBlank()) {
            layer.setName(request.name());
            changed = true;
        }
        // Note: mountX/Y/Z are now in WLayerModel, not WLayer
        // groups is available both in WLayer (for GROUND layers) and WLayerModel (for MODEL layers)
        if (request.allChunks() != null) {
            layer.setAllChunks(request.allChunks());
            changed = true;
        }
        if (request.affectedChunks() != null) {
            layer.setAffectedChunks(request.affectedChunks());
            changed = true;
        }
        if (request.order() != null) {
            layer.setOrder(request.order());
            changed = true;
        }
        if (request.enabled() != null) {
            layer.setEnabled(request.enabled());
            changed = true;
        }
        if (request.baseGround() != null) {
            layer.setBaseGround(request.baseGround());
            changed = true;
        }
        if (request.groups() != null) {
            layer.setGroups(request.groups());
            changed = true;
        }

        if (!changed) {
            return bad("at least one field required for update");
        }

        layer.touchUpdate();
        WLayer updated = layerService.save(layer);

        log.info("Updated layer: id={}, name={}", id, updated.getName());
        return ResponseEntity.ok(toDto(updated));
    }

    /**
     * Regenerate Layer.
     * Triggers complete regeneration of layer data.
     * - For MODEL layers: Creates job with executor "recreate-model-based-layer"
     * - For GROUND layers: Marks all affected chunks as dirty
     * POST /control/worlds/{worldId}/layers/{id}/regenerate
     */
    @PostMapping("/{id}/regenerate")
    @Operation(summary = "Regenerate Layer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Layer regeneration triggered"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Layer not found")
    })
    public ResponseEntity<?> regenerate(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Layer identifier") @PathVariable String id) {

        log.debug("REGENERATE layer: worldId={}, id={}", worldId, id);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLayer> opt = layerService.findById(id);
        if (opt.isEmpty()) {
            log.warn("Layer not found for regeneration: id={}", id);
            return notFound("layer not found");
        }

        WLayer layer = opt.get();

        // IMPORTANT: Filter out instances - layers are per world only
        String lookupWorldId = wid.withoutInstance().getId();
        if (!layer.getWorldId().equals(lookupWorldId)) {
            log.warn("Layer worldId mismatch: expected={}, actual={}", lookupWorldId, layer.getWorldId());
            return notFound("layer not found");
        }

        try {
            if (layer.getLayerType() == de.mhus.nimbus.world.shared.layer.LayerType.MODEL) {
                // For MODEL layers: Create job
                de.mhus.nimbus.world.shared.job.WJob job = jobService.createJob(
                        lookupWorldId,
                        "recreate-model-based-layer",
                        "layer-regeneration",
                        Map.of(
                                "layerDataId", layer.getLayerDataId(),
                                "markChunksDirty", "true"
                        ),
                        8, // High priority
                        3  // Max retries
                );

                log.info("Created regeneration job for MODEL layer: layerId={} jobId={}", id, job.getId());

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "layerType", "MODEL",
                        "jobId", job.getId(),
                        "message", "Regeneration job created successfully"
                ));

            } else {
                // For GROUND layers: Mark all affected chunks as dirty
                List<String> affectedChunks;
                if (layer.isAllChunks()) {
                    // Get all existing chunks for this world
                    affectedChunks = chunkRepository.findByWorldId(lookupWorldId)
                            .stream()
                            .map(de.mhus.nimbus.world.shared.world.WChunk::getChunk)
                            .collect(java.util.stream.Collectors.toList());
                    log.info("Regenerating GROUND layer with allChunks=true: layerId={}, chunks={}", id, affectedChunks.size());
                } else {
                    affectedChunks = layer.getAffectedChunks();
                }

                if (affectedChunks.isEmpty()) {
                    log.warn("No chunks to regenerate for GROUND layer: layerId={}", id);
                    return bad("No chunks found to regenerate.");
                }

                dirtyChunkService.markChunksDirty(lookupWorldId, affectedChunks, "layer_regeneration");

                log.info("Marked {} chunks dirty for GROUND layer: layerId={}", affectedChunks.size(), id);

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "layerType", "GROUND",
                        "chunksMarked", affectedChunks.size(),
                        "message", "Chunks marked for regeneration successfully"
                ));
            }

        } catch (Exception e) {
            log.error("Failed to trigger layer regeneration: layerId={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to trigger regeneration: " + e.getMessage()));
        }
    }

    /**
     * Delete Layer.
     * DELETE /control/worlds/{worldId}/layers/{id}
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Layer")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Layer deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Layer not found")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Layer identifier") @PathVariable String id) {

        log.debug("DELETE layer: worldId={}, id={}", worldId, id);

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(id, "id");
        if (validation != null) return validation;

        Optional<WLayer> opt = layerService.findById(id);
        if (opt.isEmpty()) {
            log.warn("Layer not found for deletion: id={}", id);
            return notFound("layer not found");
        }

        WLayer layer = opt.get();
        if (!layer.getWorldId().equals(worldId)) {
            log.warn("Layer worldId mismatch: expected={}, actual={}", worldId, layer.getWorldId());
            return notFound("layer not found");
        }

        layerService.delete(id);

        log.info("Deleted layer: id={}, name={}", id, layer.getName());
        return ResponseEntity.noContent().build();
    }

    // Helper methods

    private LayerDto toDto(WLayer layer) {
        return new LayerDto(
                layer.getId(),
                layer.getWorldId(),
                layer.getName(),
                layer.getLayerType(),
                layer.getLayerDataId(),
                layer.isAllChunks(),
                layer.getAffectedChunks(),
                layer.getOrder(),
                layer.isEnabled(),
                layer.isBaseGround(),
                layer.getGroups(),
                layer.getCreatedAt(),
                layer.getUpdatedAt()
        );
    }
}
