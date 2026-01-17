package de.mhus.nimbus.world.player.commands;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.session.WSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * CloseSessionCommand - closes a player session gracefully.
 *
 * This command:
 * 1. Persists the session state to MongoDB (position, rotation)
 * 2. Updates WSession status to CLOSED in Redis
 * 3. Deletes the player session entry from Redis (region:<regionId>:player:<playerId>)
 * 4. Closes the WebSocket connection if still active
 *
 * Used by AccessService during login to close any existing sessions for a player
 * before creating a new one (prevents multiple logins).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CloseSessionCommand implements Command {

    private final SessionManager sessionManager;
    private final WSessionService wSessionService;
    private final WPlayerSessionService playerSessionService;

    @Override
    public String getName() {
        return "session.close";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CommandResult.error(-2, "Session ID required");
        }

        try {
            // Get WSession from Redis
            Optional<WSession> wSessionOpt = wSessionService.get(sessionId);
            if (wSessionOpt.isEmpty()) {
                log.warn("WSession not found for closeSession: sessionId={}", sessionId);
                return CommandResult.error(-4, "Session not found: " + sessionId);
            }

            WSession wSession = wSessionOpt.get();

            // Get PlayerSession if it exists (might not exist if session is on different pod)
            Optional<PlayerSession> playerSessionOpt = sessionManager.getBySessionId(sessionId);

            // 1. Persist session state to MongoDB if PlayerSession exists
            if (playerSessionOpt.isPresent()) {
                PlayerSession playerSession = playerSessionOpt.get();

                if (playerSession.getWorldId() != null && playerSession.getLastPosition() != null) {
                    try {
                        playerSessionService.updateSession(
                                playerSession.getWorldId().getId(),
                                wSession.getPlayerId(),
                                playerSession.getLastPosition(),
                                playerSession.getLastRotation()
                        );
                        log.debug("Persisted session state to MongoDB: sessionId={}, worldId={}, playerId={}",
                                sessionId, playerSession.getWorldId().getId(), wSession.getPlayerId());
                    } catch (Exception e) {
                        log.error("Failed to persist session state: sessionId={}", sessionId, e);
                        // Continue with close even if persistence fails
                    }
                }

                // Close WebSocket connection if still active
                try {
                    sessionManager.removeSession(playerSession.getWebSocketSession().getId());
                    log.debug("Closed WebSocket connection: sessionId={}", sessionId);
                } catch (Exception e) {
                    log.error("Failed to close WebSocket: sessionId={}", sessionId, e);
                    // Continue with close
                }
            }

            // 2. Update WSession status to CLOSED (this also deletes player session entry)
            wSessionService.updateStatus(sessionId, WSessionStatus.CLOSED);

            log.info("Session closed successfully: sessionId={}, playerId={}", sessionId, wSession.getPlayerId());
            return CommandResult.success("Session closed");

        } catch (Exception e) {
            log.error("CloseSession failed: sessionId={}", sessionId, e);
            return CommandResult.error(-6, "Internal error: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Close a player session gracefully (persists state, closes WebSocket, updates Redis)";
    }
}
