package de.mhus.nimbus.world.generator.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.blocks.BlockManipulatorService;
import de.mhus.nimbus.world.generator.blocks.BlockToolService;
import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatService;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.util.ModelSelectorUtil;
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
 * Technical Block Builder Chat Agent.
 * Accepts JSON commands for block manipulation and generation.
 *
 * Example usage:
 * <pre>
 * {
 *   "defaults": {
 *     "blockType": "n:s"
 *   }
 * }
 * </pre>
 *
 * <pre>
 * {
 *   "plateau": {
 *     "transform": "position,forward",
 *     "width": 10,
 *     "depth": 5
 *   }
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TechnicalBlockChatAgent implements WChatAgent {

    private static final String AGENT_ID = "technical-block-builder-agent";

    private final BlockManipulatorService blockManipulatorService;
    private final BlockToolService blockToolService;
    private final WSessionService wSessionService;
    private final WChatService chatService;
    private final WorldClientService worldClientService;
    private final ObjectMapper objectMapper;
    private final Executor executors = Executors.newCachedThreadPool();

    @Override
    public String getName() {
        return "technical-block-builder";
    }

    @Override
    public String getTitle() {
        return "Technical Block Builder";
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        return chatWithSession(worldId, chatId, playerId, message, null);
    }

    @Override
    public List<WChatMessage> chatWithSession(WorldId worldId, String chatId, String playerId, String message, String sessionId) {
        log.info("🏗️ Technical Block Builder processing message from player {} (session={}): {}",
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
            return List.of(createErrorMessage(worldId, "JSON must be an object with manipulator name as key"));
        }

        ObjectNode jsonObject = (ObjectNode) jsonNode;
        if (jsonObject.isEmpty()) {
            return List.of(createErrorMessage(worldId, "JSON object is empty. Please provide a manipulator command."));
        }

        // Extract manipulator name and params
        // Expected format: { "manipulatorName": { params } }
        String manipulatorName = jsonObject.fieldNames().next();
        JsonNode paramsNode = jsonObject.get(manipulatorName);

        if (!paramsNode.isObject()) {
            return List.of(createErrorMessage(worldId,
                    "Parameters for '" + manipulatorName + "' must be an object"));
        }

        ObjectNode params = (ObjectNode) paramsNode;

        log.info("Executing manipulator '{}' with params: {} (sessionId={})", manipulatorName, params, sessionId);

        // Extract context fields from params (optional)
        // If sessionId is provided as method parameter, it takes precedence over params
        String contextSessionId = sessionId != null && !sessionId.isBlank()
                ? sessionId
                : (params.has("sessionId") ? params.get("sessionId").asText() : null);

        String layerDataId = params.has("layerDataId") ? params.get("layerDataId").asText() : null;
        String layerName = params.has("layerName") ? params.get("layerName").asText() : null;
        String modelName = params.has("modelName") ? params.get("modelName").asText() : null;
        String groupId = params.has("groupId") ? params.get("groupId").asText() : null;

        // Load missing context fields from EditState if sessionId is available
        if (contextSessionId != null && !contextSessionId.isBlank()) {
            log.info("🔍 Loading EditState for sessionId: {}", contextSessionId);
            try {
                var editStateOpt = wSessionService.getEditState(contextSessionId);
                if (editStateOpt.isPresent()) {
                    var editState = editStateOpt.get();
                    log.info("✅ EditState found: layer={}, layerDataId={}, modelName={}, group={}",
                            editState.getSelectedLayer(), editState.getLayerDataId(),
                            editState.getModelName(), editState.getSelectedGroup());

                    // Load layerDataId if not provided
                    if (layerDataId == null || layerDataId.isBlank()) {
                        layerDataId = editState.getLayerDataId();
                        log.info("📥 Loaded layerDataId from EditState: {}", layerDataId);
                    }

                    // Load layerName if not provided
                    if (layerName == null || layerName.isBlank()) {
                        layerName = editState.getSelectedLayer();
                        log.info("📥 Loaded layerName from EditState: {}", layerName);
                    }

                    // Load modelName if not provided (for MODEL layers)
                    if (modelName == null || modelName.isBlank()) {
                        modelName = editState.getModelName();
                        log.info("📥 Loaded modelName from EditState: {}", modelName);
                    }

                    // Load groupId if not provided (null is default)
                    if (groupId == null || groupId.isBlank()) {
                        groupId = editState.getSelectedGroup();
                        log.info("📥 Loaded groupId from EditState: {}", groupId);
                    }
                } else {
                    log.warn("❌ No EditState found for sessionId: {}", contextSessionId);
                }
            } catch (Exception e) {
                log.error("❌ Failed to load EditState for sessionId: {}", contextSessionId, e);
            }
        } else {
            log.warn("⚠️ No sessionId available to load EditState");
        }

        log.info("📊 Final context values: sessionId={}, layerDataId={}, layerName={}, modelName={}, groupId={}",
                contextSessionId, layerDataId, layerName, modelName, groupId);

        // Build context
        ManipulatorContext context = ManipulatorContext.builder()
                .service(blockManipulatorService)
                .worldId(worldId.getId())
                .sessionId(contextSessionId)
                .layerDataId(layerDataId)
                .layerName(layerName)
                .modelName(modelName)
                .groupId(groupId)
                .originalParams(trimmedMessage)
                .params(params)
                .build();

        executors.execute(() -> {
            log.info("Started virtual thread for manipulator '{}'", manipulatorName);
            var responses = executeManipulator(manipulatorName, worldId, playerId, context);
            chatService.saveMessages(worldId, chatId, sessionId, true, responses);
        });

        return List.of(
                WChatMessage.builder()
                        .worldId(worldId.withoutInstance().getId())
                        .messageId(UUID.randomUUID().toString())
                        .senderId(AGENT_ID)
                        .message("⏳ Processing manipulator '" + manipulatorName + "'...")
                        .type("text")
                        .createdAt(Instant.now())
                        .build()
        );
    }

    private List<WChatMessage> executeManipulator(String manipulatorName, WorldId worldId, String playerId, ManipulatorContext context) {
        // Execute manipulator using BlockToolService
        BlockToolService.BlockToolResult result = blockToolService.executeManipulator(manipulatorName, context);

        // Build response messages
        List<WChatMessage> responses = new ArrayList<>();

        if (!result.isSuccess()) {
            // Execution failed
            return List.of(createErrorMessage(worldId, result.getError()));
        }

        // Success - add text message
        WChatMessage textMessage = WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message(result.getMessage())
                .type("text")
                .createdAt(Instant.now())
                .build();
        responses.add(textMessage);

        // If ModelSelector is present, create command message with ModelSelector data as JSON
        // world-control will parse this and store it in Redis with the sessionId
        if (result.getModelSelector() != null) {
            try {
                // Convert ModelSelector to JSON string
                List<String> modelSelectorData = ModelSelectorUtil.toStringList(result.getModelSelector());
                String modelSelectorJson = objectMapper.writeValueAsString(modelSelectorData);

                log.info("Generated ModelSelector: blocks={}", result.getBlockCount());

                // Create command message with ModelSelector data as JSON in message field
                // world-control will parse this and store in Redis
                WChatMessage commandMessage = WChatMessage.builder()
                        .worldId(worldId.withoutInstance().getId())
                        .messageId(UUID.randomUUID().toString())
                        .senderId(AGENT_ID)
                        .message(modelSelectorJson)  // ModelSelector data as JSON
                        .type("model-selector")
                        .command(true)
                        .createdAt(Instant.now())
                        .build();
                responses.add(commandMessage);

                log.debug("Created model-selector command message with {} blocks", result.getBlockCount());

            } catch (Exception e) {
                log.error("Failed to create ModelSelector command message", e);
                responses.add(createErrorMessage(worldId,
                        "Warning: Failed to create model selector: " + e.getMessage()));
            }
        }

        return responses;
    }

    @Override
    public List<WChatMessage> executeCommand(WorldId worldId, String chatId, String playerId,
                                            String command, Map<String, Object> params) {
        log.info("🏗️ Technical Block Builder executing command '{}' from player {}, params: {}",
                command, playerId, params);

        // Handle "model-selector" command - reload ModelSelector from command message and display
        if ("model-selector".equals(command)) {
            String sessionId = params != null && params.containsKey("sessionId")
                    ? params.get("sessionId").toString()
                    : null;
            String messageId = params != null && params.containsKey("messageId")
                    ? params.get("messageId").toString()
                    : null;

            if (sessionId == null || sessionId.isBlank()) {
                return List.of(createErrorMessage(worldId, "SessionId required for model-selector command"));
            }
            if (messageId == null || messageId.isBlank()) {
                return List.of(createErrorMessage(worldId, "MessageId required for model-selector command"));
            }
            if (chatId == null || chatId.isBlank()) {
                return List.of(createErrorMessage(worldId, "ChatId required for model-selector command"));
            }

            try {
                // Load the command message containing ModelSelector JSON
                Optional<WChatMessage> messageOpt = chatService.findByWorldIdAndChatIdAndMessageId(
                        worldId, chatId, messageId);

                if (messageOpt.isEmpty()) {
                    return List.of(createErrorMessage(worldId, "Command message not found: " + messageId));
                }

                WChatMessage commandMessage = messageOpt.get();

                // Parse ModelSelector data from message field (JSON array of strings)
                List<String> modelSelectorData = objectMapper.readValue(
                        commandMessage.getMessage(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                );

                // Store in Redis
                wSessionService.updateModelSelector(sessionId, modelSelectorData);

                log.info("Re-activated ModelSelector in Redis: sessionId={}, blocks={}",
                        sessionId, modelSelectorData.size());

                // Send ShowModelSelectorCommand to player
                sendShowModelSelectorCommand(worldId.getId(), sessionId);

                // Return confirmation message
                WChatMessage confirmMessage = WChatMessage.builder()
                        .worldId(worldId.withoutInstance().getId())
                        .messageId(UUID.randomUUID().toString())
                        .senderId(AGENT_ID)
                        .message("✅ Model selector re-activated: " + modelSelectorData.size() + " blocks highlighted")
                        .type("text")
                        .createdAt(Instant.now())
                        .build();

                return List.of(confirmMessage);

            } catch (Exception e) {
                log.error("Failed to reactivate model selector", e);
                return List.of(createErrorMessage(worldId,
                        "Failed to reactivate model selector: " + e.getMessage()));
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

    /**
     * Send ShowModelSelector command to player.
     * Loads playerUrl from WSession and sends command to player via WorldClientService.
     */
    private void sendShowModelSelectorCommand(String worldId, String sessionId) {
        try {
            // Get WSession with playerUrl
            Optional<WSession> wSessionOpt = wSessionService.getWithPlayerUrl(sessionId);

            if (wSessionOpt.isEmpty()) {
                log.warn("No WSession found for sessionId: {}, cannot send ShowModelSelector command", sessionId);
                return;
            }

            WSession wSession = wSessionOpt.get();
            String playerUrl = wSession.getPlayerUrl();

            if (playerUrl == null || playerUrl.isBlank()) {
                log.warn("No player URL available for session {}, cannot send ShowModelSelector command", sessionId);
                return;
            }

            // Build command context with sessionId
            CommandContext ctx = new CommandContext();
            ctx.setSessionId(sessionId);

            // Send ShowModelSelector command to player via WorldClientService
            worldClientService.sendPlayerCommand(
                    worldId,
                    sessionId,
                    playerUrl,
                    "client.ShowModelSelector",
                    List.of(),  // No arguments needed - uses session ID from context
                    ctx
            );

            log.info("Sent ShowModelSelector command to player: sessionId={}, playerUrl={}", sessionId, playerUrl);

        } catch (Exception e) {
            log.error("Failed to send ShowModelSelector command for sessionId: {}", sessionId, e);
        }
    }

    @Override
    public boolean isEnabled(WorldId worldId, String sessionId) {
        return true;
    }

}
