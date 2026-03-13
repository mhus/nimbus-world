package de.mhus.nimbus.world.control.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatAgentProvider;
import de.mhus.nimbus.world.shared.chat.WChatAgentScope;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatSessionMessage;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.client.WorldClientService.CommandResponse;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for remote chat agent providers.
 * Handles agent discovery caching and remote command execution via WorldClientService.
 * Subclasses specify the target server and provider title.
 */
@Slf4j
public abstract class RemoteWChatAgentProvider implements WChatAgentProvider {

    protected final WorldClientService worldClientService;
    protected final ObjectMapper objectMapper;

    private Map<String, WChatAgent> cachedAgents;
    private Instant lastRefresh;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    protected RemoteWChatAgentProvider(WorldClientService worldClientService,
                                      ObjectMapper objectMapper) {
        this.worldClientService = worldClientService;
        this.objectMapper = objectMapper;
    }

    /**
     * Subclasses must provide the server URL for configuration validation.
     *
     * @return server URL or null if not configured
     */
    protected abstract String getServerUrl();

    /**
     * Subclasses must send the command to their specific target server.
     *
     * @param worldId World ID for context
     * @param commandName Command title
     * @param args Command arguments
     * @param context Command context
     * @return CompletableFuture with CommandResponse
     */
    protected abstract CompletableFuture<CommandResponse> sendCommand(
            String worldId,
            String commandName,
            List<String> args,
            CommandContext context);

    @Override
    public abstract String getProviderName();

    @Override
    public List<WChatAgent> getAvailableAgents() {
        refreshCacheIfNeeded();
        return new ArrayList<>(cachedAgents.values());
    }

    @Override
    public WChatAgent getAgent(String agentName) {
        refreshCacheIfNeeded();
        return cachedAgents.get(agentName);
    }

    @Override
    public boolean isAvailable() {
        String serverUrl = getServerUrl();
        return serverUrl != null && !serverUrl.isBlank();
    }

    private synchronized void refreshCacheIfNeeded() {
        if (cachedAgents == null ||
            lastRefresh == null ||
            Duration.between(lastRefresh, Instant.now()).compareTo(CACHE_TTL) > 0) {

            refreshCache();
        }
    }

    private void refreshCache() {
        Map<String, WChatAgent> newCache = new HashMap<>();
        String serverUrl = getServerUrl();

        if (serverUrl == null || serverUrl.isBlank()) {
            log.warn("Server URL not configured for provider {}", getProviderName());
            cachedAgents = newCache;
            lastRefresh = Instant.now();
            return;
        }

        try {
            // Call remote chat-connector agent-list
            CommandContext context = CommandContext.builder()
                    .worldId("00000000-0000-0000-0000-000000000000")
                    .build();

            CompletableFuture<CommandResponse> future = sendCommand(
                    context.getWorldId(),
                    "chat-connector",
                    List.of("agent-list"),
                    context
            );

            CommandResponse result = future.get(); // Blocking get

            if (result.rc() == 0 && result.streamMessages() != null && !result.streamMessages().isEmpty()) {
                List<Map<String, String>> agents = objectMapper.readValue(
                        result.streamMessages().get(0),
                        new TypeReference<List<Map<String, String>>>() {}
                );

                for (Map<String, String> agentInfo : agents) {
                    String name = agentInfo.get("name");
                    String title = agentInfo.get("title");
                    String scopeStr = agentInfo.get("scope");
                    WChatAgentScope scope = parseScope(scopeStr);
                    newCache.put(name, new RemoteWChatAgentWrapper(name, title, scope, this));
                }
                log.debug("Loaded {} agents from {} (provider: {})",
                        newCache.size(), serverUrl, getProviderName());
            } else {
                log.error("Failed to fetch agents from {}: rc={}, message={}",
                        serverUrl, result.rc(), result.message());
            }
        } catch (Exception e) {
            log.error("Failed to fetch agents from {} (provider: {})",
                    serverUrl, getProviderName(), e);
        }

        cachedAgents = newCache;
        lastRefresh = Instant.now();
    }

    private WChatAgentScope parseScope(String scopeStr) {
        if (scopeStr == null || scopeStr.isBlank()) {
            return WChatAgentScope.ALL;
        }
        try {
            return WChatAgentScope.valueOf(scopeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown agent scope: {}, defaulting to ALL", scopeStr);
            return WChatAgentScope.ALL;
        }
    }

    /**
     * Execute remote chat via command.
     * Package-private for RemoteWChatAgentWrapper.
     */
    List<WChatMessage> executeRemoteChat(String agentName, WorldId worldId,
                                        String playerId, String chatId, String message, String sessionId) {
        try {
            // Build context with metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("playerId", playerId);
            if (chatId != null) {
                metadata.put("chatId", chatId);
            }
            if (sessionId != null) {
                metadata.put("sessionId", sessionId);
            }

            CommandContext context = CommandContext.builder()
                    .worldId(worldId.toString())
                    .metadata(metadata)
                    .build();

            CompletableFuture<CommandResponse> future = sendCommand(
                    worldId.toString(),
                    "chat-connector",
                    List.of("chat", agentName, message),
                    context
            );

            CommandResponse result = future.get(); // Blocking get

            if (result.rc() == 0 && result.streamMessages() != null && !result.streamMessages().isEmpty()) {
                return objectMapper.readValue(
                        result.streamMessages().get(0),
                        new TypeReference<List<WChatMessage>>() {}
                );
            } else {
                throw new RuntimeException("Remote chat failed: " + result.message());
            }
        } catch (Exception e) {
            log.error("Failed to execute remote chat with agent {} (provider: {})",
                    agentName, getProviderName(), e);
            throw new RuntimeException("Remote agent communication failed", e);
        }
    }

    /**
     * Execute remote command via command.
     * Package-private for RemoteWChatAgentWrapper.
     */
    List<WChatMessage> executeRemoteCommand(String agentName, WorldId worldId, String playerId,
                                           String chatId, String command, Map<String, Object> params) {
        try {
            // Build context with metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("playerId", playerId);
            if (chatId != null) {
                metadata.put("chatId", chatId);
            }

            CommandContext context = CommandContext.builder()
                    .worldId(worldId.toString())
                    .metadata(metadata)
                    .build();

            // Serialize params to JSON
            String paramsJson = objectMapper.writeValueAsString(params != null ? params : new HashMap<>());

            CompletableFuture<CommandResponse> future = sendCommand(
                    worldId.toString(),
                    "chat-connector",
                    List.of("execute-command", agentName, command, paramsJson),
                    context
            );

            CommandResponse result = future.get(); // Blocking get

            if (result.rc() == 0 && result.streamMessages() != null && !result.streamMessages().isEmpty()) {
                return objectMapper.readValue(
                        result.streamMessages().get(0),
                        new TypeReference<List<WChatMessage>>() {}
                );
            } else {
                throw new RuntimeException("Remote command execution failed: " + result.message());
            }
        } catch (Exception e) {
            log.error("Failed to execute remote command {} on agent {} (provider: {})",
                    command, agentName, getProviderName(), e);
            throw new RuntimeException("Remote agent command execution failed", e);
        }
    }

    /**
     * Route a session message to the remote pod for async processing via chat-connector enqueue.
     * The remote pod's WChatExecutorService will create a local session with the real agent.
     */
    void routeEnqueue(WChatSessionMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            CommandContext ctx = CommandContext.builder()
                    .worldId(msg.getWorldId())
                    .build();
            sendCommand(msg.getWorldId(), "chat-connector", List.of("enqueue", json), ctx);
            log.debug("Routed enqueue to remote pod (provider: {}): chatId={}", getProviderName(), msg.getChatId());
        } catch (Exception e) {
            log.error("Failed to route enqueue to remote pod (provider: {})", getProviderName(), e);
        }
    }

    /**
     * Wrapper that implements WChatAgent interface for remote agents.
     */
    private static class RemoteWChatAgentWrapper implements WChatAgent {
        private final String name;
        private final String title;
        private final WChatAgentScope scope;
        private final RemoteWChatAgentProvider provider;

        public RemoteWChatAgentWrapper(String name, String title, WChatAgentScope scope, RemoteWChatAgentProvider provider) {
            this.name = name;
            this.title = title;
            this.scope = scope;
            this.provider = provider;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public WChatAgentScope getScope() {
            return scope;
        }

        @Override
        public String getTitle() {
            return title + " (Remote)";
        }

        @Override
        public boolean isLocal() {
            return false;
        }

        @Override
        public void routeMessage(WChatSessionMessage msg) {
            provider.routeEnqueue(msg);
        }

        @Override
        public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
            return provider.executeRemoteChat(name, worldId, playerId, chatId, message, null);
        }

        @Override
        public List<WChatMessage> chatWithSession(WorldId worldId, String chatId, String playerId, String message, String sessionId) {
            return provider.executeRemoteChat(name, worldId, playerId, chatId, message, sessionId);
        }

        @Override
        public List<WChatMessage> executeCommand(WorldId worldId, String chatId, String playerId,
                                                String command, Map<String, Object> params) {
            return provider.executeRemoteCommand(name, worldId, playerId, chatId, command, params);
        }
    }
}
