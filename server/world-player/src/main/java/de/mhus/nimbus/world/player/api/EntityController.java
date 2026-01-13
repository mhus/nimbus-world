package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.service.PlayerService;
import de.mhus.nimbus.world.shared.access.AccessValidator;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Entity instances in the world (read-only).
 * Returns only publicData from entities.
 */
@RestController
@RequestMapping("/player/world/entities")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Entities", description = "Entity instances placed in worlds (NPCs, players, etc.)")
public class EntityController {

    private final WEntityService service;
    private final PlayerService playerService;
    private final AccessValidator accessUtil;

    @GetMapping("/{entityId}")
    @Operation(summary = "Get Entity by world and entity ID", description = "Returns Entity instance for a specific entity in a world")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity found"),
            @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<?> getEntity(
            HttpServletRequest request,
            @PathVariable String entityId) {
        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        if (Strings.isBlank(entityId)) {
            return ResponseEntity.badRequest().body("entityId is required");
        }
        if (entityId.startsWith("@")) {
            var playerId = PlayerId.of(entityId);
            if (playerId.isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid player ID");
            }
            var playerEntity = playerService.getPlayerAsEntity(playerId.get(), worldId);
            return playerEntity
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        return service.findByWorldIdAndEntityId(worldId, entityId)
                        .map(WEntity::getPublicData)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
