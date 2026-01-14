package de.mhus.nimbus.world.generator.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.flat.FlatToolService;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Technical Flat Builder Chat Agent.
 * Accepts JSON commands for flat terrain manipulation, creation, and export.
 * Uses FlatToolService for reusable flat tool operations.
 *
 * Example usage (manipulator):
 * <pre>
 * {
 *   "manipulator": "raise",
 *   "flatId": "flat-1",
 *   "x": 0,
 *   "z": 0,
 *   "sizeX": 100,
 *   "sizeZ": 100,
 *   "parameters": {
 *     "height": "10",
 *     "strength": "0.5"
 *   }
 * }
 * </pre>
 *
 * Example usage (create):
 * <pre>
 * {
 *   "create": true,
 *   "worldId": "world-1",
 *   "layerDataId": "layer-1",
 *   "flatId": "flat-1",
 *   "sizeX": 100,
 *   "sizeZ": 100,
 *   "mountX": 0,
 *   "mountZ": 0,
 *   "title": "My Flat",
 *   "description": "Test flat"
 * }
 * </pre>
 *
 * Example usage (export):
 * <pre>
 * {
 *   "export": true,
 *   "flatId": "flat-1",
 *   "worldId": "world-1",
 *   "layerName": "ground",
 *   "smoothCorners": true,
 *   "optimizeFaces": true
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TechnicalFlatChatAgent implements WChatAgent {

    private static final String AGENT_ID = "technical-flat-builder-agent";

    private final FlatToolService flatToolService;
    private final WChatService chatService;
    private final ObjectMapper objectMapper;
    private final Executor executors = Executors.newCachedThreadPool();

    @Override
    public String getName() {
        return "technical-flat-builder";
    }

    @Override
    public String getTitle() {
        return "Technical Flat Builder";
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        return chatWithSession(worldId, chatId, playerId, message, null);
    }

    @Override
    public List<WChatMessage> chatWithSession(WorldId worldId, String chatId, String playerId, String message, String sessionId) {
        log.info("🏔️ Technical Flat Builder processing message from player {} (session={}): {}",
                playerId, sessionId, message);

        // Check if message is empty
        if (message == null || message.trim().isBlank()) {
            return List.of(createErrorMessage(worldId, "Empty message. Please provide a JSON command."));
        }

        String trimmedMessage = message.trim();

        // Parse JSON
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(trimmedMessage);
        } catch (Exception e) {
            log.warn("Failed to parse JSON from player {}: {}", playerId, e.getMessage());
            return List.of(createErrorMessage(worldId, "Invalid JSON: " + e.getMessage()));
        }

        if (!jsonNode.isObject()) {
            return List.of(createErrorMessage(worldId, "JSON must be an object"));
        }

        ObjectNode jsonObject = (ObjectNode) jsonNode;
        if (jsonObject.isEmpty()) {
            return List.of(createErrorMessage(worldId, "JSON object is empty. Please provide a command."));
        }

        // Determine command type based on presence of specific fields
        boolean hasManipulator = jsonObject.has("manipulator");
        boolean hasCreate = jsonObject.has("create") && jsonObject.get("create").asBoolean();
        boolean hasExport = jsonObject.has("export") && jsonObject.get("export").asBoolean();

        // Validate that exactly one command type is present
        int commandCount = (hasManipulator ? 1 : 0) + (hasCreate ? 1 : 0) + (hasExport ? 1 : 0);
        if (commandCount == 0) {
            return List.of(createErrorMessage(worldId,
                "No command specified. Use 'manipulator', 'create', or 'export'."));
        }
        if (commandCount > 1) {
            return List.of(createErrorMessage(worldId,
                "Multiple commands specified. Use only one of: 'manipulator', 'create', 'export'."));
        }

        // Execute command in background
        executors.execute(() -> {
            List<WChatMessage> responses;
            try {
                if (hasManipulator) {
                    String manipulatorName = jsonObject.get("manipulator").asText();
                    log.info("Processing manipulator command: {}", manipulatorName);
                    responses = executeManipulator(worldId, jsonObject);
                } else if (hasCreate) {
                    log.info("Processing create command");
                    responses = executeCreate(worldId, jsonObject);
                } else { // hasExport
                    log.info("Processing export command");
                    responses = executeExport(worldId, jsonObject);
                }
            } catch (Exception e) {
                log.error("Command execution failed", e);
                responses = List.of(createErrorMessage(worldId, "Command execution failed: " + e.getMessage()));
            }
            chatService.saveMessages(worldId, chatId, sessionId, true, responses);
        });

        return List.of(
                WChatMessage.builder()
                        .worldId(worldId.withoutInstance().getId())
                        .messageId(UUID.randomUUID().toString())
                        .senderId(AGENT_ID)
                        .message("⏳ Processing command...")
                        .type("text")
                        .createdAt(Instant.now())
                        .build()
        );
    }

    /**
     * Execute a manipulator command using FlatToolService.
     */
    private List<WChatMessage> executeManipulator(WorldId worldId, ObjectNode params) {
        FlatToolService.FlatToolResult result = flatToolService.executeManipulator(params);

        if (!result.isSuccess()) {
            return List.of(createErrorMessage(worldId, result.getError()));
        }

        return List.of(
                WChatMessage.builder()
                        .worldId(worldId.withoutInstance().getId())
                        .messageId(UUID.randomUUID().toString())
                        .senderId(AGENT_ID)
                        .message("✅ " + result.getMessage())
                        .type("text")
                        .createdAt(Instant.now())
                        .build()
        );
    }

    /**
     * Execute a create command using FlatToolService.
     */
    private List<WChatMessage> executeCreate(WorldId worldId, ObjectNode params) {
        List<WChatMessage> responses = new ArrayList<>();

        FlatToolService.FlatToolResult result = flatToolService.executeCreate(params, worldId.getId());

        if (!result.isSuccess()) {
            return List.of(createErrorMessage(worldId, result.getError()));
        }

        // Add text message
        responses.add(WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message("✅ " + result.getMessage())
                .type("text")
                .createdAt(Instant.now())
                .build());

        // Add command message with flatId for future use
        if (result.getFlatId() != null) {
            responses.add(WChatMessage.builder()
                    .worldId(worldId.withoutInstance().getId())
                    .messageId(UUID.randomUUID().toString())
                    .senderId(AGENT_ID)
                    .message(result.getFlatId())
                    .type("flat-id")
                    .command(true)
                    .createdAt(Instant.now())
                    .build());
        }

        return responses;
    }

    /**
     * Execute an export command using FlatToolService.
     */
    private List<WChatMessage> executeExport(WorldId worldId, ObjectNode params) {
        FlatToolService.FlatToolResult result = flatToolService.executeExport(params, worldId.getId());

        if (!result.isSuccess()) {
            return List.of(createErrorMessage(worldId, result.getError()));
        }

        return List.of(
                WChatMessage.builder()
                        .worldId(worldId.withoutInstance().getId())
                        .messageId(UUID.randomUUID().toString())
                        .senderId(AGENT_ID)
                        .message("✅ " + result.getMessage())
                        .type("text")
                        .createdAt(Instant.now())
                        .build()
        );
    }

    @Override
    public List<WChatMessage> executeCommand(WorldId worldId, String chatId, String playerId,
                                            String command, Map<String, Object> params) {
        log.info("🏔️ Technical Flat Builder executing command '{}' from player {}, params: {}",
                command, playerId, params);

        // Handle "flat-id" command - store flatId for later use
        if ("flat-id".equals(command)) {
            String messageId = params != null && params.containsKey("messageId")
                    ? params.get("messageId").toString()
                    : null;

            if (messageId == null || messageId.isBlank()) {
                return List.of(createErrorMessage(worldId, "MessageId required for flat-id command"));
            }
            if (chatId == null || chatId.isBlank()) {
                return List.of(createErrorMessage(worldId, "ChatId required for flat-id command"));
            }

            try {
                // Load the command message containing flatId
                Optional<WChatMessage> messageOpt = chatService.findByWorldIdAndChatIdAndMessageId(
                        worldId, chatId, messageId);

                if (messageOpt.isEmpty()) {
                    return List.of(createErrorMessage(worldId, "Command message not found: " + messageId));
                }

                WChatMessage commandMessage = messageOpt.get();
                String flatId = commandMessage.getMessage();

                // Return confirmation message
                WChatMessage confirmMessage = WChatMessage.builder()
                        .worldId(worldId.withoutInstance().getId())
                        .messageId(UUID.randomUUID().toString())
                        .senderId(AGENT_ID)
                        .message("✅ Using flat: " + flatId)
                        .type("text")
                        .createdAt(Instant.now())
                        .build();

                return List.of(confirmMessage);

            } catch (Exception e) {
                log.error("Failed to load flat-id", e);
                return List.of(createErrorMessage(worldId,
                        "Failed to load flat-id: " + e.getMessage()));
            }
        }

        // Unknown command
        WChatMessage errorMessage = WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message("Unknown command: " + command)
                .type("error")
                .command(false)
                .createdAt(Instant.now())
                .build();

        return List.of(errorMessage);
    }

    /**
     * Create an error message.
     */
    private WChatMessage createErrorMessage(WorldId worldId, String errorText) {
        return WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message("❌ " + errorText)
                .type("error")
                .command(false)
                .createdAt(Instant.now())
                .build();
    }

    @Override
    public boolean isEnabled(WorldId worldId, String sessionId) {
        return true;
    }

}
