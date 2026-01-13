package de.mhus.nimbus.world.player.commands;

import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelector;
import de.mhus.nimbus.world.shared.util.ModelSelectorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ShowModelSelectorCommand - loads ModelSelector from WSession and sends it to the client.
 * Used by EditService to display model blocks in the client.
 *
 * This command:
 * 1. Loads ModelSelector from WSession using session ID from context
 * 2. Converts it to client command format
 * 3. Sends "modelselector enable" command to client with all block positions and colors
 *
 * No parameters required - uses session ID from command context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShowModelSelectorCommand implements Command {

    private final SessionManager sessionManager;
    private final ClientService clientService;
    private final WSessionService wSessionService;

    @Override
    public String getName() {
        return "client.ShowModelSelector";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CommandResult.error(-2, "Session ID required");
        }

        try {
            // Find player session
            Optional<PlayerSession> sessionOpt = sessionManager.getBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                return CommandResult.error(-4, "Session not found: " + sessionId);
            }

            PlayerSession session = sessionOpt.get();

            // Load WSession to get ModelSelector
            Optional<WSession> wSessionOpt = wSessionService.get(sessionId);
            if (wSessionOpt.isEmpty()) {
                return CommandResult.error(-5, "WSession not found: " + sessionId);
            }

            WSession wSession = wSessionOpt.get();
            List<String> modelSelectorData = wSession.getModelSelector();

            // Convert to ModelSelector (even if empty - need to send disable command to client)
            ModelSelector modelSelector = null;
            if (modelSelectorData != null && !modelSelectorData.isEmpty()) {
                modelSelector = ModelSelectorUtil.fromStringList(modelSelectorData);
            }

            // If ModelSelector is null or empty, send disable command to client to clear visualization
            if (modelSelector == null || ModelSelectorUtil.isEmpty(modelSelector)) {
                log.debug("ModelSelector is empty, sending disable command: sessionId={}", sessionId);

                // Send "modelselector disable" command to client
                List<String> clientArgs = new ArrayList<>();
                clientArgs.add("modelselector");
                clientArgs.add("disable");

                clientService.sendCommand(session, clientArgs.get(0), clientArgs.subList(1, clientArgs.size()));

                log.info("Sent ModelSelector disable to client: sessionId={}", sessionId);
                return CommandResult.success("ModelSelector cleared");
            }

            // Build client command arguments
            // Format: modelselector, enable, <defaultColor>, <autoSelectName>, true, <positions...>
            List<String> clientArgs = new ArrayList<>();
            clientArgs.add("modelselector");
            clientArgs.add("enable");
            clientArgs.add(modelSelector.getDefaultColor() != null ? modelSelector.getDefaultColor() : "#dddd00");
            clientArgs.add(modelSelector.getAutoSelectName() != null ? modelSelector.getAutoSelectName() : "");
            clientArgs.add("true"); // Show immediately

            // Add all block positions with colors
            for (String blockEntry : modelSelector.getBlocks()) {
                // blockEntry format: "x,y,z,color"
                String[] parts = blockEntry.split(",", 4);
                if (parts.length == 4) {
                    clientArgs.add(parts[0]); // x
                    clientArgs.add(parts[1]); // y
                    clientArgs.add(parts[2]); // z
                    clientArgs.add(parts[3]); // color
                }
            }

            // Send to client
            clientService.sendCommand(session, clientArgs.get(0), clientArgs.subList(1, clientArgs.size()));

            log.info("Sent ModelSelector to client: sessionId={} blocks={} source={}",
                    sessionId, modelSelector.getBlockCount(), modelSelector.getAutoSelectName());

            return CommandResult.success("ModelSelector displayed: " + modelSelector.getBlockCount() + " blocks");

        } catch (Exception e) {
            log.error("ShowModelSelector failed: session={}", sessionId, e);
            return CommandResult.error(-6, "Internal error: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Load ModelSelector from WSession and display it in the client (no parameters, uses session ID from context)";
    }
}
