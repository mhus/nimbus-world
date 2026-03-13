package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic proxy chat agent that delegates to a target agent via an internal sub-chat.
 * The target agent can run on any server — routing is handled by the existing
 * enqueueOrRoute mechanism.
 *
 * <p>The proxy creates an internal chat linked to the parent chat and forwards
 * all messages through it. Responses are collected via Redis BLPOP notification
 * and returned to the parent chat.</p>
 *
 * <p>The internal chat ID is persisted in the parent chat's agentState so it
 * survives session restarts.</p>
 *
 * <p>Subclasses only need to provide name, title, scope, and the target agent name.</p>
 */
@Slf4j
public abstract class ProxyChatAgent implements WChatAgent {

    private static final String STATE_INTERNAL_CHAT_ID = "internalChatId";
    private static final Duration REPLY_TIMEOUT = Duration.ofSeconds(30);

    private final WChatService chatService;

    /**
     * Tracks internal chatIds per parent chatId for the current session.
     * Populated from agentState on session start.
     */
    private final ConcurrentHashMap<String, String> internalChatIds = new ConcurrentHashMap<>();

    protected ProxyChatAgent(WChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * The target agent name to delegate to (e.g., "eliza").
     */
    protected abstract String getTargetAgentName();

    @Override
    public void onSessionStarted(WChat chat, WChatSessionQueue queue) {
        // Restore internal chatId from persisted state
        if (chat.getAgentState() != null) {
            String internalChatId = (String) chat.getAgentState().get(STATE_INTERNAL_CHAT_ID);
            if (internalChatId != null) {
                internalChatIds.put(chat.getChatId(), internalChatId);
                log.debug("Restored internal chatId={} for parent chatId={}", internalChatId, chat.getChatId());
            }
        }
    }

    @Override
    public void onSessionEnded(WChat chat) {
        // Persist internal chatId to agentState
        String internalChatId = internalChatIds.remove(chat.getChatId());
        if (internalChatId != null) {
            Map<String, Object> state = chat.getAgentState() != null
                    ? new java.util.HashMap<>(chat.getAgentState())
                    : new java.util.HashMap<>();
            state.put(STATE_INTERNAL_CHAT_ID, internalChatId);
            chat.setAgentState(state);
            log.debug("Persisted internal chatId={} for parent chatId={}", internalChatId, chat.getChatId());
        }
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        return chatWithSession(worldId, chatId, playerId, message, null);
    }

    @Override
    public List<WChatMessage> chatWithSession(WorldId worldId, String chatId, String playerId,
                                               String message, String sessionId) {
        // Get or create internal chat
        String internalChatId = getOrCreateInternalChat(worldId, chatId, playerId);

        // Get last message ID in internal chat before sending (for polling after)
        String lastMessageId = getLastMessageId(worldId, internalChatId);

        // Enqueue message to internal chat → routes to target agent's server
        WChatSessionMessage sessionMsg = WChatSessionMessage.builder()
                .type(WChatSessionMessage.Type.CHAT)
                .worldId(worldId.toBaseWorldId().getId())
                .chatId(internalChatId)
                .agentName(getTargetAgentName())
                .playerId(playerId)
                .playerMessageId(java.util.UUID.randomUUID().toString())
                .message(message)
                .sessionId(sessionId)
                .build();

        // Save player message to internal chat
        WChatMessage playerMsg = WChatMessage.builder()
                .worldId(worldId.toBaseWorldId().getId())
                .chatId(internalChatId)
                .messageId(sessionMsg.getPlayerMessageId())
                .senderId(playerId)
                .message(message)
                .type("text")
                .build();
        playerMsg.touchCreate();
        chatService.saveMessage(playerMsg);

        // Route to target agent
        chatService.enqueueOrRoute(getTargetAgentName(), sessionMsg);

        log.debug("Proxy {} forwarded message to {} via internal chat {}: parentChatId={}",
                getName(), getTargetAgentName(), internalChatId, chatId);

        // Wait for reply via Redis BLPOP
        List<WChatMessage> replies = chatService.waitForReply(worldId, internalChatId, lastMessageId, REPLY_TIMEOUT);

        if (replies.isEmpty()) {
            log.warn("Proxy {} got no reply from {} within timeout: parentChatId={}", getName(), getTargetAgentName(), chatId);
            WChatMessage timeoutMsg = WChatMessage.builder()
                    .messageId(java.util.UUID.randomUUID().toString())
                    .senderId(getName() + "-agent")
                    .message("The target agent did not respond in time. Please try again.")
                    .type("error")
                    .createdAt(java.time.Instant.now())
                    .build();
            return List.of(timeoutMsg);
        }

        // Filter out the player's own message and return only agent responses
        return replies.stream()
                .filter(msg -> !playerId.equals(msg.getSenderId()))
                .toList();
    }

    private String getOrCreateInternalChat(WorldId worldId, String parentChatId, String ownerId) {
        return internalChatIds.computeIfAbsent(parentChatId, key -> {
            // Check if internal chat already exists for this parent (from a previous session)
            List<WChat> children = chatService.findChildren(worldId, parentChatId);
            for (WChat child : children) {
                if (getTargetAgentName().equals(child.getType())) {
                    log.debug("Found existing internal chat: chatId={} for parent={}", child.getChatId(), parentChatId);
                    return child.getChatId();
                }
            }

            // Create new internal chat
            WChat internalChat = chatService.createInternalChat(
                    worldId, parentChatId,
                    getName() + " → " + getTargetAgentName(),
                    getTargetAgentName(),
                    ownerId,
                    "Internal proxy chat from " + getName()
            );
            log.info("Created internal chat: chatId={} for parent={}, targetAgent={}",
                    internalChat.getChatId(), parentChatId, getTargetAgentName());
            return internalChat.getChatId();
        });
    }

    private String getLastMessageId(WorldId worldId, String chatId) {
        List<WChatMessage> lastMessages = chatService.getChatMessages(worldId, chatId, 1);
        if (lastMessages.isEmpty()) {
            return null;
        }
        return lastMessages.getLast().getMessageId();
    }
}
