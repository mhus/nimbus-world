package de.mhus.nimbus.world.player.commands;

import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Command triggered by world-control when a player's wearing items have been modified
 * (e.g. via the wearing panel). Notifies the GameplayService so it can update
 * the player's session state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WearingModifiedCommand implements Command {

    private final SessionManager sessionManager;
    private final GameplayService gameplayService;

    @Override
    public String getName() {
        return "WearingModified";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CommandResult.error(-2, "Session ID required");
        }

        Optional<PlayerSession> sessionOpt = sessionManager.getBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            log.debug("Session not found for WearingModified: {}", sessionId);
            return CommandResult.error(-4, "Session not found: " + sessionId);
        }

        PlayerSession session = sessionOpt.get();
        gameplayService.onWearingModified(session);

        log.info("WearingModified processed: sessionId={}", sessionId);
        return CommandResult.success("Wearing modification notified");
    }

    @Override
    public String getHelp() {
        return "Notify player session that wearing items have been modified (called by world-control)";
    }

    @Override
    public boolean requiresSession() {
        return false;
    }
}
