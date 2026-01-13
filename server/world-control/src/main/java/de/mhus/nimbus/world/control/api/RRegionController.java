package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.region.RRegion;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WWorldCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing RRegion entities.
 * Provides CRUD operations and maintainer management.
 */
@RestController
@RequestMapping("/control/regions")
@RequiredArgsConstructor
@Slf4j
public class RRegionController extends BaseEditorController {

    private final RRegionService regionService;
    private final WWorldCollectionService collectionService;

    // DTOs
    public record RegionRequest(String name, String maintainers) {}
    public record RegionResponse(String id, String name, boolean enabled, List<String> maintainers) {}
    public record MaintainerRequest(String userId) {}

    private RegionResponse toResponse(RRegion region) {
        List<String> maintainers = region.getMaintainers().stream().toList();
        return new RegionResponse(region.getId(), region.getName(), region.isEnabled(), maintainers);
    }

    /**
     * List all regions with optional filtering
     * GET /control/region?name=...&enabled=true
     */
    @GetMapping
    public ResponseEntity<List<RegionResponse>> list(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {

        List<RegionResponse> result = regionService.listAll().stream()
                .filter(r -> name == null || name.equals(r.getName()))
                .filter(r -> enabled == null || r.isEnabled() == enabled)
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Get region by ID
     * GET /control/region/{regionId}
     */
    @GetMapping("/{regionId}")
    public ResponseEntity<?> get(@PathVariable String regionId) {
        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        return regionService.getById(regionId)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok(toResponse(r)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Region not found: " + regionId)));
    }

    /**
     * Create new region
     * POST /control/region
     * Automatically creates @public:<regionId> and @region:<regionId> collections
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody RegionRequest request) {
        if (blank(request.name())) {
            return bad("name is required");
        }

        try {
            RRegion created = regionService.create(request.name(), request.maintainers());

            // Auto-create world collections for this region
            String regionId = created.getName();

            // Create @public:<regionId> collection
            String publicCollectionId = "@public:" + regionId;
            try {
                collectionService.create(
                        publicCollectionId,
                        "Public Collection - " + regionId,
                        "Auto-created public collection for region " + regionId
                );
                log.info("Created public collection for region {}: {}", regionId, publicCollectionId);
            } catch (IllegalStateException e) {
                // Collection already exists, that's ok
                log.debug("Public collection already exists: {}", publicCollectionId);
            }

            // Create @region:<regionId> collection
            String regionCollectionId = "@region:" + regionId;
            try {
                collectionService.create(
                        regionCollectionId,
                        "Region Collection - " + regionId,
                        "Auto-created region collection for region " + regionId
                );
                log.info("Created region collection for region {}: {}", regionId, regionCollectionId);
            } catch (IllegalStateException e) {
                // Collection already exists, that's ok
                log.debug("Region collection already exists: {}", regionCollectionId);
            }

            return ResponseEntity.created(URI.create("/control/region/" + created.getId()))
                    .body(toResponse(created));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update region
     * PUT /control/region/{regionId}?enabled=true
     */
    @PutMapping("/{regionId}")
    public ResponseEntity<?> update(
            @PathVariable String regionId,
            @RequestBody RegionRequest request,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        if (regionService.getById(regionId).isEmpty()) {
            return notFound("Region not found: " + regionId);
        }

        try {
            RRegion updated = regionService.updateFull(regionId, request.name(), request.maintainers(), enabled);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Delete region
     * DELETE /control/region/{regionId}
     */
    @DeleteMapping("/{regionId}")
    public ResponseEntity<?> delete(@PathVariable String regionId) {
        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        if (regionService.getById(regionId).isEmpty()) {
            return notFound("Region not found: " + regionId);
        }

        regionService.delete(regionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Enable region
     * POST /control/region/{regionId}/enable
     */
    @PostMapping("/{regionId}/enable")
    public ResponseEntity<?> enable(@PathVariable String regionId) {
        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        try {
            RRegion updated = regionService.setEnabled(regionId, true);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * Disable region
     * POST /control/region/{regionId}/disable
     */
    @PostMapping("/{regionId}/disable")
    public ResponseEntity<?> disable(@PathVariable String regionId) {
        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        try {
            RRegion updated = regionService.setEnabled(regionId, false);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * Add maintainer
     * POST /control/region/{regionId}/maintainers
     */
    @PostMapping("/{regionId}/maintainers")
    public ResponseEntity<?> addMaintainer(
            @PathVariable String regionId,
            @RequestBody MaintainerRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        if (blank(request.userId())) {
            return bad("userId is required");
        }

        try {
            RRegion updated = regionService.addMaintainer(regionId, request.userId().trim());
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * Remove maintainer
     * DELETE /control/region/{regionId}/maintainers/{userId}
     */
    @DeleteMapping("/{regionId}/maintainers/{userId}")
    public ResponseEntity<?> removeMaintainer(
            @PathVariable String regionId,
            @PathVariable String userId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        if (blank(userId)) {
            return bad("userId is required");
        }

        try {
            RRegion updated = regionService.removeMaintainer(regionId, userId);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * List maintainers
     * GET /control/region/{regionId}/maintainers
     */
    @GetMapping("/{regionId}/maintainers")
    public ResponseEntity<?> listMaintainers(@PathVariable String regionId) {
        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        return regionService.getById(regionId)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok(r.getMaintainers().stream().toList()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Region not found: " + regionId)));
    }
}
