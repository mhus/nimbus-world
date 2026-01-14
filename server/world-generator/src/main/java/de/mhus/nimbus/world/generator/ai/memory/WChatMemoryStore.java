package de.mhus.nimbus.world.generator.ai.memory;

import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatMessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat memory store that adapts WChatMessages to langchain4j ChatMemoryStore.
 *
 * This adapter allows langchain4j agents to use WChatMessages as conversation history.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WChatMemoryStore implements ChatMemoryStore {

    private final WChatMessageRepository chatMessageRepository;

    // In-memory cache for current session messages (cleared after use)
    private final Map<Object, List<ChatMessage>> sessionCache = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // Check cache first
        if (sessionCache.containsKey(memoryId)) {
            return sessionCache.get(memoryId);
        }

        // Load from database if ChatMemoryId
        if (memoryId instanceof ChatMemoryId chatMemoryId) {
            try {
                List<WChatMessage> history = chatMessageRepository
                    .findByWorldIdAndChatIdOrderByCreatedAtDesc(
                        chatMemoryId.worldId(),
                        chatMemoryId.chatId(),
                        PageRequest.of(0, 50)
                    );

                // Convert WChatMessage to langchain4j ChatMessage
                List<ChatMessage> messages = new ArrayList<>();
                for (int i = history.size() - 1; i >= 0; i--) { // Reverse to chronological order
                    WChatMessage msg = history.get(i);
                    ChatMessage chatMessage = convertToChatMessage(msg);
                    if (chatMessage != null) {
                        messages.add(chatMessage);
                    }
                }

                log.debug("Loaded {} messages from chat history for {}", messages.size(), chatMemoryId);
                return messages;

            } catch (Exception e) {
                log.error("Failed to load chat history for " + chatMemoryId, e);
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // Store in session cache
        sessionCache.put(memoryId, new ArrayList<>(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        sessionCache.remove(memoryId);
    }

    /**
     * Inject messages for the current session.
     * Used to provide context to agents from ongoing chat.
     */
    public void injectMessages(ChatMemoryId memoryId, List<WChatMessage> messages) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (WChatMessage msg : messages) {
            ChatMessage chatMessage = convertToChatMessage(msg);
            if (chatMessage != null) {
                chatMessages.add(chatMessage);
            }
        }
        sessionCache.put(memoryId, chatMessages);
        log.debug("Injected {} messages for session {}", chatMessages.size(), memoryId);
    }

    /**
     * Clear session cache after processing.
     */
    public void clearSession(ChatMemoryId memoryId) {
        sessionCache.remove(memoryId);
    }

    /**
     * Convert WChatMessage to langchain4j ChatMessage.
     */
    private ChatMessage convertToChatMessage(WChatMessage msg) {
        if (msg == null || msg.getMessage() == null) {
            return null;
        }

        // Agent messages are AiMessages
        if (msg.getSenderId() != null && msg.getSenderId().endsWith("-agent")) {
            return AiMessage.from(msg.getMessage());
        }

        // User messages are UserMessages
        return UserMessage.from(msg.getMessage());
    }
}
