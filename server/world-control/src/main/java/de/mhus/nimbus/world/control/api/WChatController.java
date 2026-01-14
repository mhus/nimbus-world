package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.*;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for player chat operations.
 * Provides access to chat functionality for players.
 */
@RestController
@RequestMapping("/control/player/chats")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Chats", description = "Chat management for players")
public class WChatController extends BaseEditorController {

    private final WChatService chatService;

    // DTOs
    public record ChatResponse(
            String chatId,
            String name,
            String type,
            Instant createdAt,
            Instant modifiedAt,
            boolean archived,
            String ownerId,
            String model
    ) {}

    public record MessageResponse(
            String messageId,
            String senderId,
            String message,
            String type,
            boolean command,
            Instant createdAt
    ) {}

    public record CreateChatRequest(
            String name,
            String type,
            String agentName,
            String model
    ) {}

    public record SendMessageRequest(
            String message
    ) {}

    public record ExecuteCommandRequest(
            String command,
            Map<String, Object> params
    ) {}

    public record AgentResponse(
            String name,
            String title
    ) {}

    /**
     * Get all chats for a player.
     * GET /control/player/chats/{worldId}/{playerId}
     */
    @GetMapping("/{worldId}/{playerId}")
    @Operation(summary = "Get all chats for a player")
    public ResponseEntity<?> getChatsForPlayer(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Player ID") @PathVariable String playerId,
            @Parameter(description = "Filter by archived status") @RequestParam(required = false, defaultValue = "false") boolean archived,
            HttpServletRequest request) {

        // Get authenticated playerId from AccessFilter (JWT cookie)
        String userId = (String) request.getAttribute("accessUserId");
        String characterId = (String) request.getAttribute("accessCharacterId");

        log.debug("GET chats for player: worldId={}, pathPlayerId={}, userId={}, characterId={}, archived={}",
            worldId, playerId, userId, characterId, archived);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }

        // Build authenticated playerId from JWT claims
        String actualPlayerId;
        if (userId != null && characterId != null) {
            actualPlayerId = PlayerId.of(userId, characterId)
                .map(PlayerId::getId)
                .orElse(playerId); // Fallback to path parameter
        } else {
            actualPlayerId = playerId; // Fallback to path parameter
        }

        if (Strings.isBlank(actualPlayerId)) {
            return bad("playerId required - not authenticated");
        }

        try {
            WorldId wId = WorldId.unchecked(worldId);
            List<WChat> chats = chatService.getChatsForOwner(wId, null, actualPlayerId, archived);

            List<ChatResponse> responses = chats.stream()
                    .map(this::toChatResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting chats for player: worldId={}, playerId={}", worldId, playerId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get chats: " + e.getMessage()));
        }
    }

    /**
     * Get messages for a specific chat.
     * GET /control/player/chats/{worldId}/{chatId}/messages
     */
    @GetMapping("/{worldId}/{chatId}/messages")
    @Operation(summary = "Get messages for a chat")
    public ResponseEntity<?> getChatMessages(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Chat ID") @PathVariable String chatId,
            @Parameter(description = "After Message ID") @RequestParam(required = false) String afterMessageId,
            @Parameter(description = "Limit") @RequestParam(required = false, defaultValue = "50") int limit) {

        log.debug("GET messages: worldId={}, chatId={}, afterMessageId={} limit={}", worldId, chatId, afterMessageId, limit);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            return bad("chatId required");
        }

        try {
            WorldId wId = WorldId.unchecked(worldId);
            List<WChatMessage> messages =
                    Strings.isBlank(afterMessageId)
                        ?
                    chatService.getChatMessages(wId, chatId, limit)
                        :
                    chatService.getChatMessagesAfterMessageId(wId, chatId, afterMessageId, limit);

            List<MessageResponse> responses = messages.stream()
                    .map(this::toMessageResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting messages: worldId={}, chatId={}", worldId, chatId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get messages: " + e.getMessage()));
        }
    }

    /**
     * Create a new chat.
     * POST /control/player/chats/{worldId}/{playerId}
     */
    @PostMapping("/{worldId}/{playerId}")
    @Operation(summary = "Create a new chat")
    public ResponseEntity<?> createChat(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Player ID") @PathVariable String playerId,
            @RequestBody CreateChatRequest request,
            HttpServletRequest httpRequest) {

        // Get authenticated playerId from AccessFilter (JWT cookie)
        String userId = (String) httpRequest.getAttribute("accessUserId");
        String characterId = (String) httpRequest.getAttribute("accessCharacterId");

        // Build authenticated playerId from JWT claims
        String actualPlayerId;
        if (userId != null && characterId != null) {
            actualPlayerId = PlayerId.of(userId, characterId)
                .map(PlayerId::getId)
                .orElse(playerId); // Fallback to path parameter
        } else {
            actualPlayerId = playerId; // Fallback to path parameter
        }

        log.debug("POST create chat: worldId={}, pathPlayerId={}, actualPlayerId={}, request={}",
            worldId, playerId, actualPlayerId, request);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }
        if (Strings.isBlank(actualPlayerId)) {
            return bad("playerId required - not authenticated");
        }
        if (Strings.isBlank(request.name())) {
            return bad("name required");
        }
        if (Strings.isBlank(request.type())) {
            return bad("type required");
        }

        try {
            WorldId wId = WorldId.unchecked(worldId);
            String chatId = UUID.randomUUID().toString();

            WChat chat = chatService.save(wId, chatId, request.name(), request.type(), actualPlayerId, request.model());

            return ResponseEntity.ok(toChatResponse(chat));
        } catch (Exception e) {
            log.error("Error creating chat: worldId={}, playerId={}", worldId, playerId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create chat: " + e.getMessage()));
        }
    }

    /**
     * Send a message to an agent chat.
     * POST /control/player/chats/{worldId}/{chatId}/messages/{playerId}
     */
    @PostMapping("/{worldId}/{chatId}/messages/{playerId}")
    @Operation(summary = "Send a message to an agent chat")
    public ResponseEntity<?> sendMessage(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Chat ID") @PathVariable String chatId,
            @Parameter(description = "Player ID") @PathVariable String playerId,
            @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {

        log.debug("POST send message: worldId={}, chatId={}, playerId={}", worldId, chatId, playerId);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            return bad("chatId required");
        }
        if (Strings.isBlank(playerId)) {
            return bad("playerId required");
        }
        if (Strings.isBlank(request.message())) {
            return bad("message required");
        }

        try {
            WorldId wId = WorldId.unchecked(worldId);

            // Get chat to determine agent
            WChat chat = chatService.findByWorldIdAndChatId(wId, chatId)
                    .orElseThrow(() -> new IllegalArgumentException("Chat not found: " + chatId));

            // For agent chats, use the chat type as agent name (or you could store agentName in chat)
            String agentName = chat.getType(); // Assuming type contains agent name like "eliza"

            String playerMessageId = UUID.randomUUID().toString();

            // Extract sessionId from request (set by AccessFilterBase)
            String sessionId = (String) httpRequest.getAttribute("accessSessionId");
            log.debug("SessionId from request: {}", sessionId);

            // Chat with agent - this saves both player message and agent responses
            List<WChatMessage> responses = chatService.chatWithAgent(
                    wId, chatId, agentName, playerId, playerMessageId,
                    request.message(), sessionId);

            List<MessageResponse> messageResponses = responses.stream()
                    .map(this::toMessageResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(messageResponses);
        } catch (Exception e) {
            log.error("Error sending message: worldId={}, chatId={}, playerId={}", worldId, chatId, playerId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send message: " + e.getMessage()));
        }
    }

    /**
     * Execute a command on an agent chat.
     * POST /control/player/chats/{worldId}/{chatId}/execute-command/{playerId}
     */
    @PostMapping("/{worldId}/{chatId}/execute-command/{playerId}")
    @Operation(summary = "Execute a command on an agent")
    public ResponseEntity<?> executeCommand(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Chat ID") @PathVariable String chatId,
            @Parameter(description = "Player ID") @PathVariable String playerId,
            @RequestBody ExecuteCommandRequest request,
            HttpServletRequest httpRequest) {

        log.debug("POST execute command: worldId={}, chatId={}, playerId={}, command={}",
                worldId, chatId, playerId, request.command());

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            return bad("chatId required");
        }
        if (Strings.isBlank(playerId)) {
            return bad("playerId required");
        }
        if (Strings.isBlank(request.command())) {
            return bad("command required");
        }

        try {
            WorldId wId = WorldId.unchecked(worldId);

            // Get chat to determine agent
            WChat chat = chatService.findByWorldIdAndChatId(wId, chatId)
                    .orElseThrow(() -> new IllegalArgumentException("Chat not found: " + chatId));

            // For agent chats, use the chat type as agent name
            String agentName = chat.getType();

            // Get sessionId from request attributes (set by AccessFilter)
            String sessionId = (String) httpRequest.getAttribute("accessSessionId");

            // Merge sessionId into params
            Map<String, Object> params = request.params() != null
                    ? new java.util.HashMap<>(request.params())
                    : new java.util.HashMap<>();
            if (sessionId != null && !sessionId.isBlank()) {
                params.put("sessionId", sessionId);
                log.debug("Added sessionId to params: {}", sessionId);
            }

            // Execute command on agent
            List<WChatMessage> responses = chatService.executeAgentCommand(
                    wId, chatId, agentName, playerId,
                    request.command(), params);

            List<MessageResponse> messageResponses = responses.stream()
                    .map(this::toMessageResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(messageResponses);
        } catch (Exception e) {
            log.error("Error executing command: worldId={}, chatId={}, playerId={}",
                    worldId, chatId, playerId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to execute command: " + e.getMessage()));
        }
    }

    /**
     * Get available chat agents.
     * GET /control/player/chats/agents
     */
    @GetMapping("/agents")
    @Operation(summary = "Get available chat agents")
    public ResponseEntity<?> getAvailableAgents(HttpServletRequest request) {
        log.debug("GET available agents");

        // Extract worldId and sessionId from AccessFilter
        String worldId = (String) request.getAttribute("accessWorldId");
        String sessionId = (String) request.getAttribute("accessSessionId");

        log.debug("worldId={}, sessionId={}", worldId, sessionId);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required - not authenticated");
        }

        try {
            WorldId wId = WorldId.unchecked(worldId);
            List<WChatAgent> agents = chatService.getAvailableAgents(wId, sessionId);

            List<AgentResponse> responses = agents.stream()
                    .map(agent -> new AgentResponse(agent.getName(), agent.getTitle()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting available agents", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get agents: " + e.getMessage()));
        }
    }

    /**
     * Archive a chat.
     * PUT /control/player/chats/{worldId}/{chatId}/archive
     */
    @PutMapping("/{worldId}/{chatId}/archive")
    @Operation(summary = "Archive a chat")
    public ResponseEntity<?> archiveChat(
            @Parameter(description = "World ID") @PathVariable String worldId,
            @Parameter(description = "Chat ID") @PathVariable String chatId) {

        log.debug("PUT archive chat: worldId={}, chatId={}", worldId, chatId);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            return bad("chatId required");
        }

        try {
            WorldId wId = WorldId.unchecked(worldId);
            boolean success = chatService.archive(wId, chatId);

            if (success) {
                return ResponseEntity.ok(Map.of("message", "Chat archived successfully"));
            } else {
                return notFound("Chat not found");
            }
        } catch (Exception e) {
            log.error("Error archiving chat: worldId={}, chatId={}", worldId, chatId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to archive chat: " + e.getMessage()));
        }
    }

    // Helper methods

    private ChatResponse toChatResponse(WChat chat) {
        return new ChatResponse(
                chat.getChatId(),
                chat.getName(),
                chat.getType(),
                chat.getCreatedAt(),
                chat.getModifiedAt(),
                chat.isArchived(),
                chat.getOwnerId(),
                chat.getModel()
        );
    }

    private MessageResponse toMessageResponse(WChatMessage message) {
        return new MessageResponse(
                message.getMessageId(),
                message.getSenderId(),
                message.getMessage(),
                message.getType(),
                message.isCommand(),
                message.getCreatedAt()
        );
    }
}
