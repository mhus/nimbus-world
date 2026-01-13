package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.generated.types.BlockType;
import de.mhus.nimbus.world.shared.access.AccessValidator;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for BlockType templates (read-only).
 * Returns only publicData from entities.
 */
@RestController
@RequestMapping("/player/world")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "BlockTypes", description = "BlockType templates for rendering blocks")
public class BlockTypeController {

    private final WBlockTypeService service;
    private final AccessValidator accessUtil;

    /**
     * GET /player/worlds/{worldId}/blocktypeschunk/{groupName}
     * Returns all BlockTypes in a specific group for chunked loading.
     */
    @GetMapping("/blocktypeschunk/{groupName}")
    @Operation(summary = "Get BlockTypes by group", description = "Returns all BlockTypes in a specific group for chunked loading")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of BlockTypes in group"),
            @ApiResponse(responseCode = "400", description = "Invalid group name")
    })
    public ResponseEntity<?> getBlockTypesByGroup(
            HttpServletRequest request,
            @PathVariable String groupName) {

        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        // Validate group name (only a-z0-9_- allowed)
        if (!groupName.matches("^[a-z0-9_-]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid group name. Only lowercase letters, numbers, hyphens and underscores allowed."));
        }

        List<BlockType> blockTypes = service.findByBlockTypeGroup(worldId, groupName).stream()
        .filter(WBlockType::isEnabled)
        .map(WBlockType::getPublicDataWithFullId)
        .toList();

        log.debug("Returning {} BlockTypes for group: {}", blockTypes.size(), groupName);

        return ResponseEntity.ok(blockTypes);
        }

    /**
     * GET /player/worlds/{worldId}/blocktypes/{blockId}
     * Returns a single BlockType by ID.
     */
    @GetMapping("/blocktypes/{*blockId}")
    @Operation(summary = "Get BlockType by ID", description = "Returns BlockType template for a specific block ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "BlockType found"),
            @ApiResponse(responseCode = "404", description = "BlockType not found")
    })
    public ResponseEntity<?> getBlockType(
            HttpServletRequest request,
            @PathVariable String blockId) {

        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        // Strip leading slash from wildcard pattern {*blockId}
        if (blockId != null && blockId.startsWith("/")) {
            blockId = blockId.substring(1);
        }

        // Extract ID from format "w/310" -> "310" or "310" -> "310"
        if (blockId != null && blockId.contains("/")) {
            String[] parts = blockId.split("/", 2);
            if (parts.length == 2) {
                blockId = parts[1];
            }
        }

        final String finalBlockId = blockId;
        log.debug("GET blocktype: blockId={}, worldId={}", finalBlockId, worldId);

        return service.findByBlockId(worldId, finalBlockId)
                        .map(WBlockType::getPublicDataWithFullId)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> {
                            log.warn("BlockType not found: blockId={}", finalBlockId);
                            return ResponseEntity.notFound().build();
                        });
    }

}
