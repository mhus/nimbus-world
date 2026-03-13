package de.mhus.nimbus.world.generator.chat;

import de.mhus.nimbus.world.shared.chat.AbstractElizaChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatAgentScope;
import de.mhus.nimbus.world.shared.chat.WChatService;
import org.springframework.stereotype.Component;

/**
 * Generator-specific Eliza chatbot agent in world-generator.
 */
@Component
public class GeneratorElizaChatAgent extends AbstractElizaChatAgent {

    public GeneratorElizaChatAgent(WChatService chatService) {
        super(chatService);
    }

    @Override
    public String getName() {
        return "generator-eliza";
    }

    @Override
    public String getTitle() {
        return "Generator Eliza (Terrain Edition)";
    }

    @Override
    public WChatAgentScope getScope() {
        return WChatAgentScope.ALL;
    }
}
