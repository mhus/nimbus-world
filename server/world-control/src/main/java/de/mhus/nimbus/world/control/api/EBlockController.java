package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.dto.BlockOriginDto;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Block operations.
 * Base path: /control/worlds/{worldId}/blocks
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/blocks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Blocks", description = "Block inspection and debugging")
public class EBlockController extends BaseEditorController {

    private final WLayerService layerService;

    /**
     * Find origin of a block at specific coordinates.
     * Returns layer, terrain, model (if applicable), and block metadata.
     * GET /control/worlds/{worldId}/blocks/origin?x={x}&y={y}&z={z}
     */
    @GetMapping("/origin")
    @Operation(summary = "Find block origin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Block origin found or not found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<?> findOrigin(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Block X coordinate") @RequestParam int x,
            @Parameter(description = "Block Y coordinate") @RequestParam int y,
            @Parameter(description = "Block Z coordinate") @RequestParam int z) {

        log.debug("FIND block origin: worldId={}, pos=({},{},{})", worldId, x, y, z);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );

        // IMPORTANT: Filter out instances - layers are per world only
        String lookupWorldId = wid.withoutInstance().getId();

        try {
            WLayerService.BlockOrigin origin = layerService.findBlockOrigin(lookupWorldId, x, y, z);

            if (origin == null) {
                log.debug("Block origin not found: worldId={}, pos=({},{},{})", lookupWorldId, x, y, z);
                return ResponseEntity.ok(Map.of(
                        "found", false,
                        "message", "Block not found in any layer"
                ));
            }

            // Build DTO
            BlockOriginDto dto = toBlockOriginDto(origin);

            log.info("Found block origin: worldId={}, pos=({},{},{}), layer={}",
                    lookupWorldId, x, y, z, origin.layer().getName());

            return ResponseEntity.ok(Map.of(
                    "found", true,
                    "origin", dto
            ));

        } catch (Exception e) {
            log.error("Failed to find block origin: worldId={}, pos=({},{},{})",
                    lookupWorldId, x, y, z, e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to find block origin: " + e.getMessage()));
        }
    }

    /**
     * Convert BlockOrigin to DTO.
     */
    private BlockOriginDto toBlockOriginDto(WLayerService.BlockOrigin origin) {
        String groupName = null;

        // Find group name if block is in a group
        if (origin.model() != null && origin.layerBlock().getGroup() > 0) {
            // Search for group name in model's groups map
            for (Map.Entry<String, Integer> entry : origin.model().getGroups().entrySet()) {
                if (entry.getValue().equals(origin.layerBlock().getGroup())) {
                    groupName = entry.getKey();
                    break;
                }
            }
        }

        Integer groupValue = origin.layerBlock().getGroup() > 0 ? Integer.valueOf(origin.layerBlock().getGroup()) : null;

        return new BlockOriginDto(
                origin.layer().getId(),
                origin.layer().getName(),
                origin.layer().getLayerType().name(),
                origin.layer().getOrder(),
                origin.terrain() != null ? origin.terrain().getId() : null,
                origin.terrain() != null ? origin.terrain().getChunkKey() : null,
                origin.model() != null ? origin.model().getId() : null,
                origin.model() != null ? origin.model().getName() : null,
                origin.model() != null ? origin.model().getTitle() : null,
                origin.model() != null ? Integer.valueOf(origin.model().getMountX()) : null,
                origin.model() != null ? Integer.valueOf(origin.model().getMountY()) : null,
                origin.model() != null ? Integer.valueOf(origin.model().getMountZ()) : null,
                groupValue,
                groupName,
                origin.layerBlock().getMetadata()
        );
    }
}
