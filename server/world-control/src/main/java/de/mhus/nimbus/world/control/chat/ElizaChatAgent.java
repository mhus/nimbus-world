package de.mhus.nimbus.world.control.chat;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.WChat;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatAgentScope;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatService;
import de.mhus.nimbus.world.shared.chat.WChatSessionQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Eliza-style chatbot agent for testing.
 * All per-chat logic lives in {@link ElizaActiveChat}.
 * This class only manages the lifecycle of active chat instances.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ElizaChatAgent implements WChatAgent {

    private final WChatService chatService;
    private final ConcurrentHashMap<String, ElizaActiveChat> activeChats = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "eliza";
    }

    @Override
    public String getTitle() {
        return "Eliza Chatbot";
    }

    @Override
    public WChatAgentScope getScope() {
        return WChatAgentScope.ALL;
    }

    @Override
    public void onSessionStarted(WChat chat, WChatSessionQueue queue) {
        var ac = new ElizaActiveChat(chatService, chat, queue);
        activeChats.put(chat.getChatId(), ac);
        log.debug("Eliza session started: chatId={}", chat.getChatId());
    }

    @Override
    public void onSessionEnded(WChat chat) {
        var ac = activeChats.remove(chat.getChatId());
        if (ac != null) {
            ac.persistState(chat);
        }
        log.debug("Eliza session ended: chatId={}", chat.getChatId());
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        var ac = activeChats.get(chatId);
        if (ac != null) {
            return ac.chat(worldId, chatId, playerId, message);
        }
        // Fallback if no active session (e.g. called via connector command)
        return new ElizaActiveChat(chatService, null, null).chat(worldId, chatId, playerId, message);
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
