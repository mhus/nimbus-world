package de.mhus.nimbus.world.control.chat;

import de.mhus.nimbus.world.shared.chat.AbstractElizaChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatAgentScope;
import de.mhus.nimbus.world.shared.chat.WChatService;
import org.springframework.stereotype.Component;

/**
 * Standard Eliza chatbot agent in world-control.
 */
@Component
public class ElizaChatAgent extends AbstractElizaChatAgent {

    public ElizaChatAgent(WChatService chatService) {
        super(chatService);
    }

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
}
