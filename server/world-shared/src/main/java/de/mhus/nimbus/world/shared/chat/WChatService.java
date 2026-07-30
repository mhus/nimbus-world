package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.annotation.Lazy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

    private static final String REPLY_KEY_PREFIX = "wchat:reply:";
    private static final Duration REPLY_KEY_TTL = Duration.ofSeconds(60);

    private final WChatRepository repository;
    private final WChatMessageRepository messageRepository;
    private final List<WChatAgentProvider> agentProviders;
    private final List<WChatMessageProcessor> messageProcessors;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final de.mhus.nimbus.world.shared.client.WorldClientService worldClientService;
    private final StringRedisTemplate redis;
    private final WChatExecutorService chatExecutorService;
    private final MongoTemplate mongoTemplate;

    private volatile Map<String, WChatAgent> globalAgentMap;
    private volatile long agentMapTimestamp;

    /**
     * Constructor with dependency injection.
     */
    public WChatService(WChatRepository repository,
                       WChatMessageRepository messageRepository,
                       List<WChatAgentProvider> agentProviders,
                       List<WChatMessageProcessor> messageProcessors,
                       tools.jackson.databind.ObjectMapper objectMapper,
                       de.mhus.nimbus.world.shared.client.WorldClientService worldClientService,
                       StringRedisTemplate redis,
                       @Lazy WChatExecutorService chatExecutorService,
                       MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.agentProviders = agentProviders;
        this.messageProcessors = messageProcessors;
        this.objectMapper = objectMapper;
        this.worldClientService = worldClientService;
        this.redis = redis;
        this.chatExecutorService = chatExecutorService;
        this.mongoTemplate = mongoTemplate;

        log.info("WChatService initialized with {} agent providers, {} message processors",
                agentProviders.size(), messageProcessors.size());
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

        // If type is provided, filter by all criteria (excluding internal chats)
        if (!Strings.isBlank(type)) {
            List<WChat> result = repository.findByWorldIdAndTypeAndOwnerIdAndArchivedAndInternal(
                    lookupWorld.getId(), type, ownerId, archived, false);
            log.debug("Found {} chats with type filter", result.size());
            return result;
        }

        // If no type provided, filter only by ownerId and archived (excluding internal chats)
        List<WChat> result = repository.findByWorldIdAndOwnerIdAndArchivedAndInternal(
                lookupWorld.getId(), ownerId, archived, false);
        log.debug("Found {} chats without type filter", result.size());

        return result;
    }

    /**
     * Save or update a chat.
     * Filters out instances - chats are stored per world.
     */
    @Transactional
    public WChat save(WorldId worldId, String chatId, String name, String type, String ownerId, String hint) {
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
                    .hint(hint)
                    .archived(false)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WChat: world={}, chatId={}, type={}", lookupWorld, chatId, type);
            return neu;
        });

        chat.setName(name);
        chat.setType(type);
        chat.setOwnerId(ownerId);
        chat.setHint(hint);
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
     * Archive a chat and all its child chats (internal sub-chats).
     */
    @Transactional
    public boolean archive(WorldId worldId, String chatId) {
        boolean result = update(worldId, chatId, chat -> chat.setArchived(true)).isPresent();
        if (result) {
            archiveChildren(worldId, chatId);
        }
        return result;
    }

    /**
     * Unarchive a chat and all its child chats (internal sub-chats).
     */
    @Transactional
    public boolean unarchive(WorldId worldId, String chatId) {
        boolean result = update(worldId, chatId, chat -> chat.setArchived(false)).isPresent();
        if (result) {
            unarchiveChildren(worldId, chatId);
        }
        return result;
    }

    /**
     * Delete a chat and all its child chats (internal sub-chats) including their messages.
     */
    @Transactional
    public boolean delete(WorldId worldId, String chatId) {
        var lookupWorld = worldId.toBaseWorldId();

        return repository.findByWorldIdAndChatId(lookupWorld.getId(), chatId).map(chat -> {
            // Delete children first
            deleteChildren(worldId, chatId);
            // Delete messages of this chat
            messageRepository.deleteByWorldIdAndChatId(lookupWorld.getId(), chatId);
            // Delete the chat itself
            repository.delete(chat);
            log.debug("Deleted WChat with children: world={}, chatId={}", lookupWorld, chatId);
            return true;
        }).orElse(false);
    }

    /**
     * Find all child chats (internal sub-chats) of a parent chat.
     */
    @Transactional(readOnly = true)
    public List<WChat> findChildren(WorldId worldId, String parentChatId) {
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndParentChatId(lookupWorld.getId(), parentChatId);
    }

    /**
     * Create an internal sub-chat linked to a parent chat.
     * Internal chats are not visible in the UI and are cascaded on archive/delete.
     */
    @Transactional
    public WChat createInternalChat(WorldId worldId, String parentChatId, String name, String type, String ownerId, String hint) {
        var lookupWorld = worldId.toBaseWorldId();
        String chatId = UUID.randomUUID().toString();

        WChat chat = WChat.builder()
                .worldId(lookupWorld.getId())
                .chatId(chatId)
                .name(name)
                .type(type)
                .ownerId(ownerId)
                .hint(hint)
                .parentChatId(parentChatId)
                .internal(true)
                .archived(false)
                .build();
        chat.touchCreate();

        WChat saved = repository.save(chat);
        log.debug("Created internal WChat: world={}, chatId={}, parentChatId={}, type={}",
                lookupWorld, chatId, parentChatId, type);
        return saved;
    }

    private void archiveChildren(WorldId worldId, String parentChatId) {
        List<WChat> children = findChildren(worldId, parentChatId);
        for (WChat child : children) {
            child.setArchived(true);
            child.touchUpdate();
            repository.save(child);
            archiveChildren(worldId, child.getChatId());
        }
    }

    private void unarchiveChildren(WorldId worldId, String parentChatId) {
        List<WChat> children = findChildren(worldId, parentChatId);
        for (WChat child : children) {
            child.setArchived(false);
            child.touchUpdate();
            repository.save(child);
            unarchiveChildren(worldId, child.getChatId());
        }
    }

    private void deleteChildren(WorldId worldId, String parentChatId) {
        var lookupWorld = worldId.toBaseWorldId();
        List<WChat> children = findChildren(worldId, parentChatId);
        for (WChat child : children) {
            deleteChildren(worldId, child.getChatId());
            messageRepository.deleteByWorldIdAndChatId(lookupWorld.getId(), child.getChatId());
            repository.delete(child);
            log.debug("Deleted child WChat: world={}, chatId={}, parentChatId={}", lookupWorld, child.getChatId(), parentChatId);
        }
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
        // Notify any waiting proxy agents that new messages are available
        notifyReply(lookupWorld.getId(), chatId);
    }

    /**
     * Notify waiting consumers that new messages are available for a chat.
     * Uses Redis LPUSH so BLPOP consumers wake up immediately.
     */
    private void notifyReply(String worldId, String chatId) {
        try {
            String key = REPLY_KEY_PREFIX + worldId + ":" + chatId;
            redis.opsForList().leftPush(key, "reply");
            redis.expire(key, REPLY_KEY_TTL);
        } catch (Exception e) {
            log.warn("Failed to notify reply for chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Wait for new messages in a chat using Redis BLPOP.
     * Blocks the current (virtual) thread until a notification arrives or timeout expires.
     * After waking up, reads new messages from MongoDB.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID to wait on
     * @param afterMessageId The last known message ID (to fetch only new messages)
     * @param timeout Maximum time to wait
     * @return New messages, or empty list on timeout
     */
    public List<WChatMessage> waitForReply(WorldId worldId, String chatId, String afterMessageId, Duration timeout) {
        var lookupWorld = worldId.toBaseWorldId();
        String key = REPLY_KEY_PREFIX + lookupWorld.getId() + ":" + chatId;

        try {
            // Block until notification or timeout
            String result = redis.opsForList().rightPop(key, timeout.toSeconds(), TimeUnit.SECONDS);
            if (result == null) {
                log.debug("waitForReply timed out: chatId={}", chatId);
                return Collections.emptyList();
            }

            // Read new messages from MongoDB
            if (Strings.isBlank(afterMessageId)) {
                return getChatMessages(worldId, chatId, 50);
            }
            return getChatMessagesAfterMessageId(worldId, chatId, afterMessageId, 50);
        } catch (Exception e) {
            log.error("Error waiting for reply: chatId={}", chatId, e);
            return Collections.emptyList();
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

    /**
     * Delete ALL chat channels and messages of a world. Owner-level bulk
     * operation so callers do not touch the WChat/WChatMessage collections
     * directly (data ownership). The given worldId is used verbatim (no base
     * normalization) to match the resource-cleanup contract.
     *
     * @param worldId World identifier (exact value)
     * @return total number of deleted documents (messages + channels)
     */
    @Transactional
    public int deleteByWorldId(String worldId) {
        Query query = new Query(Criteria.where("worldId").is(worldId));
        var messages = mongoTemplate.remove(query, WChatMessage.class);
        var chats = mongoTemplate.remove(new Query(Criteria.where("worldId").is(worldId)), WChat.class);

        log.info("Deleted chat for world {}: {} messages, {} channels",
                worldId, messages.getDeletedCount(), chats.getDeletedCount());
        return (int) (messages.getDeletedCount() + chats.getDeletedCount());
    }

    /**
     * Distinct world IDs that have chat channels or messages (owner-level;
     * avoids callers querying the WChat/WChatMessage collections directly).
     *
     * @return sorted list of distinct world IDs
     */
    public List<String> findDistinctWorldIds() {
        Set<String> worldIds = new HashSet<>();
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WChat.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(new Query(), "worldId", WChatMessage.class, String.class));
        return worldIds.stream().sorted().toList();
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

        // Build context with full worldId and call agent
        WChatContext context = WChatContext.builder()
                .fullWorldId(worldId)
                .sessionId(sessionId)
                .build();
        List<WChatMessage> responses;
        if (sessionId != null && !sessionId.isBlank()) {
            responses = agent.chatWithSession(lookupWorld, chatId, playerId, message, sessionId, context);
        } else {
            responses = agent.chat(lookupWorld, chatId, playerId, message, context);
        }

        // Save agent responses and handle model-selector commands
        saveMessages(worldId, chatId, sessionId, true, responses);

        log.debug("Agent {} generated {} responses for chat: world={}, chatId={}, sessionId={}",
                agentName, responses.size(), lookupWorld, chatId, sessionId);

        return responses;
    }

    private void processMessage(WorldId worldId, String sessionId, WChatMessage response) {
        for (WChatMessageProcessor processor : messageProcessors) {
            try {
                if (processor.canProcess(response)) {
                    processor.process(worldId, sessionId, response);
                }
            } catch (Exception e) {
                log.error("Message processor {} failed for message type={}: {}",
                        processor.getClass().getSimpleName(), response.getType(), e.getMessage(), e);
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

        Object sessionIdRaw = params.get("sessionId");
        String sessionId = sessionIdRaw == null ? null : sessionIdRaw.toString();
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
                .fullWorldId(worldId.getId())
                .chatId(chatId)
                .agentName(agentName)
                .playerId(playerId)
                .playerMessageId(playerMessageId)
                .message(message)
                .sessionId(sessionId)
                .build();

        // Route to remote pod or enqueue locally
        enqueueOrRoute(agentName, sessionMsg);

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
                .fullWorldId(worldId.getId())
                .chatId(chatId)
                .agentName(agentName)
                .playerId(playerId)
                .command(command)
                .commandParams(params)
                .sessionId(sessionId)
                .build();

        // Route to remote pod or enqueue locally
        enqueueOrRoute(agentName, sessionMsg);
    }

    /**
     * Route a message to the correct destination:
     * - Remote agent → route directly to remote pod via agent.routeMessage()
     * - Local agent → enqueue locally (or route to another pod if session is already active there)
     */
    public void enqueueOrRoute(String agentName, WChatSessionMessage sessionMsg) {
        WChatAgent agent = getAgent(agentName).orElse(null);
        if (agent != null && !agent.isLocal()) {
            log.info("Routing to remote agent: agentName={}, chatId={}", agentName, sessionMsg.getChatId());
            agent.routeMessage(sessionMsg);
            return;
        }

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

        // Build request context with full worldId (includes instance suffix, e.g. "ymir:Mist::x0")
        String fullWorldIdStr = msg.getFullWorldId() != null && !msg.getFullWorldId().isBlank()
                ? msg.getFullWorldId() : msg.getWorldId();
        WChatContext context = WChatContext.builder()
                .fullWorldId(WorldId.unchecked(fullWorldIdStr))
                .sessionId(msg.getSessionId())
                .build();

        WChatAgent agent = getAgent(msg.getAgentName())
                .orElse(null);
        if (agent == null) {
            log.error("Agent not found for async chat: agentName={}", msg.getAgentName());
            return;
        }

        try {
            List<WChatMessage> responses;
            if (agent.supportsQueue() && sessionQueue != null) {
                responses = agent.chatWithQueue(worldId, msg.getChatId(), msg.getPlayerId(),
                        msg.getMessage(), msg.getSessionId(), sessionQueue, context);
            } else if (msg.getSessionId() != null && !msg.getSessionId().isBlank()) {
                responses = agent.chatWithSession(worldId, msg.getChatId(), msg.getPlayerId(),
                        msg.getMessage(), msg.getSessionId(), context);
            } else {
                responses = agent.chat(worldId, msg.getChatId(), msg.getPlayerId(),
                        msg.getMessage(), context);
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
