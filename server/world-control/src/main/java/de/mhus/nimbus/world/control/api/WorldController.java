package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.control.service.BlockInfoService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST Controller for World operations.
 * Base path: /control/worlds
 * <p>
 * Provides access to world metadata and configuration.
 */
@RestController
@RequestMapping("/control/worlds")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Worlds", description = "World metadata and configuration")
public class WorldController extends BaseEditorController {

    private final WWorldService worldService;
    private final BlockInfoService blockInfoService;

    // DTOs
    public record WorldListDto(
            String worldId,
            String name,
            String description,
            Integer chunkSize,
            String status
    ) {
    }

    public record WorldDetailDto(
            String worldId,
            String name,
            String description,
            Integer chunkSize,
            String status,
            String regionId,
            Set<String> owner,
            Boolean publicFlag,
            Set<String> editor,
            Set<String> player
    ) {
    }

    /**
     * List all worlds with optional filtering.
     * GET /control/worlds
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
    @Operation(summary = "List all worlds")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success")
    })
    public ResponseEntity<?> list(
            @Parameter(description = "Filter type for world selection") @RequestParam(required = false) String filter) {
        log.debug("LIST worlds with filter: {}", filter);

        try {
            List<WorldListDto> dtos;

            if ("withCollections".equals(filter)) {
                // Get main worlds
                List<WorldListDto> worlds = worldService.findAll().stream()
                        .filter(world -> matchesFilter(world, "mainOnly"))
                        .map(this::toListDto)
                        .collect(Collectors.toList());

                // Get world collections
                List<WorldListDto> collections = worldService.findWorldCollections().stream()
                        .map(this::toListDtoFromWorldId)
                        .toList();

                // Combine both lists
                worlds.addAll(collections);
                dtos = worlds;
            } else if ("withCollectionsAndZones".equals(filter)) {
                // Get main worlds
                List<WorldListDto> worlds = worldService.findAll().stream()
                        .filter(world -> matchesFilter(world, "mainOnly"))
                        .map(this::toListDto)
                        .collect(Collectors.toList());

                // Get world collections
                List<WorldListDto> collections = worldService.findWorldCollections().stream()
                        .map(this::toListDtoFromWorldId)
                        .toList();

                // Get zones (worlds with parent reference)
                List<WorldListDto> zones = worldService.findAll().stream()
                        .filter(world -> {
                            de.mhus.nimbus.shared.types.WorldId worldId =
                                de.mhus.nimbus.shared.types.WorldId.unchecked(world.getWorldId());
                            return worldId.isZone();
                        })
                        .map(this::toListDto)
                        .toList();

                // Combine all three lists
                worlds.addAll(collections);
                worlds.addAll(zones);
                dtos = worlds;
            } else if (filter != null && !filter.isBlank()) {
                // Apply filter to regular worlds
                dtos = worldService.findAll().stream()
                        .filter(world -> matchesFilter(world, filter))
                        .map(this::toListDto)
                        .collect(Collectors.toList());
            } else {
                // No filter
                dtos = worldService.findAll().stream()
                        .map(this::toListDto)
                        .collect(Collectors.toList());
            }

            log.debug("Returning {} worlds", dtos.size());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Failed to list worlds", e);
            return ResponseEntity.badRequest().body("Failed to list worlds: " + e.getMessage());
        }
    }

    /**
     * Get single world by ID.
     * GET /control/worlds/{worldId}
     */
    @GetMapping("/{worldId}")
    @Operation(summary = "Get world by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "World found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "World not found")
    })
    public ResponseEntity<?> get(
            @Parameter(description = "World identifier") @PathVariable String worldId) {

        log.debug("GET world: worldId={}", worldId);

        ResponseEntity<?> validation = validateId(worldId, "worldId");
        if (validation != null) return validation;

        Optional<WWorld> opt = worldService.getByWorldId(worldId);
        if (opt.isEmpty()) {
            log.warn("World not found: worldId={}", worldId);
            return notFound("world not found");
        }

        log.debug("Returning world: worldId={}", worldId);
        return ResponseEntity.ok(toDetailDto(opt.get()));
    }

    /**
     * Get block info with layer metadata.
     * GET /control/worlds/{worldId}/session/{sessionId}/block/{x}/{y}/{z}
     */
    @GetMapping("/{worldId}/session/{sessionId}/block/{x}/{y}/{z}")
    @Operation(summary = "Get block info with layer metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Block info loaded"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Block/Chunk not found")
    })
    public ResponseEntity<?> getBlock(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Session identifier") @PathVariable String sessionId,
            @Parameter(description = "Block X coordinate") @PathVariable int x,
            @Parameter(description = "Block Y coordinate") @PathVariable int y,
            @Parameter(description = "Block Z coordinate") @PathVariable int z) {

        log.debug("GET block: worldId={} session={} pos=({},{},{})", worldId, sessionId, x, y, z);

        ResponseEntity<?> validation = validateId(worldId, "worldId");
        if (validation != null) return validation;

        validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        try {
            // Load block info with layer metadata
            java.util.Map<String, Object> blockInfo = blockInfoService.loadBlockInfo(worldId, sessionId, x, y, z);

            log.debug("Block info loaded: pos=({},{},{}) layer={} readOnly={}",
                    x, y, z, blockInfo.get("layer"), blockInfo.get("readOnly"));

            return ResponseEntity.ok(blockInfo);

        } catch (Exception e) {
            log.error("Failed to load block info: worldId={} pos=({},{},{})", worldId, x, y, z, e);
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Failed to load block: " + e.getMessage()));
        }
    }

    // Helper methods

    private WorldListDto toListDto(WWorld world) {
        // Build display name: "worldId Title" (title is optional)
        String displayName = world.getWorldId();
        if (world.getPublicData() != null && world.getPublicData().getName() != null) {
            displayName = world.getWorldId() + " " + world.getPublicData().getName();
        }

        return new WorldListDto(
                world.getWorldId(),
                displayName,
                world.getPublicData() != null ? world.getPublicData().getDescription() : null,
                16, // Default chunk size
                world.isEnabled() ? "active" : "inactive"
        );
    }

    private WorldDetailDto toDetailDto(WWorld world) {
        return new WorldDetailDto(
                world.getWorldId(),
                world.getPublicData() != null ? world.getPublicData().getName() : null,
                world.getPublicData() != null ? world.getPublicData().getDescription() : null,
                16, // Default chunk size
                world.isEnabled() ? "active" : "inactive",
                world.getRegionId(),
                world.getOwner(),
                world.isPublicFlag(),
                world.getEditor(),
                world.getPlayer()
        );
    }

    private WorldListDto toListDtoFromWorldId(de.mhus.nimbus.shared.types.WorldId worldId) {
        // Try to get title from WorldCollectionDto
        String title = worldService.getWorldCollectionTitle(worldId);
        String displayName = worldId.getId();
        if (title != null && !title.isBlank()) {
            displayName = worldId.getId() + " " + title;
        }

        return new WorldListDto(
                worldId.getId(),
                displayName,
                "World Collection: " + worldId.getId(),
                16, // Default chunk size
                "active"
        );
    }

    /**
     * Check if a world matches the given filter criteria.
     */
    private boolean matchesFilter(WWorld world, String filter) {
        if (filter == null || filter.isBlank()) {
            return true; // No filter = show all
        }

        de.mhus.nimbus.shared.types.WorldId worldId = de.mhus.nimbus.shared.types.WorldId.unchecked(world.getWorldId());

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
                (world.getWorldId().startsWith(de.mhus.nimbus.shared.types.WorldId.COLLECTION_REGION) ||
                 world.getWorldId().startsWith(de.mhus.nimbus.shared.types.WorldId.COLLECTION_SHARED));

            case "regionOnly" ->
                // Only @region collection
                worldId.isCollection() && world.getWorldId().startsWith(de.mhus.nimbus.shared.types.WorldId.COLLECTION_REGION);

            default -> true; // Unknown filter = show all
        };
    }
}
