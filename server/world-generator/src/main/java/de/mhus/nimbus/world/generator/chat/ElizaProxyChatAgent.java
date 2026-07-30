package de.mhus.nimbus.world.generator.chat;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.ProxyChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatAgentScope;
import de.mhus.nimbus.world.shared.chat.WChatContext;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatService;
import org.springframework.stereotype.Component;

import java.util.List;

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
        return super.chatWithSession(worldId, chatId, playerId, message, sessionId, context);
    }
}
