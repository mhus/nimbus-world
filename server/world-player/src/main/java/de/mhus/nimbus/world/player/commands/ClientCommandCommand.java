package de.mhus.nimbus.world.player.commands;

import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * ClientCommand - sends "scmd" (Server Command) message to client via WebSocket.
 * Triggered by world-control to execute commands on the client side.
 *
 * Message format:
 * {
 *   "t": "scmd",
 *   "d": {
 *     "cmd": "commandName",
 *     "args": ["arg1", "arg2", ...]
 *   }
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientCommandCommand implements Command {

    private final SessionManager sessionManager;
    private final ClientService clientService;

    @Override
    public String getName() {
        return "client";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        // Args: [commandName, arg1, arg2, ...]
        if (args.isEmpty()) {
            return CommandResult.error(-3, "Usage: client <commandName> [args...]");
        }

        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CommandResult.error(-2, "Session ID required");
        }

        try {
            // Find session
            Optional<PlayerSession> sessionOpt = sessionManager.getBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                return CommandResult.error(-4, "Session not found: " + sessionId);
            }

            PlayerSession session = sessionOpt.get();

            // Extract command name and arguments
            String commandName = args.get(0);
            List<String> commandArgs = args.size() > 1 ? args.subList(1, args.size()) : List.of();

            // Send via ClientService
            clientService.sendCommand(session, commandName, commandArgs);

            log.debug("Sent client command: session={} cmd={} args={}",
                    sessionId, commandName, commandArgs.size());

            return CommandResult.success("Client command sent: " + commandName);

        } catch (Exception e) {
            log.error("ClientCommand failed: session={}", sessionId, e);
            return CommandResult.error(-5, "Internal error: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Send a command and parameters to the client";
    }
}
