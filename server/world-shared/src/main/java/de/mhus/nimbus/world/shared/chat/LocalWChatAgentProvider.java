package de.mhus.nimbus.world.shared.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Local provider for chat agents.
 * Manages agents that are available as Spring beans in the local application context.
 */
@Component
@Slf4j
public class LocalWChatAgentProvider implements WChatAgentProvider {

    @Autowired(required = false)
    @Lazy
    private List<WChatAgent> localAgents;
    private Map<String, WChatAgent> agentMap;

    @Override
    public String getProviderName() {
        return "local";
    }

    @Override
    public List<WChatAgent> getAvailableAgents() {
        initializeAgentsIfNeeded();
        return List.copyOf(agentMap.values());
    }

    @Override
    public WChatAgent getAgent(String agentName) {
        initializeAgentsIfNeeded();
        return agentMap.get(agentName);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private synchronized void initializeAgentsIfNeeded() {
        if (agentMap == null) {
            if (localAgents == null || localAgents.isEmpty()) {
                agentMap = Collections.emptyMap();
                log.debug("No local chat agents found");
            } else {
                agentMap = localAgents.stream()
                        .collect(Collectors.toMap(WChatAgent::getName, agent -> agent));
                log.debug("Initialized agent map with {} agents: {}", agentMap.size(), agentMap.keySet());
            }
        }
    }
}
