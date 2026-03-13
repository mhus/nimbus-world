package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for Eliza-style chatbot agents.
 * Manages the lifecycle of active chat instances (ElizaActiveChat).
 * Subclasses only need to provide name, title, and scope.
 */
@Slf4j
public abstract class AbstractElizaChatAgent implements WChatAgent {

    private final WChatService chatService;
    private final ConcurrentHashMap<String, ElizaActiveChat> activeChats = new ConcurrentHashMap<>();

    protected AbstractElizaChatAgent(WChatService chatService) {
        this.chatService = chatService;
    }

    protected String getAgentId() {
        return getName() + "-agent";
    }

    @Override
    public void onSessionStarted(WChat chat, WChatSessionQueue queue) {
        var ac = new ElizaActiveChat(getAgentId(), chatService, chat, queue);
        activeChats.put(chat.getChatId(), ac);
        log.info("{} session started: chatId={}", getName(), chat.getChatId());
    }

    @Override
    public void onSessionEnded(WChat chat) {
        var ac = activeChats.remove(chat.getChatId());
        if (ac != null) {
            ac.persistState(chat);
        }
        log.info("{} session ended: chatId={}", getName(), chat.getChatId());
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        var ac = activeChats.get(chatId);
        if (ac != null) {
            return ac.chat(worldId, chatId, playerId, message);
        }
        // Fallback if no active session (e.g. called via connector command)
        log.warn("{} chat without active session (queue will be null): chatId={}", getName(), chatId);
        return new ElizaActiveChat(getAgentId(), chatService, null, null)
                .chat(worldId, chatId, playerId, message);
    }

    @Override
    public IdleResult onIdle(WorldId worldId, String chatId, WChatSessionQueue queue) {
        var ac = activeChats.get(chatId);
        if (ac != null) {
            return ac.onIdle(worldId, chatId, queue);
        }
        return IdleResult.IDLE;
    }
}
