package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.EditService;
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
 * REST Controller for block marker operations.
 * Base path: /control/worlds/{worldId}/session/{sessionId}/marker
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/session/{sessionId}/marker")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Marker", description = "Block marker operations for edit mode")
public class EMarkerController extends BaseEditorController {

    private final EditService editService;

    /**
     * Mark a block at specified coordinates.
     * POST /control/worlds/{worldId}/session/{sessionId}/marker/{x}/{y}/{z}
     */
    @PostMapping("/{x}/{y}/{z}")
    @Operation(summary = "Mark block at position", description = "Marks a block at the specified coordinates in the client")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Block marked successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Session or world not found")
    })
    public ResponseEntity<?> markBlock(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Session identifier") @PathVariable String sessionId,
            @Parameter(description = "X coordinate") @PathVariable int x,
            @Parameter(description = "Y coordinate") @PathVariable int y,
            @Parameter(description = "Z coordinate") @PathVariable int z) {

        log.debug("Mark block: worldId={}, sessionId={}, pos=({},{},{})", worldId, sessionId, x, y, z);

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        try {
            // Call EditService markBlock method
            editService.doMarkBlock(worldId, sessionId, x, y, z);

            log.info("Block marked successfully: worldId={}, sessionId={}, pos=({},{},{})",
                    worldId, sessionId, x, y, z);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "worldId", worldId,
                    "sessionId", sessionId,
                    "x", x,
                    "y", y,
                    "z", z
            ));

        } catch (Exception e) {
            log.error("Failed to mark block: worldId={}, sessionId={}, pos=({},{},{})",
                    worldId, sessionId, x, y, z, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to mark block: " + e.getMessage()));
        }
    }

    /**
     * Clear the marked block.
     * DELETE /control/worlds/{worldId}/session/{sessionId}/marker
     */
    @DeleteMapping
    @Operation(summary = "Clear marked block", description = "Removes the visual marker from the client and clears marked block data from Redis")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marker cleared successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Session or world not found")
    })
    public ResponseEntity<?> clearMarker(
            @Parameter(description = "World identifier") @PathVariable String worldId,
            @Parameter(description = "Session identifier") @PathVariable String sessionId) {

        log.debug("Clear marker: worldId={}, sessionId={}", worldId, sessionId);

        WorldId.of(worldId).orElseThrow(
                () -> new IllegalStateException("Invalid worldId: " + worldId)
        );
        var validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        try {
            // Call EditService to clear the marked block
            editService.clearMarkedBlock(worldId, sessionId);

            log.info("Marker cleared successfully: worldId={}, sessionId={}", worldId, sessionId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "worldId", worldId,
                    "sessionId", sessionId
            ));

        } catch (Exception e) {
            log.error("Failed to clear marker: worldId={}, sessionId={}", worldId, sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to clear marker: " + e.getMessage()));
        }
    }
}
