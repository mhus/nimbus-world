package de.mhus.nimbus.world.generator.chat;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.ProxyChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatAgentScope;
import de.mhus.nimbus.world.shared.chat.WChatContext;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Proxy agent on world-generator that delegates to the eliza agent on world-control.
 * Creates an internal sub-chat for the conversation and forwards all messages through.
 */
@Component
public class ElizaProxyChatAgent extends ProxyChatAgent {

    public ElizaProxyChatAgent(WChatService chatService) {
        super(chatService);
    }

    @Override
    public String getName() {
        return "eliza-proxy";
    }

    @Override
    public String getTitle() {
        return "Eliza (via Proxy)";
    }

    @Override
    public WChatAgentScope getScope() {
        return WChatAgentScope.ALL;
    }

    @Override
    protected String getTargetAgentName() {
        return "eliza";
    }

    @Override
    public List<WChatMessage> chatWithSession(WorldId worldId, String chatId, String playerId,
                                               String message, String sessionId, WChatContext context) {
        // Test: respond locally when message contains "proxy"
        if (message != null && message.toLowerCase().contains("proxy")) {
            WChatMessage response = WChatMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .senderId(getName() + "-agent")
                    .message("Hello from the proxy! I intercepted your message locally on world-generator. No forwarding to eliza.")
                    .type("text")
                    .createdAt(Instant.now())
                    .build();
            return List.of(response);
        }
        return super.chatWithSession(worldId, chatId, playerId, message, sessionId, context);
    }
}
