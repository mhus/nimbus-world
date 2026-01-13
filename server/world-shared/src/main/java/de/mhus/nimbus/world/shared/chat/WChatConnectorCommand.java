package de.mhus.nimbus.world.shared.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat connector command for remote agent access.
 *
 * Subcommands:
 *   agent-list                              - Get list of available agents
 *   chat <agent> <message>                  - Chat with an agent
 *   execute-command <agent> <cmd> <params>  - Execute command on agent
 *
 * Usage:
 *   From remote: sendCommand(url, "chat.Connector", ["agent-list"], context)
 *   From remote: sendCommand(url, "chat.Connector", ["chat", "eliza", "Hello"], context)
 *   From remote: sendCommand(url, "chat.Connector", ["execute-command", "eliza", "status", "{}"], context)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WChatConnectorCommand implements Command {

    private final LocalWChatAgentProvider localProvider;
    private final WChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "chat-connector";
    }

    @Override
    public boolean requiresSession() {
        return false; // Can be called remotely without session
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        if (args == null || args.isEmpty()) {
            return CommandResult.error("Subcommand required: agent-list or chat");
        }

        String subCommand = args.get(0);
        WorldId worldId = WorldId.unchecked(context.getWorldId());
        String sessionId = context.getSessionId();

        try {
            switch (subCommand) {
                case "agent-list":
                    return executeAgentList(worldId, sessionId);
                case "chat":
                    return executeChat(context, args);
                case "execute-command":
                    return executeAgentCommand(context, args);
                default:
                    return CommandResult.error("Unknown subcommand: " + subCommand);
            }
        } catch (Exception e) {
            log.error("Error executing chat.Connector command", e);
            return CommandResult.error("Command execution failed: " + e.getMessage());
        }
    }

    /**
     * Execute agent-list subcommand.
     * Returns JSON array in streamMessages.
     */
    private CommandResult executeAgentList(WorldId worldId, String sessionId) throws JsonProcessingException {
        List<WChatAgent> agents = localProvider.getAvailableAgents(worldId, sessionId);

        List<Map<String, String>> agentList = new ArrayList<>();
        for (WChatAgent agent : agents) {
            if (!agent.isEnabled(worldId, sessionId)) continue;
            Map<String, String> agentInfo = new HashMap<>();
            agentInfo.put("name", agent.getName());
            agentInfo.put("title", agent.getTitle());
            agentList.add(agentInfo);
        }

        String json = objectMapper.writeValueAsString(agentList);
        log.debug("Returning {} agents: {}", agentList.size(), json);

        return CommandResult.withStreaming(0, "OK", List.of(json));
    }

    /**
     * Execute chat subcommand.
     * Returns agent responses as JSON in streamMessages.
     */
    private CommandResult executeChat(CommandContext context, List<String> args) throws JsonProcessingException {
        if (args.size() < 3) {
            return CommandResult.error("Usage: chat <agentName> <message>");
        }

        String agentName = args.get(1);
        String message = args.get(2);

        // Get metadata from context
        String playerId = getMetadata(context, "playerId");
        String chatId = getMetadata(context, "chatId");
        String sessionId = getMetadata(context, "sessionId");

        if (context.getWorldId() == null || context.getWorldId().isBlank()) {
            return CommandResult.error("worldId required in context");
        }

        // Convert worldId
        WorldId worldId = WorldId.unchecked(context.getWorldId());

        // Get agent
        WChatAgent agent = localProvider.getAgent(agentName);
        if (agent == null) {
            return CommandResult.error("Agent not found: " + agentName);
        }

        // Execute agent (use chatWithSession if sessionId is available)
        log.debug("Executing agent {} for player {} with message: {} (sessionId: {})",
                agentName, playerId, message, sessionId);
        List<WChatMessage> responses = sessionId != null && !sessionId.isBlank()
                ? agent.chatWithSession(worldId, chatId, playerId, message, sessionId)
                : agent.chat(worldId, chatId, playerId, message);

        // Save to DB if chatId provided
        if (chatId != null && !chatId.isBlank()) {
            log.debug("Saving {} agent responses to DB (chatId: {})", responses.size(), chatId);
            chatService.saveMessages(worldId, chatId, sessionId, false, responses);
        }

        // Return as JSON in streamMessages
        String json = objectMapper.writeValueAsString(responses);
        log.debug("Returning {} responses: {}", responses.size(), json);

        return CommandResult.withStreaming(0, "OK", List.of(json));
    }

    /**
     * Execute execute-command subcommand.
     * Returns agent command responses as JSON in streamMessages.
     */
    private CommandResult executeAgentCommand(CommandContext context, List<String> args) throws JsonProcessingException {
        if (args.size() < 4) {
            return CommandResult.error("Usage: execute-command <agentName> <command> <paramsJson>");
        }

        String agentName = args.get(1);
        String command = args.get(2);
        String paramsJson = args.get(3);

        // Get metadata from context
        String playerId = getMetadata(context, "playerId");
        String chatId = getMetadata(context, "chatId");
        String sessionId = getMetadata(context, "sessionId");

        if (context.getWorldId() == null || context.getWorldId().isBlank()) {
            return CommandResult.error("worldId required in context");
        }

        // Convert worldId
        WorldId worldId = WorldId.unchecked(context.getWorldId());

        // Get agent
        WChatAgent agent = localProvider.getAgent(agentName);
        if (agent == null) {
            return CommandResult.error("Agent not found: " + agentName);
        }

        // Parse params
        @SuppressWarnings("unchecked")
        Map<String, Object> params = objectMapper.readValue(paramsJson, Map.class);

        // Add sessionId to params if available
        if (sessionId != null && !sessionId.isBlank()) {
            params.put("sessionId", sessionId);
        }

        // Execute command on agent
        log.debug("Executing command {} on agent {} for player {} (sessionId: {})",
                command, agentName, playerId, sessionId);
        List<WChatMessage> responses = agent.executeCommand(worldId, chatId, playerId, command, params);

        // Save to DB if chatId provided
        if (chatId != null && !chatId.isBlank()) {
            log.debug("Saving {} command responses to DB (chatId: {})", responses.size(), chatId);
            chatService.saveMessages(worldId, chatId, sessionId, false, responses);
        }

        // Return as JSON in streamMessages
        String json = objectMapper.writeValueAsString(responses);
        log.debug("Returning {} command responses: {}", responses.size(), json);

        return CommandResult.withStreaming(0, "OK", List.of(json));
    }

    /**
     * Helper to get metadata value from context.
     */
    private String getMetadata(CommandContext context, String key) {
        if (context.getMetadata() == null) {
            return null;
        }
        Object value = context.getMetadata().get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public String getHelp() {
        return "Chat connector for remote agent access\n" +
                "\n" +
                "Subcommands:\n" +
                "  agent-list                              - Get list of available agents\n" +
                "  chat <agent> <message>                  - Chat with an agent\n" +
                "  execute-command <agent> <cmd> <params>  - Execute command on agent\n" +
                "\n" +
                "Examples:\n" +
                "  /chat.Connector agent-list\n" +
                "  /chat.Connector chat eliza Hello\n" +
                "  /chat.Connector execute-command eliza status {}";
    }
}
