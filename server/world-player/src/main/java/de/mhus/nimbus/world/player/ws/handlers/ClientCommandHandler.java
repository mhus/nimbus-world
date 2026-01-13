package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.commands.CommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles client command messages.
 * Message type: "cmd" (Client Command, Client → Server)
 *
 * Client sends commands for execution.
 * Server executes via CommandService and sends response.
 *
 * Expected data:
 * {
 *   "cmd": "help",
 *   "args": ["say"],  // optional
 *   "oneway": false   // optional, true = no response expected
 * }
 *
 * Server can send streaming messages during execution:
 * Response type: "cmd.msg" (streaming message)
 * {
 *   "message": "Processing..."
 * }
 *
 * Final response type: "cmd.rs" (command result)
 * {
 *   "rc": 0,  // return code (0=success, negative=system error, positive=command error)
 *   "message": "Command output"
 * }
 *
 * Return codes:
 * -1 = Command not found
 * -2 = Command not allowed (permission denied)
 * -3 = Invalid arguments
 * -4 = Internal error
 *  0 = OK / true
 *  1 = Error / false
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientCommandHandler implements MessageHandler {

    private final CommandService commandService;
    private final WorldClientService worldClientService;
    private final ObjectMapper objectMapper;

    // Server IP and port for origin in CommandContext (cached)
    private String serverIp = null;
    private Integer serverPort = null;

    // Static prefix mapping for command routing
    private static final Map<String, String> PREFIX_ROUTING = Map.of(
            "life.", "world-life",
            "control.", "world-control"
    );

    @Override
    public String getMessageType() {
        return "cmd";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Command from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();
        String requestId = message.getI();

        // Extract command data
        String commandName = data.has("cmd") ? data.get("cmd").asText() : null;
        boolean oneway = data.has("oneway") && data.get("oneway").asBoolean();
        List<String> args = extractArgs(data);

        if (commandName == null || commandName.isBlank()) {
            log.warn("Command without name");
            if (!oneway && requestId != null) {
                sendErrorResponse(session, requestId, -3, "Missing command name");
            }
            return;
        }

        // Check for remote routing prefix
        String targetServer = detectTargetServer(commandName);

        if (targetServer != null) {
            // Remote command execution
            handleRemoteCommand(session, requestId, commandName, args, targetServer, oneway);
        } else {
            // Local command execution
            handleLocalCommand(session, requestId, commandName, args, oneway);
        }
    }

    /**
     * Detect if command should be routed to remote server.
     * Returns target server name or null for local execution.
     */
    private String detectTargetServer(String commandName) {
        for (Map.Entry<String, String> entry : PREFIX_ROUTING.entrySet()) {
            if (commandName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Strip prefix from command name.
     * Example: "life.status" -> "status"
     */
    private String stripPrefix(String commandName) {
        for (String prefix : PREFIX_ROUTING.keySet()) {
            if (commandName.startsWith(prefix)) {
                return commandName.substring(prefix.length());
            }
        }
        return commandName;
    }

    /**
     * Handle local command execution.
     */
    private void handleLocalCommand(
            PlayerSession session,
            String requestId,
            String commandName,
            List<String> args,
            boolean oneway) {

        // Build context from session
        CommandContext context = CommandContext.builder()
                .worldId(session.getWorldId().getId())
                .sessionId(session.getSessionId())
                .userId(session.getPlayer().user().getUserId())
                .title(session.getTitle())
                .build();

        // Execute command
        Command.CommandResult result = commandService.execute(context, commandName, args);

        // Send streaming messages if any
        if (result.getStreamMessages() != null && !result.getStreamMessages().isEmpty()) {
            for (String streamMessage : result.getStreamMessages()) {
                sendStreamingMessage(session, requestId, streamMessage);
            }
        }

        // Send final response (unless oneway)
        if (!oneway && requestId != null) {
            sendResponse(session, requestId, result.getReturnCode(), result.getMessage());
        }

        log.debug("Local command executed: cmd={}, rc={}, user={}",
                commandName, result.getReturnCode(), session.getTitle());
    }

    /**
     * Handle remote command execution.
     */
    private void handleRemoteCommand(
            PlayerSession session,
            String requestId,
            String commandName,
            List<String> args,
            String targetServer,
            boolean oneway) {

        // Strip prefix from command name
        String actualCommandName = stripPrefix(commandName);

        // Build context
        CommandContext context = CommandContext.builder()
                .worldId(session.getWorldId().getId())
                .sessionId(session.getSessionId())
                .userId(session.getPlayer().user().getUserId())
                .title(session.getTitle())
                .build();

        log.debug("Routing command to {}: cmd={}, actualCmd={}, user={}",
                targetServer, commandName, actualCommandName, session.getTitle());

        // Route to appropriate server
        java.util.concurrent.CompletableFuture<WorldClientService.CommandResponse> future;

        switch (targetServer) {
            case "world-life":
            case "life":
                future = worldClientService.sendLifeCommand(
                        session.getWorldId().getId(), actualCommandName, args, context);
                break;

            case "world-control":
            case "control":
                future = worldClientService.sendControlCommand(
                        session.getWorldId().getId(), actualCommandName, args, context);
                break;

            case "world-generator":
            case "generator":
                future = worldClientService.sendGeneratorCommand(
                        session.getWorldId().getId(), actualCommandName, args, context);
                break;
            default:
                log.error("Unknown target server: {}", targetServer);
                if (!oneway && requestId != null) {
                    sendErrorResponse(session, requestId, -4, "Unknown target server");
                }
                return;
        }

        // Handle async response
        if (!oneway && requestId != null) {
            future.thenAccept(response -> {
                // Send streaming messages if any
                if (response.streamMessages() != null) {
                    for (String streamMessage : response.streamMessages()) {
                        sendStreamingMessage(session, requestId, streamMessage);
                    }
                }

                // Send final response
                sendResponse(session, requestId, response.rc(), response.message());

                log.debug("Remote command completed: cmd={}, rc={}, target={}",
                        commandName, response.rc(), targetServer);

            }).exceptionally(throwable -> {
                log.error("Remote command failed: cmd={}, target={}",
                        commandName, targetServer, throwable);
                sendErrorResponse(session, requestId, -4,
                        "Remote command failed: " + throwable.getMessage());
                return null;
            });
        } else if (oneway) {
            // Fire and forget
            future.exceptionally(throwable -> {
                log.error("Remote command failed (oneway): cmd={}, target={}",
                        commandName, targetServer, throwable);
                return null;
            });
        }
    }

    private List<String> extractArgs(JsonNode data) {
        List<String> args = new ArrayList<>();
        if (data.has("args") && data.get("args").isArray()) {
            ArrayNode argsArray = (ArrayNode) data.get("args");
            for (JsonNode arg : argsArray) {
                args.add(arg.asText());
            }
        }
        return args;
    }

    /**
     * Send streaming message during command execution.
     */
    private void sendStreamingMessage(PlayerSession session, String requestId, String message) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("message", message);

            NetworkMessage response = NetworkMessage.builder()
                    .r(requestId)
                    .t("cmd.msg")
                    .d(data)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            session.getWebSocketSession().sendMessage(new TextMessage(json));

            log.trace("Sent command streaming message: requestId={}", requestId);

        } catch (Exception e) {
            log.error("Failed to send command streaming message", e);
        }
    }

    /**
     * Send final command response.
     */
    private void sendResponse(PlayerSession session, String requestId, int returnCode, String message) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("rc", returnCode);
            if (message != null) {
                data.put("message", message);
            }

            NetworkMessage response = NetworkMessage.builder()
                    .r(requestId)
                    .t("cmd.rs")
                    .d(data)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            session.getWebSocketSession().sendMessage(new TextMessage(json));

            log.trace("Sent command response: requestId={}, rc={}", requestId, returnCode);

        } catch (Exception e) {
            log.error("Failed to send command response", e);
        }
    }

    /**
     * Send error response for invalid requests.
     */
    private void sendErrorResponse(PlayerSession session, String requestId, int errorCode, String errorMessage) {
        sendResponse(session, requestId, errorCode, errorMessage);
    }

}
