package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.annotation.Lazy;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service for managing WChat instances, WChatMessage, and WChatAgent in the world.
 * Chats and messages exist per world (no instances).
 * Manages a registry of available chat agents with periodic refresh.
 */
@Service
@Slf4j
public class WChatService {

    private static final long AGENT_CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private final WChatRepository repository;
    private final WChatMessageRepository messageRepository;
    private final List<WChatAgentProvider> agentProviders;
    private final de.mhus.nimbus.world.shared.session.WSessionService wSessionService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final de.mhus.nimbus.world.shared.client.WorldClientService worldClientService;
    private final WChatExecutorService chatExecutorService;

    private volatile Map<String, WChatAgent> globalAgentMap;
    private volatile long agentMapTimestamp;

    /**
     * Constructor with dependency injection.
     */
    public WChatService(WChatRepository repository,
                       WChatMessageRepository messageRepository,
                       List<WChatAgentProvider> agentProviders,
                       de.mhus.nimbus.world.shared.session.WSessionService wSessionService,
                       com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                       de.mhus.nimbus.world.shared.client.WorldClientService worldClientService,
                       @Lazy WChatExecutorService chatExecutorService) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.agentProviders = agentProviders;
        this.wSessionService = wSessionService;
        this.objectMapper = objectMapper;
        this.worldClientService = worldClientService;
        this.chatExecutorService = chatExecutorService;

        log.info("WChatService initialized with {} agent providers", agentProviders.size());
    }

    /**
     * Get the global agent map, refreshing if stale (every 5 minutes).
     */
    private Map<String, WChatAgent> getAgentMap() {
        long now = System.currentTimeMillis();
        if (globalAgentMap == null || (now - agentMapTimestamp) > AGENT_CACHE_TTL_MS) {
            synchronized (this) {
                // Double-check after acquiring lock
                if (globalAgentMap == null || (now - agentMapTimestamp) > AGENT_CACHE_TTL_MS) {
                    globalAgentMap = createAgentMap();
                    agentMapTimestamp = now;
                }
            }
        }
        return globalAgentMap;
    }

    private Map<String, WChatAgent> createAgentMap() {
        var agentMap = new HashMap<String, WChatAgent>();

        for (WChatAgentProvider provider : agentProviders) {
            if (!provider.isAvailable()) {
                log.debug("Skipping unavailable provider: {}", provider.getProviderName());
                continue;
            }

            List<WChatAgent> providerAgents = provider.getAvailableAgents();
            log.debug("Loading {} agents from provider: {}", providerAgents.size(), provider.getProviderName());

            for (WChatAgent agent : providerAgents) {
                String agentName = agent.getName();

                // Check for duplicate names
                if (agentMap.containsKey(agentName)) {
                    // Use qualified name: provider:agent
                    String qualifiedName = provider.getProviderName() + ":" + agentName;
                    agentMap.put(qualifiedName, agent);
                    log.debug("Agent name conflict resolved: {} -> {}", agentName, qualifiedName);
                } else {
                    agentMap.put(agentName, agent);
                }
            }
        }

        log.info("Agent map initialized with {} agents from {} providers: {}",
                agentMap.size(), agentProviders.size(), agentMap.keySet());
        return agentMap;
    }

    /**
     * Find chat by chatId.
     * Instances always look up in their main world.
     */
    @Transactional(readOnly = true)
    public Optional<WChat> findByWorldIdAndChatId(WorldId worldId, String chatId) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndChatId(lookupWorld.getId(), chatId);
    }

    /**
     * Find all chats for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChat> findByWorldId(WorldId worldId) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Find chats by type for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChat> findByType(WorldId worldId, String type) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndType(lookupWorld.getId(), type);
    }

    /**
     * Find chats by archived status for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChat> findByArchived(WorldId worldId, boolean archived) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndArchived(lookupWorld.getId(), archived);
    }

    /**
     * Find chats by owner for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChat> findByOwnerId(WorldId worldId, String ownerId) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndOwnerId(lookupWorld.getId(), ownerId);
    }

    /**
     * Find chats by type and archived status for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChat> findByTypeAndArchived(WorldId worldId, String type, boolean archived) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndTypeAndArchived(lookupWorld.getId(), type, archived);
    }

    /**
     * Get all chats for a specific owner with optional type filter and archived status.
     * Filters out instances.
     *
     * @param worldId The world identifier
     * @param type The chat type filter (can be null to ignore type filtering)
     * @param ownerId The owner player ID
     * @param archived The archived status filter
     * @return List of chats matching the criteria
     */
    @Transactional(readOnly = true)
    public List<WChat> getChatsForOwner(WorldId worldId, String type, String ownerId, boolean archived) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(ownerId)) {
            throw new IllegalArgumentException("ownerId required");
        }

        var lookupWorld = worldId.toBaseWorldId();

        log.debug("getChatsForOwner: worldId={}, lookupWorldId={}, type={}, ownerId={}, archived={}",
                worldId.getId(), lookupWorld.getId(), type, ownerId, archived);

        // If type is provided, filter by all criteria
        if (!Strings.isBlank(type)) {
            List<WChat> result = repository.findByWorldIdAndTypeAndOwnerIdAndArchived(
                    lookupWorld.getId(), type, ownerId, archived);
            log.debug("Found {} chats with type filter", result.size());
            return result;
        }

        // If no type provided, filter only by ownerId and archived
        List<WChat> result = repository.findByWorldIdAndOwnerIdAndArchived(
                lookupWorld.getId(), ownerId, archived);
        log.debug("Found {} chats without type filter", result.size());

        return result;
    }

    /**
     * Save or update a chat.
     * Filters out instances - chats are stored per world.
     */
    @Transactional
    public WChat save(WorldId worldId, String chatId, String name, String type, String ownerId, String model) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            throw new IllegalArgumentException("chatId required");
        }
        if (Strings.isBlank(name)) {
            throw new IllegalArgumentException("name required");
        }
        if (Strings.isBlank(type)) {
            throw new IllegalArgumentException("type required");
        }

        var lookupWorld = worldId.toBaseWorldId();

        WChat chat = repository.findByWorldIdAndChatId(lookupWorld.getId(), chatId).orElseGet(() -> {
            WChat neu = WChat.builder()
                    .worldId(lookupWorld.getId())
                    .chatId(chatId)
                    .name(name)
                    .type(type)
                    .ownerId(ownerId)
                    .model(model)
                    .archived(false)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WChat: world={}, chatId={}, type={}", lookupWorld, chatId, type);
            return neu;
        });

        chat.setName(name);
        chat.setType(type);
        chat.setOwnerId(ownerId);
        chat.setModel(model);
        chat.touchUpdate();

        WChat saved = repository.save(chat);
        log.debug("Saved WChat: world={}, chatId={}, type={}", lookupWorld, chatId, type);
        return saved;
    }

    /**
     * Save or update a chat with WChat object.
     */
    @Transactional
    public WChat save(WChat chat) {
        if (chat.getCreatedAt() == null) {
            chat.touchCreate();
        } else {
            chat.touchUpdate();
        }
        WChat saved = repository.save(chat);
        log.debug("Saved WChat: world={}, chatId={}, type={}", chat.getWorldId(), chat.getChatId(), chat.getType());
        return saved;
    }

    /**
     * Update a chat.
     * Filters out instances.
     */
    @Transactional
    public Optional<WChat> update(WorldId worldId, String chatId, Consumer<WChat> updater) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndChatId(lookupWorld.getId(), chatId).map(chat -> {
            updater.accept(chat);
            chat.touchUpdate();
            WChat saved = repository.save(chat);
            log.debug("Updated WChat: world={}, chatId={}", lookupWorld, chatId);
            return saved;
        });
    }

    /**
     * Archive a chat.
     * Filters out instances.
     */
    @Transactional
    public boolean archive(WorldId worldId, String chatId) {
        return update(worldId, chatId, chat -> chat.setArchived(true)).isPresent();
    }

    /**
     * Unarchive a chat.
     * Filters out instances.
     */
    @Transactional
    public boolean unarchive(WorldId worldId, String chatId) {
        return update(worldId, chatId, chat -> chat.setArchived(false)).isPresent();
    }

    /**
     * Delete a chat.
     * Filters out instances.
     */
    @Transactional
    public boolean delete(WorldId worldId, String chatId) {
        var lookupWorld = worldId.toBaseWorldId();

        return repository.findByWorldIdAndChatId(lookupWorld.getId(), chatId).map(chat -> {
            repository.delete(chat);
            log.debug("Deleted WChat: world={}, chatId={}", lookupWorld, chatId);
            return true;
        }).orElse(false);
    }

    /**
     * Find all chats for a world with optional query filter.
     * Filters out instances - chats are per world only.
     */
    @Transactional(readOnly = true)
    public List<WChat> findByWorldIdAndQuery(WorldId worldId, String query) {
        var lookupWorld = worldId.toBaseWorldId();
        List<WChat> all = repository.findByWorldId(lookupWorld.getId());

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WChat> filterByQuery(List<WChat> chats, String query) {
        String lowerQuery = query.toLowerCase();
        return chats.stream()
                .filter(chat -> {
                    String chatId = chat.getChatId();
                    String name = chat.getName();
                    String type = chat.getType();
                    String ownerId = chat.getOwnerId();
                    return (chatId != null && chatId.toLowerCase().contains(lowerQuery)) ||
                            (name != null && name.toLowerCase().contains(lowerQuery)) ||
                            (type != null && type.toLowerCase().contains(lowerQuery)) ||
                            (ownerId != null && ownerId.toLowerCase().contains(lowerQuery));
                })
                .toList();
    }

    // ==================== Message Management ====================

    /**
     * Send/save a chat message.
     * Filters out instances - messages are stored per world.
     */
    @Transactional
    public WChatMessage sendMessage(WorldId worldId, String chatId, String messageId, String senderId, String message, String type) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            throw new IllegalArgumentException("chatId required");
        }
        if (Strings.isBlank(messageId)) {
            throw new IllegalArgumentException("messageId required");
        }
        if (Strings.isBlank(senderId)) {
            throw new IllegalArgumentException("senderId required");
        }
        if (Strings.isBlank(message)) {
            throw new IllegalArgumentException("message required");
        }
        if (Strings.isBlank(type)) {
            throw new IllegalArgumentException("type required");
        }

        var lookupWorld = worldId.toBaseWorldId();

        WChatMessage chatMessage = WChatMessage.builder()
                .worldId(lookupWorld.getId())
                .chatId(chatId)
                .messageId(messageId)
                .senderId(senderId)
                .message(message)
                .type(type)
                .build();
        chatMessage.touchCreate();

        WChatMessage saved = messageRepository.save(chatMessage);
        log.debug("Sent message: world={}, chatId={}, messageId={}, type={}", lookupWorld, chatId, messageId, type);
        return saved;
    }

    /**
     * Save or update a message with WChatMessage object.
     */
    @Transactional
    public WChatMessage saveMessage(WChatMessage message) {
        if (message.getCreatedAt() == null) {
            message.touchCreate();
        }
        WChatMessage saved = messageRepository.save(message);
        log.debug("Saved message: world={}, chatId={}, messageId={}", message.getWorldId(), message.getChatId(), message.getMessageId());
        return saved;
    }

    public void saveMessages(WorldId worldId, String chatId, String sessionId, boolean process, List<WChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        var lookupWorld = worldId.toBaseWorldId();
        for (WChatMessage message : messages) {
            message.setWorldId(lookupWorld.getId());
            message.setChatId(chatId);
            // Ensure message has required fields
            if (Strings.isBlank(message.getMessageId())) {
                message.setMessageId(UUID.randomUUID().toString());
            }
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(Instant.now());
            }
            if (process) {
                processMessage(worldId, sessionId, message);
            }
            saveMessage(message);
        }
    }
    /**
     * Find message by messageId.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public Optional<WChatMessage> findMessageByWorldIdAndChatIdAndMessageId(WorldId worldId, String chatId, String messageId) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.findByWorldIdAndChatIdAndMessageId(lookupWorld.getId(), chatId, messageId);
    }

    /**
     * Find all messages for specific chat.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> findMessagesByWorldIdAndChatId(WorldId worldId, String chatId) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.findByWorldIdAndChatId(lookupWorld.getId(), chatId);
    }

    /**
     * Get the last N messages for a specific chat, sorted by createdAt ascending (chronological order).
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> getChatMessages(WorldId worldId, String chatId, int limit) {
        var lookupWorld = worldId.toBaseWorldId();
        Pageable pageable = PageRequest.of(0, limit);

        // Fetch newest messages first (DESC)
        List<WChatMessage> messages = messageRepository.findByWorldIdAndChatIdOrderByCreatedAtDesc(
                lookupWorld.getId(), chatId, pageable);

        // Reverse to get chronological order (ASC)
        java.util.Collections.reverse(messages);

        return messages;
    }

    /**
     * Find messages for specific chat with pagination (newest first).
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> findMessagesByWorldIdAndChatIdNewestFirst(WorldId worldId, String chatId, int page, int size) {
        var lookupWorld = worldId.toBaseWorldId();
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByWorldIdAndChatIdOrderByCreatedAtDesc(lookupWorld.getId(), chatId, pageable);
    }

    /**
     * Find messages for specific chat with pagination (oldest first).
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> findMessagesByWorldIdAndChatIdOldestFirst(WorldId worldId, String chatId, int page, int size) {
        var lookupWorld = worldId.toBaseWorldId();
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByWorldIdAndChatIdOrderByCreatedAtAsc(lookupWorld.getId(), chatId, pageable);
    }

    /**
     * Find messages by type for specific chat.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> findMessagesByType(WorldId worldId, String chatId, String type) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.findByWorldIdAndChatIdAndType(lookupWorld.getId(), chatId, type);
    }

    /**
     * Find messages after a specific timestamp.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> findMessagesAfter(WorldId worldId, String chatId, Instant after) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.findByWorldIdAndChatIdAndCreatedAtAfter(lookupWorld.getId(), chatId, after);
    }

    /**
     * Find messages before a specific timestamp.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> findMessagesBefore(WorldId worldId, String chatId, Instant before) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.findByWorldIdAndChatIdAndCreatedAtBefore(lookupWorld.getId(), chatId, before);
    }

    /**
     * Find messages by sender for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WChatMessage> findMessagesBySenderId(WorldId worldId, String senderId) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.findByWorldIdAndSenderId(lookupWorld.getId(), senderId);
    }

    /**
     * Count messages in a specific chat.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public long countMessages(WorldId worldId, String chatId) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.countByWorldIdAndChatId(lookupWorld.getId(), chatId);
    }

    /**
     * Delete a specific message.
     * Filters out instances.
     */
    @Transactional
    public boolean deleteMessage(WorldId worldId, String chatId, String messageId) {
        var lookupWorld = worldId.toBaseWorldId();

        return messageRepository.findByWorldIdAndChatIdAndMessageId(lookupWorld.getId(), chatId, messageId).map(message -> {
            messageRepository.delete(message);
            log.debug("Deleted message: world={}, chatId={}, messageId={}", lookupWorld, chatId, messageId);
            return true;
        }).orElse(false);
    }

    /**
     * Delete all messages in a specific chat.
     * Filters out instances.
     */
    @Transactional
    public void deleteAllMessagesInChat(WorldId worldId, String chatId) {
        var lookupWorld = worldId.toBaseWorldId();
        messageRepository.deleteByWorldIdAndChatId(lookupWorld.getId(), chatId);
        log.debug("Deleted all messages in chat: world={}, chatId={}", lookupWorld, chatId);
    }

    // ==================== Agent Management ====================

    /**
     * Get a chat agent by name.
     *
     * @param agentName The technical name of the agent
     * @return Optional containing the agent if found
     */
    public Optional<WChatAgent> getAgent(String agentName) {
        return Optional.ofNullable(getAgentMap().get(agentName));
    }

    /**
     * Get all available chat agents filtered by scope.
     *
     * @param scope The scope to filter by (ALL matches all agents, PLAYER/EDITOR filter accordingly)
     * @return List of agents matching the scope
     */
    public List<WChatAgent> getAvailableAgents(WChatAgentScope scope) {
        return getAgentMap().values().stream()
                .filter(agent -> matchesScope(agent.getScope(), scope))
                .toList();
    }

    /**
     * Check if an agent's scope matches the requested scope filter.
     * An agent with scope ALL is visible in all contexts.
     * An agent with scope PLAYER is visible for PLAYER and ALL.
     * An agent with scope EDITOR is visible for EDITOR and ALL.
     */
    private boolean matchesScope(WChatAgentScope agentScope, WChatAgentScope requestedScope) {
        if (requestedScope == WChatAgentScope.ALL) {
            return true;
        }
        return agentScope == WChatAgentScope.ALL || agentScope == requestedScope;
    }

    /**
     * Chat with an agent and save the messages.
     */
    @Transactional
    public List<WChatMessage> chatWithAgent(WorldId worldId, String chatId, String agentName,
                                           String playerId, String playerMessageId,
                                           String message) {
        return chatWithAgent(worldId, chatId, agentName, playerId, playerMessageId, message, null);
    }

    /**
     * Chat with an agent with session context.
     * Saves player message, gets agent responses, and saves them.
     */
    @Transactional
    public List<WChatMessage> chatWithAgent(WorldId worldId, String chatId, String agentName,
                                           String playerId, String playerMessageId,
                                           String message, String sessionId) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            throw new IllegalArgumentException("chatId required");
        }
        if (Strings.isBlank(agentName)) {
            throw new IllegalArgumentException("agentName required");
        }
        if (Strings.isBlank(playerId)) {
            throw new IllegalArgumentException("playerId required");
        }

        // Get the agent
        WChatAgent agent = getAgent(agentName)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentName));

        var lookupWorld = worldId.toBaseWorldId();

        // Save player message
        WChatMessage playerMessage = WChatMessage.builder()
                .worldId(lookupWorld.getId())
                .chatId(chatId)
                .messageId(playerMessageId)
                .senderId(playerId)
                .message(message)
                .type("text")
                .build();
        playerMessage.touchCreate();
        messageRepository.save(playerMessage);
        log.debug("Saved player message: world={}, chatId={}, playerId={}", lookupWorld, chatId, playerId);

        // Get agent responses (use chatWithSession if sessionId is available)
        List<WChatMessage> responses = sessionId != null && !sessionId.isBlank()
                ? agent.chatWithSession(worldId, chatId, playerId, message, sessionId)
                : agent.chat(worldId, chatId, playerId, message);

        // Save agent responses and handle model-selector commands
        saveMessages(worldId, chatId, sessionId, true, responses);

        log.debug("Agent {} generated {} responses for chat: world={}, chatId={}, sessionId={}",
                agentName, responses.size(), lookupWorld, chatId, sessionId);

        return responses;
    }

    private void processMessage(WorldId worldId, String sessionId, WChatMessage response) {
        var lookupWorld = worldId.toBaseWorldId();
        // Handle model-selector command: extract ModelSelector data and store in Redis
        if ("model-selector".equals(response.getType()) &&
                response.isCommand() &&
                sessionId != null &&
                !sessionId.isBlank()) {

            try {
                // Parse ModelSelector data from message field (JSON array of strings)
                List<String> modelSelectorData = objectMapper.readValue(
                        response.getMessage(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                );

                // Store in Redis
                wSessionService.updateModelSelector(sessionId, modelSelectorData);

                log.info("Stored ModelSelector in Redis: sessionId={}, blocks={}",
                        sessionId, modelSelectorData.size());

                // Send ShowModelSelectorCommand to player
                sendShowModelSelectorCommand(lookupWorld.getId(), sessionId);

            } catch (Exception e) {
                log.error("Failed to store ModelSelector in Redis for sessionId: {}", sessionId, e);
            }
        }
    }

    /**
     * Execute a command on an agent and save the responses.
     */
    @Transactional
    public List<WChatMessage> executeAgentCommand(WorldId worldId, String chatId, String agentName,
                                                 String playerId, String command,
                                                 Map<String, Object> params) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(chatId)) {
            throw new IllegalArgumentException("chatId required");
        }
        if (Strings.isBlank(agentName)) {
            throw new IllegalArgumentException("agentName required");
        }
        if (Strings.isBlank(playerId)) {
            throw new IllegalArgumentException("playerId required");
        }
        if (Strings.isBlank(command)) {
            throw new IllegalArgumentException("command required");
        }

        var sessionId = String.valueOf(params.get("sessionId"));
        if (Strings.isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId required in params");
        }

        // Get the agent
        WChatAgent agent = getAgent(agentName)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentName));

        var lookupWorld = worldId.toBaseWorldId();

        // Execute command on agent
        List<WChatMessage> responses = agent.executeCommand(worldId, chatId, playerId, command, params);

        // Save agent responses
        for (WChatMessage response : responses) {
            response.setWorldId(lookupWorld.getId());
            response.setChatId(chatId);
            if (response.getCreatedAt() == null) {
                response.touchCreate();
            }
            messageRepository.save(response);
        }

        log.debug("Agent {} executed command {} with {} responses for chat: world={}, chatId={}",
                agentName, command, responses.size(), lookupWorld, chatId);

        return responses;
    }

    @Transactional
    public List<WChatMessage> getChatMessagesAfterMessageId(WorldId worldId, String chatId, String messageId, int limit) {
        var lookupWorld = worldId.toBaseWorldId();
        Pageable pageable = PageRequest.of(0, limit);

        // Find the reference message to get its createdAt timestamp
        Optional<WChatMessage> referenceMessageOpt = messageRepository.findByWorldIdAndChatIdAndMessageId(
                lookupWorld.getId(), chatId, messageId);

        if (referenceMessageOpt.isEmpty()) {
            log.warn("Reference message not found: world={}, chatId={}, messageId={}", lookupWorld, chatId, messageId);
            return Collections.emptyList();
        }

        Instant referenceTimestamp = referenceMessageOpt.get().getCreatedAt();

        // Fetch messages after the reference timestamp
        List<WChatMessage> messages = messageRepository.findByWorldIdAndChatIdAndCreatedAtAfterOrderByCreatedAtAsc(
                lookupWorld.getId(), chatId, referenceTimestamp, pageable);

        return messages;
    }

    /**
     * Send ShowModelSelector command to player.
     */
    private void sendShowModelSelectorCommand(String worldId, String sessionId) {
        try {
            Optional<de.mhus.nimbus.world.shared.session.WSession> wSessionOpt =
                    wSessionService.getWithPlayerUrl(sessionId);

            if (wSessionOpt.isEmpty()) {
                log.warn("No WSession found for sessionId: {}, cannot send ShowModelSelector command", sessionId);
                return;
            }

            de.mhus.nimbus.world.shared.session.WSession wSession = wSessionOpt.get();
            String playerUrl = wSession.getPlayerUrl();

            if (playerUrl == null || playerUrl.isBlank()) {
                log.warn("No player URL available for session {}, cannot send ShowModelSelector command", sessionId);
                return;
            }

            de.mhus.nimbus.world.shared.commands.CommandContext ctx =
                    de.mhus.nimbus.world.shared.commands.CommandContext.builder()
                            .worldId(worldId)
                            .sessionId(sessionId)
                            .originServer("world-control")
                            .build();

            worldClientService.sendPlayerCommand(
                    worldId,
                    sessionId,
                    playerUrl,
                    "client.ShowModelSelector",
                    List.of(),
                    ctx
            );

            log.info("Sent ShowModelSelector command to player: sessionId={}, playerUrl={}", sessionId, playerUrl);

        } catch (Exception e) {
            log.error("Failed to send ShowModelSelector command for sessionId: {}", sessionId, e);
        }
    }

    public Optional<WChatMessage> findByWorldIdAndChatIdAndMessageId(WorldId worldId, String chatId, String messageId) {
        var lookupWorld = worldId.toBaseWorldId();
        return messageRepository.findByWorldIdAndChatIdAndMessageId(lookupWorld.getId(), chatId, messageId);
    }

    // ==================== Async Enqueue Methods ====================

    /**
     * Enqueue a player message for async processing.
     * Saves the player message to DB immediately, then enqueues for agent processing.
     * If the session is active on a remote pod, routes the message there.
     */
    public WChatMessage enqueuePlayerMessage(WorldId worldId, String chatId, String agentName,
                                              String playerId, String playerMessageId,
                                              String message, String sessionId) {
        var lookupWorld = worldId.toBaseWorldId();

        // Save player message to DB immediately
        WChatMessage playerMessage = WChatMessage.builder()
                .worldId(lookupWorld.getId())
                .chatId(chatId)
                .messageId(playerMessageId)
                .senderId(playerId)
                .message(message)
                .type("text")
                .build();
        playerMessage.touchCreate();
        messageRepository.save(playerMessage);
        log.debug("Saved player message: world={}, chatId={}, playerId={}", lookupWorld, chatId, playerId);

        // Build session message for async processing
        WChatSessionMessage sessionMsg = WChatSessionMessage.builder()
                .type(WChatSessionMessage.Type.CHAT)
                .worldId(lookupWorld.getId())
                .chatId(chatId)
                .agentName(agentName)
                .playerId(playerId)
                .playerMessageId(playerMessageId)
                .message(message)
                .sessionId(sessionId)
                .build();

        // Enqueue for processing
        WChatExecutorService.EnqueueResult result = chatExecutorService.enqueue(sessionMsg);
        if (result instanceof WChatExecutorService.EnqueueResult.Remote remote) {
            routeToRemotePod(remote.url(), sessionMsg);
        }

        return playerMessage;
    }

    /**
     * Enqueue a player command for async processing.
     * If the session is active on a remote pod, routes the command there.
     */
    public void enqueuePlayerCommand(WorldId worldId, String chatId, String agentName,
                                     String playerId, String command,
                                     Map<String, Object> params, String sessionId) {
        var lookupWorld = worldId.toBaseWorldId();

        WChatSessionMessage sessionMsg = WChatSessionMessage.builder()
                .type(WChatSessionMessage.Type.COMMAND)
                .worldId(lookupWorld.getId())
                .chatId(chatId)
                .agentName(agentName)
                .playerId(playerId)
                .command(command)
                .commandParams(params)
                .sessionId(sessionId)
                .build();

        WChatExecutorService.EnqueueResult result = chatExecutorService.enqueue(sessionMsg);
        if (result instanceof WChatExecutorService.EnqueueResult.Remote remote) {
            routeToRemotePod(remote.url(), sessionMsg);
        }
    }

    /**
     * Process an agent chat message (called by WChatSession).
     * Executes the agent and saves responses.
     * If the agent supports queue-based processing, passes the session queue
     * so the agent can consume follow-up messages during processing.
     */
    void processAgentChat(WChatSessionMessage msg, WChatSessionQueue sessionQueue) {
        WorldId worldId = WorldId.unchecked(msg.getWorldId());

        WChatAgent agent = getAgent(msg.getAgentName())
                .orElse(null);
        if (agent == null) {
            log.error("Agent not found for async chat: agentName={}", msg.getAgentName());
            return;
        }

        try {
            List<WChatMessage> responses;
            if (agent.supportsQueue()) {
                responses = agent.chatWithQueue(worldId, msg.getChatId(), msg.getPlayerId(),
                        msg.getMessage(), msg.getSessionId(), sessionQueue);
            } else if (msg.getSessionId() != null && !msg.getSessionId().isBlank()) {
                responses = agent.chatWithSession(worldId, msg.getChatId(), msg.getPlayerId(),
                        msg.getMessage(), msg.getSessionId());
            } else {
                responses = agent.chat(worldId, msg.getChatId(), msg.getPlayerId(), msg.getMessage());
            }

            saveMessages(worldId, msg.getChatId(), msg.getSessionId(), true, responses);

            log.debug("Async agent {} generated {} responses: chatId={}",
                    msg.getAgentName(), responses.size(), msg.getChatId());
        } catch (Exception e) {
            log.error("Error in async agent chat: agentName={}, chatId={}", msg.getAgentName(), msg.getChatId(), e);
        }
    }

    /**
     * Process an agent command message (called by WChatSession).
     * Executes the command on the agent and saves responses.
     */
    void processAgentCommand(WChatSessionMessage msg) {
        WorldId worldId = WorldId.unchecked(msg.getWorldId());
        var lookupWorld = worldId.toBaseWorldId();

        WChatAgent agent = getAgent(msg.getAgentName())
                .orElse(null);
        if (agent == null) {
            log.error("Agent not found for async command: agentName={}", msg.getAgentName());
            return;
        }

        try {
            Map<String, Object> params = msg.getCommandParams() != null
                    ? new HashMap<>(msg.getCommandParams())
                    : new HashMap<>();
            if (msg.getSessionId() != null && !msg.getSessionId().isBlank()) {
                params.put("sessionId", msg.getSessionId());
            }

            List<WChatMessage> responses = agent.executeCommand(worldId, msg.getChatId(), msg.getPlayerId(), msg.getCommand(), params);

            for (WChatMessage response : responses) {
                response.setWorldId(lookupWorld.getId());
                response.setChatId(msg.getChatId());
                if (response.getCreatedAt() == null) {
                    response.touchCreate();
                }
                messageRepository.save(response);
            }

            log.debug("Async agent {} executed command {} with {} responses: chatId={}",
                    msg.getAgentName(), msg.getCommand(), responses.size(), msg.getChatId());
        } catch (Exception e) {
            log.error("Error in async agent command: agentName={}, command={}, chatId={}",
                    msg.getAgentName(), msg.getCommand(), msg.getChatId(), e);
        }
    }

    /**
     * Route a session message to a remote pod via the chat-connector command.
     */
    private void routeToRemotePod(String remoteUrl, WChatSessionMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);

            de.mhus.nimbus.world.shared.commands.CommandContext ctx =
                    de.mhus.nimbus.world.shared.commands.CommandContext.builder()
                            .worldId(msg.getWorldId())
                            .sessionId(msg.getSessionId())
                            .originServer("world-control")
                            .build();

            worldClientService.sendControlCommand(
                    msg.getWorldId(),
                    "chat-connector",
                    List.of("enqueue", json),
                    ctx
            );

            log.debug("Routed message to remote pod: url={}, chatId={}", remoteUrl, msg.getChatId());
        } catch (Exception e) {
            log.error("Failed to route message to remote pod: url={}, chatId={}", remoteUrl, msg.getChatId(), e);
        }
    }
}
