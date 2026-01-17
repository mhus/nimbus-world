package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REST Controller for managing WWorld entities.
 * Provides CRUD operations for worlds within regions.
 */
@RestController
@RequestMapping("/control/regions/{regionId}/worlds")
@RequiredArgsConstructor
@Slf4j
public class WWorldController extends BaseEditorController {

    private final WWorldService worldService;
    private final de.mhus.nimbus.shared.engine.EngineMapper engineMapper;
    private final de.mhus.nimbus.world.shared.job.WJobService jobService;

    // DTOs
    public record WorldRequest(
            String worldId,
            String title,
            String description,
            WorldInfo publicData,
            Boolean enabled,
            Boolean instanceable,
            Set<String> owner,
            Set<String> editor,
            Set<String> supporter,
            Set<String> player,
            Integer groundLevel,
            Integer waterLevel,
            String groundBlockType,
            String waterBlockType
    ) {}

    public record WorldResponse(
            String id,
            String worldId,
            String regionId,
            String title,
            String description,
            WorldInfo publicData,
            Instant createdAt,
            Instant updatedAt,
            boolean enabled,
            boolean instanceable,
            int groundLevel,
            Integer waterLevel,
            String groundBlockType,
            String waterBlockType,
            Set<String> owner,
            Set<String> editor,
            Set<String> supporter,
            Set<String> player,
            boolean publicFlag
    ) {}

    public record WorldCreateResponse(
            String id,
            String worldId,
            String regionId,
            String title,
            String description,
            WorldInfo publicData,
            Instant createdAt,
            Instant updatedAt,
            boolean enabled,
            boolean instanceable,
            int groundLevel,
            Integer waterLevel,
            String groundBlockType,
            String waterBlockType,
            Set<String> owner,
            Set<String> editor,
            Set<String> supporter,
            Set<String> player,
            boolean publicFlag,
            String jobId
    ) {}

    private WorldResponse toResponse(WWorld world) {
        // Get title from publicData if available
        String title = null;
        if (world.getPublicData() != null && world.getPublicData().getTitle() != null) {
            title = world.getPublicData().getTitle();
        }

        return new WorldResponse(
                world.getId(),
                world.getWorldId(),
                world.getRegionId(),
                title,
                world.getDescription(),
                world.getPublicData(),
                world.getCreatedAt(),
                world.getUpdatedAt(),
                world.isEnabled(),
                world.isInstanceable(),
                world.getGroundLevel(),
                world.getWaterLevel(),
                world.getGroundBlockType(),
                world.getWaterBlockType(),
                world.getOwner(),
                world.getEditor(),
                world.getSupporter(),
                world.getPlayer(),
                world.isPublicFlag()
        );
    }

    private WorldResponse toResponseFromWorldId(WorldId worldId) {
        // Try to get title from WorldCollectionDto
        String title = worldService.getWorldCollectionTitle(worldId);

        return new WorldResponse(
                null,  // no database id for collections
                worldId.getId(),
                worldId.getRegionId(),
                title,
                "World Collection: " + worldId.getId(),  // generated description
                null,  // no publicData for collections
                null,  // no createdAt
                null,  // no updatedAt
                true,  // collections are always enabled
                false, // collections are not instanceable
                0,     // default groundLevel
                null,  // no waterLevel
                null,  // no groundBlockType
                null,  // no waterBlockType
                Set.of(),  // empty owner set
                Set.of(),  // empty editor set
                Set.of(),  // empty supporter set
                Set.of(),  // empty player set
                false  // not public
        );
    }

    /**
     * List all worlds in a region with optional filtering
     * GET /control/regions/{regionId}/worlds
     *
     * Query parameters:
     * - filter: Filter type for world selection
     *   - "mainOnly": Only main worlds (no branches, zones, instances, collections)
     *   - "mainAndBranches": Main worlds + branches (no zones, instances, collections)
     *   - "mainWorldsAndInstances": Main worlds + instances + branches (no zones, no collections)
     *   - "allWithoutInstances": Worlds + zones + branches (no instances, no collections)
     *   - "regionCollections": Only @region + shared collections
     *   - "regionOnly": Only @region collection
     *   - "withCollections": Include main worlds + world collections
     *   - "withCollectionsAndZones": Include main worlds + world collections + zones
     */
    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable String regionId,
            @RequestParam(required = false) String filter) {
        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        try {
            List<WorldResponse> result;

            if ("withCollections".equals(filter)) {
                // Get main worlds
                List<WorldResponse> worlds = worldService.findByRegionId(regionId).stream()
                        .filter(world -> matchesFilter(world, "mainOnly"))
                        .map(this::toResponse)
                        .collect(java.util.stream.Collectors.toList());

                // Get world collections
                List<WorldResponse> collections = worldService.findWorldCollections().stream()
                        .filter(worldId -> regionId.equals(worldId.getRegionId()))
                        .map(this::toResponseFromWorldId)
                        .toList();

                // Combine both lists
                worlds.addAll(collections);
                result = worlds;
            } else if ("withCollectionsAndZones".equals(filter)) {
                // Get main worlds
                List<WorldResponse> worlds = worldService.findByRegionId(regionId).stream()
                        .filter(world -> matchesFilter(world, "mainOnly"))
                        .map(this::toResponse)
                        .collect(java.util.stream.Collectors.toList());

                // Get world collections
                List<WorldResponse> collections = worldService.findWorldCollections().stream()
                        .filter(worldId -> regionId.equals(worldId.getRegionId()))
                        .map(this::toResponseFromWorldId)
                        .toList();

                // Get zones
                List<WorldResponse> zones = worldService.findByRegionId(regionId).stream()
                        .filter(world -> {
                            WorldId worldId = WorldId.unchecked(world.getWorldId());
                            return worldId.isZone();
                        })
                        .map(this::toResponse)
                        .toList();

                // Combine all three lists
                worlds.addAll(collections);
                worlds.addAll(zones);
                result = worlds;
            } else {
                result = worldService.findByRegionId(regionId).stream()
                        .filter(world -> matchesFilter(world, filter))
                        .map(this::toResponse)
                        .toList();
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Check if a world matches the given filter criteria.
     */
    private boolean matchesFilter(WWorld world, String filter) {
        if (filter == null || filter.isBlank()) {
            return true; // No filter = show all
        }

        WorldId worldId = WorldId.unchecked(world.getWorldId());

        return switch (filter) {
            case "mainOnly" ->
                // Only main worlds (no branches, zones, instances, collections)
                worldId.isMain() && !worldId.isCollection();

            case "mainAndBranches" ->
                // Main worlds + branches (no zones, instances, collections)
                !worldId.isCollection() && !worldId.isZone() && !worldId.isInstance();

            case "mainWorldsAndInstances" ->
                // Main worlds + instances + branches (no zones, no collections)
                !worldId.isCollection() && !worldId.isZone();

            case "allWithoutInstances" ->
                // Worlds + zones + branches (no instances, no collections)
                !worldId.isCollection() && !worldId.isInstance();

            case "regionCollections" ->
                // Only @region + shared collections
                worldId.isCollection() &&
                (world.getWorldId().startsWith(WorldId.COLLECTION_REGION) ||
                 world.getWorldId().startsWith(WorldId.COLLECTION_SHARED));

            case "regionOnly" ->
                // Only @region collection
                worldId.isCollection() && world.getWorldId().startsWith(WorldId.COLLECTION_REGION);

            default -> true; // Unknown filter = show all
        };
    }

    /**
     * Get world by worldId
     * GET /control/regions/{regionId}/worlds/{worldId}
     */
    @GetMapping("/{worldId}")
    public ResponseEntity<?> get(
            @PathVariable String regionId,
            @PathVariable String worldId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(worldId, "worldId");
        if (error2 != null) return error2;

        return worldService.getByWorldId(worldId)
                .<ResponseEntity<?>>map(w -> {
                    if (!regionId.equals(w.getRegionId())) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "World not found in this region"));
                    }
                    return ResponseEntity.ok(toResponse(w));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "World not found: " + worldId)));
    }

    /**
     * Create new world
     * POST /control/regions/{regionId}/worlds
     */
    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String regionId,
            @RequestBody WorldRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        if (Strings.isBlank(request.worldId())) {
            return bad("worldId is required");
        }

        if (Strings.isBlank(request.title())) {
            return bad("title is required");
        }

        try {
            WorldInfo info = request.publicData() != null ? request.publicData() : new WorldInfo();
            // Set title in publicData
            info.setTitle(request.title());

            WorldId worldIdObj = WorldId.of(request.worldId()).orElseThrow(() ->
                new IllegalArgumentException("Invalid worldId: " + request.worldId()));

            WWorld created = worldService.createWorld(worldIdObj, info);

            // Set additional fields via update
            worldService.updateWorld(worldIdObj, w -> {
                w.setRegionId(regionId);
                w.setDescription(request.description());
                if (request.enabled() != null) w.setEnabled(request.enabled());
                if (request.groundLevel() != null) w.setGroundLevel(request.groundLevel());
                if (request.waterLevel() != null) w.setWaterLevel(request.waterLevel());
                if (request.groundBlockType() != null) w.setGroundBlockType(request.groundBlockType());
                if (request.waterBlockType() != null) w.setWaterBlockType(request.waterBlockType());
            });

            // Create job to initialize world defaults
            Map<String, String> jobParameters = Map.of("worldId", request.worldId());

            de.mhus.nimbus.world.shared.job.WJob job = jobService.createJob(
                    request.worldId(),
                    "create-world-defaults",
                    "initialize",
                    jobParameters,
                    5,  // priority
                    0   // no retries
            );

            WWorld updated = worldService.getByWorldId(worldIdObj).orElseThrow();
            WorldResponse worldResponse = toResponse(updated);

            WorldCreateResponse response = new WorldCreateResponse(
                    worldResponse.id(),
                    worldResponse.worldId(),
                    worldResponse.regionId(),
                    worldResponse.title(),
                    worldResponse.description(),
                    worldResponse.publicData(),
                    worldResponse.createdAt(),
                    worldResponse.updatedAt(),
                    worldResponse.enabled(),
                    worldResponse.instanceable(),
                    worldResponse.groundLevel(),
                    worldResponse.waterLevel(),
                    worldResponse.groundBlockType(),
                    worldResponse.waterBlockType(),
                    worldResponse.owner(),
                    worldResponse.editor(),
                    worldResponse.supporter(),
                    worldResponse.player(),
                    worldResponse.publicFlag(),
                    job.getId()
            );

            return ResponseEntity.created(URI.create("/control/regions/" + regionId + "/worlds/" + updated.getWorldId()))
                    .body(response);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update world
     * PUT /control/regions/{regionId}/worlds/{worldId}
     */
    @PutMapping("/{worldId}")
    public ResponseEntity<?> update(
            @PathVariable String regionId,
            @PathVariable String worldId,
            @RequestBody WorldRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(worldId, "worldId");
        if (error2 != null) return error2;

        WWorld existing = worldService.getByWorldId(worldId).orElse(null);
        if (existing == null) {
            return notFound("World not found: " + worldId);
        }

        if (!regionId.equals(existing.getRegionId())) {
            return notFound("World not found in this region");
        }

        try {
            // Validate immutable fields: chunkSize and hexGridSize cannot be changed after creation
            if (request.publicData() != null && existing.getPublicData() != null) {
                int existingChunkSize = existing.getPublicData().getChunkSize();
                int existingHexGridSize = existing.getPublicData().getHexGridSize();
                int requestChunkSize = request.publicData().getChunkSize();
                int requestHexGridSize = request.publicData().getHexGridSize();

                if (existingChunkSize != requestChunkSize) {
                    return bad(String.format(
                            "chunkSize cannot be changed after world creation (existing: %d, requested: %d)",
                            existingChunkSize, requestChunkSize));
                }

                if (existingHexGridSize != requestHexGridSize) {
                    return bad(String.format(
                            "hexGridSize cannot be changed after world creation (existing: %d, requested: %d)",
                            existingHexGridSize, requestHexGridSize));
                }
            }

            // Update title in publicData if provided
            if (request.title() != null) {
                WorldInfo publicData = existing.getPublicData();
                if (publicData == null) {
                    publicData = new WorldInfo();
                    existing.setPublicData(publicData);
                }
                publicData.setTitle(request.title());
            }

            if (request.description() != null) existing.setDescription(request.description());
            if (request.publicData() != null) existing.setPublicData(request.publicData());
            if (request.enabled() != null) existing.setEnabled(request.enabled());
            if (request.instanceable() != null) existing.setInstanceable(request.instanceable());
            if (request.owner() != null) existing.setOwner(request.owner());
            if (request.editor() != null) existing.setEditor(request.editor());
            if (request.supporter() != null) existing.setSupporter(request.supporter());
            if (request.player() != null) existing.setPlayer(request.player());
            if (request.groundLevel() != null) existing.setGroundLevel(request.groundLevel());
            if (request.waterLevel() != null) existing.setWaterLevel(request.waterLevel());
            if (request.groundBlockType() != null) existing.setGroundBlockType(request.groundBlockType());
            if (request.waterBlockType() != null) existing.setWaterBlockType(request.waterBlockType());

            WWorld updated = worldService.save(existing);
            return ResponseEntity.ok(toResponse(updated));
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Delete world
     * DELETE /control/regions/{regionId}/worlds/{worldId}
     *
     * Response: { "jobId": "..." }
     */
    @DeleteMapping("/{worldId}")
    public ResponseEntity<?> delete(
            @PathVariable String regionId,
            @PathVariable String worldId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(worldId, "worldId");
        if (error2 != null) return error2;

        Optional<WWorld> existing = worldService.getByWorldId(worldId);
        if (existing.isEmpty()) {
            return notFound("World not found: " + worldId);
        }

        if (!regionId.equals(existing.get().getRegionId())) {
            return notFound("World not found in this region");
        }

        try {
            WorldId worldIdObj = WorldId.of(worldId).orElseThrow(() ->
                new IllegalArgumentException("Invalid worldId: " + worldId));

            // Delete the WWorld entity first
            worldService.deleteWorld(worldIdObj);

            // Create job to delete all associated resources
            Map<String, String> jobParameters = Map.of("worldId", worldId);

            de.mhus.nimbus.world.shared.job.WJob job = jobService.createJob(
                    worldId,
                    "delete-world-resources",
                    "cleanup",
                    jobParameters,
                    5,  // priority
                    0   // no retries
            );

            // Return job info
            Map<String, String> response = Map.of("jobId", job.getId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete world", e);
            return bad(e.getMessage());
        }
    }

    /**
     * Create zone from existing world
     * POST /control/regions/{regionId}/worlds/{worldId}/zones
     *
     * Request body: { "zoneName": "zone1" }
     */
    @PostMapping("/{worldId}/zones")
    public ResponseEntity<?> createZone(
            @PathVariable String regionId,
            @PathVariable String worldId,
            @RequestBody Map<String, String> request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(worldId, "worldId");
        if (error2 != null) return error2;

        String zoneName = request.get("zoneName");
        if (zoneName == null || zoneName.isBlank()) {
            return bad("zoneName is required");
        }

        // Validate source world exists
        Optional<WWorld> sourceWorld = worldService.getByWorldId(worldId);
        if (sourceWorld.isEmpty()) {
            return notFound("Source world not found: " + worldId);
        }

        if (!regionId.equals(sourceWorld.get().getRegionId())) {
            return notFound("World not found in this region");
        }

        try {
            WorldId worldIdObj = WorldId.of(worldId).orElseThrow(() ->
                new IllegalArgumentException("Invalid worldId: " + worldId));

            WWorld zoneWorld = worldService.copyWorldAsZone(worldIdObj, zoneName);
            String zoneWorldId = zoneWorld.getWorldId();

            return ResponseEntity.created(URI.create("/control/regions/" + regionId + "/worlds/" + zoneWorldId))
                    .body(toResponse(zoneWorld));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Duplicate world with all its data
     * POST /control/regions/{regionId}/worlds/{worldId}/duplicate
     *
     * Request body: { "targetWorldId": "new-world", "targetWorldTitle": "New World" }
     * Response: { "jobId": "...", "targetWorldId": "...", "targetWorldTitle": "..." }
     */
    @PostMapping("/{worldId}/duplicate")
    public ResponseEntity<?> duplicate(
            @PathVariable String regionId,
            @PathVariable String worldId,
            @RequestBody Map<String, String> request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(worldId, "worldId");
        if (error2 != null) return error2;

        String targetWorldId = request.get("targetWorldId");
        if (targetWorldId == null || targetWorldId.isBlank()) {
            return bad("targetWorldId is required");
        }

        String targetWorldTitle = request.get("targetWorldTitle");
        if (targetWorldTitle == null || targetWorldTitle.isBlank()) {
            return bad("targetWorldTitle is required");
        }

        // Validate source world exists
        Optional<WWorld> sourceWorld = worldService.getByWorldId(worldId);
        if (sourceWorld.isEmpty()) {
            return notFound("Source world not found: " + worldId);
        }

        if (!regionId.equals(sourceWorld.get().getRegionId())) {
            return notFound("World not found in this region");
        }

        // Validate target worldId doesn't exist
        Optional<WWorld> existingTarget = worldService.getByWorldId(targetWorldId);
        if (existingTarget.isPresent()) {
            return bad("Target world already exists: " + targetWorldId);
        }

        try {
            WWorld source = sourceWorld.get();

            // Create target world entity with same properties as source
            WorldId targetWorldIdObj = WorldId.of(targetWorldId).orElseThrow(() ->
                new IllegalArgumentException("Invalid targetWorldId: " + targetWorldId));

            WorldInfo targetInfo = source.getPublicData() != null ? source.getPublicData() : new WorldInfo();

            WWorld targetWorld = worldService.createWorld(targetWorldIdObj, targetInfo);

            // Set additional fields
            worldService.updateWorld(targetWorldIdObj, w -> {
                w.setRegionId(regionId);
                // Set title in publicData
                WorldInfo publicData = w.getPublicData();
                if (publicData == null) {
                    publicData = new WorldInfo();
                    w.setPublicData(publicData);
                }
                publicData.setTitle(targetWorldTitle);
                w.setDescription(source.getDescription());
                w.setEnabled(source.isEnabled());
                w.setGroundLevel(source.getGroundLevel());
                w.setWaterLevel(source.getWaterLevel());
                w.setGroundBlockType(source.getGroundBlockType());
                w.setWaterBlockType(source.getWaterBlockType());
                w.setInstanceable(source.isInstanceable());
                w.setOwner(source.getOwner());
                w.setEditor(source.getEditor());
                w.setSupporter(source.getSupporter());
                w.setPlayer(source.getPlayer());
                w.setPublicFlag(source.isPublicFlag());
            });

            // Create duplication job
            Map<String, String> jobParameters = Map.of(
                    "sourceWorldId", worldId,
                    "targetWorldId", targetWorldId
            );

            de.mhus.nimbus.world.shared.job.WJob job = jobService.createJob(
                    targetWorldId,
                    "duplicate-world",
                    "duplicate",
                    jobParameters,
                    5,  // priority
                    0   // no retries
            );

            // Return job info
            Map<String, String> response = Map.of(
                    "jobId", job.getId(),
                    "targetWorldId", targetWorldId,
                    "targetWorldTitle", targetWorldTitle
            );

            return ResponseEntity.ok(response);

        } catch (IllegalStateException | IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to duplicate world", e);
            return bad("Failed to duplicate world: " + e.getMessage());
        }
    }
}
