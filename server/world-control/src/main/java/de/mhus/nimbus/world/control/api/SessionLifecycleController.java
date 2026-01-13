package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for session lifecycle events.
 * Used by world-player to notify world-control about session state changes.
 */
@RestController
@RequestMapping("/control/session-lifecycle")
@RequiredArgsConstructor
@Slf4j
public class SessionLifecycleController {

    private final WWorldInstanceService instanceService;

    /**
     * Request DTO for session closed event.
     */
    public record SessionClosedRequest(
            String worldId,
            String playerId
    ) {}

    /**
     * Called by world-player when a session is closed.
     * If worldId is an instanceId, removes the player from active players.
     * If no active players remain, deletes the instance.
     *
     * @param request The session closed request
     * @return 204 No Content (always, fire-and-forget operation)
     */
    @PostMapping("/session-closed")
    public ResponseEntity<Void> handleSessionClosed(@RequestBody SessionClosedRequest request) {
        log.debug("Session closed event received: worldId={}, playerId={}", request.worldId(), request.playerId());

        // Validate input
        if (request.worldId() == null || request.worldId().isBlank()) {
            log.warn("Session closed event with empty worldId");
            return ResponseEntity.noContent().build();
        }

        if (request.playerId() == null || request.playerId().isBlank()) {
            log.warn("Session closed event with empty playerId");
            return ResponseEntity.noContent().build();
        }

        try {
            // Delegate to service (handles all business logic)
            instanceService.removePlayerAndDeleteIfEmpty(request.worldId(), request.playerId());
        } catch (Exception e) {
            log.error("Error handling session closed event for worldId={}, playerId={}: {}",
                    request.worldId(), request.playerId(), e.getMessage(), e);
        }

        // Always return 204 (fire-and-forget, idempotent)
        return ResponseEntity.noContent().build();
    }
}
