package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.world.shared.redis.BlockStatusPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing WProgress entities.
 * Provides business logic for player progress tracking including quests, achievements, and skills.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WProgressService {

    private final WProgressRepository repository;
    private final MongoTemplate mongoTemplate;
    private final BlockStatusPublisher blockStatusPublisher;

    /**
     * Find all progress entries for a world.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

    /**
     * Find a specific progress entry by ID.
     */
    @Transactional(readOnly = true)
    public Optional<WProgress> findById(String id) {
        return repository.findById(id);
    }

    /**
     * Find a progress entry by its progressId.
     */
    @Transactional(readOnly = true)
    public Optional<WProgress> findByProgressId(String progressId) {
        return repository.findByProgressId(progressId);
    }

    /**
     * Find all progress entries for a player in a world.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldIdAndPlayerId(String worldId, String playerId) {
        return repository.findByWorldIdAndPlayerId(worldId, playerId);
    }

    /**
     * Find all progress entries for a player in a world with a specific type.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldIdAndPlayerIdAndType(String worldId, String playerId, String type) {
        return repository.findByWorldIdAndPlayerIdAndType(worldId, playerId, type);
    }

    /**
     * Find all progress entries for a player in a world for a specific quest.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldIdAndPlayerIdAndQuest(String worldId, String playerId, String quest) {
        return repository.findByWorldIdAndPlayerIdAndQuest(worldId, playerId, quest);
    }

    /**
     * Find a specific progress entry by world, player, type, and quest.
     */
    @Transactional(readOnly = true)
    public Optional<WProgress> findByWorldIdAndPlayerIdAndTypeAndQuest(String worldId, String playerId, String type, String quest) {
        return repository.findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, playerId, type, quest);
    }

    /**
     * Find block-status progress entries for a world and a list of chunk keys.
     * Returns a map: chunkKey -> progressData (block coordinates -> status).
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> findBlockStatusForChunks(String worldId, List<String> chunkKeys) {
        List<WProgress> entries = repository.findByWorldIdAndTypeAndQuestIn(worldId, "block-status", chunkKeys);
        Map<String, Map<String, Object>> result = new java.util.HashMap<>();
        for (WProgress entry : entries) {
            if (entry.getProgressData() != null && !entry.getProgressData().isEmpty()) {
                result.put(entry.getQuest(), entry.getProgressData());
            }
        }
        return result;
    }

    /**
     * Create or update a progress entry.
     * If a matching entry (worldId + playerId + type + quest) exists, it is updated.
     * Otherwise a new entry is created.
     */
    @Transactional
    public WProgress save(String worldId, String playerId, String type, String quest, Map<String, Object> progressData) {
        return save(worldId, playerId, type, quest, null, progressData);
    }

    /**
     * Create or update a progress entry with title.
     * If a matching entry (worldId + playerId + type + quest) exists, it is updated.
     * Otherwise a new entry is created.
     */
    @Transactional
    public WProgress save(String worldId, String playerId, String type, String quest, String title, Map<String, Object> progressData) {
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId is required");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }

        Optional<WProgress> existing = repository.findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, playerId, type, quest);
        if (existing.isPresent()) {
            WProgress progress = existing.get();
            if (title != null) {
                progress.setTitle(title);
            }
            progress.setProgressData(progressData);
            progress.touchUpdate();
            log.debug("Updated progress: worldId={}, playerId={}, type={}, quest={}", worldId, playerId, type, quest);
            return repository.save(progress);
        }

        WProgress progress = WProgress.builder()
                .worldId(worldId)
                .playerId(playerId)
                .type(type)
                .quest(quest)
                .title(title)
                .progressData(progressData)
                .build();
        progress.touchCreate();

        log.debug("Created progress: worldId={}, playerId={}, type={}, quest={}", worldId, playerId, type, quest);
        return repository.save(progress);
    }

    // --- Atomic MongoTemplate operations on progressData ---

    /**
     * Atomically set a single key in progressData.
     *
     * @param progressId WProgress.progressId (UUID)
     * @param key        the key to set
     * @param value      the value to set
     * @return true if the update was applied
     */
    public boolean setProgressDataValue(String progressId, String key, Object value) {
        Query query = new Query(Criteria.where("progressId").is(progressId));
        Update update = new Update()
                .set("progressData." + key, value)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WProgress.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("setProgressDataValue failed: progressId={}, key={}", progressId, key);
        return false;
    }

    /**
     * Atomically set multiple keys in progressData.
     * Existing keys not in the map are preserved.
     *
     * @param progressId WProgress.progressId (UUID)
     * @param values     key-value pairs to set
     * @return true if the update was applied
     */
    public boolean setProgressDataValues(String progressId, Map<String, Object> values) {
        Query query = new Query(Criteria.where("progressId").is(progressId));
        Update update = new Update()
                .set("updatedAt", Instant.now());

        for (var entry : values.entrySet()) {
            update.set("progressData." + entry.getKey(), entry.getValue());
        }

        var result = mongoTemplate.updateFirst(query, update, WProgress.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("setProgressDataValues failed: progressId={}", progressId);
        return false;
    }

    /**
     * Atomically remove a key from progressData.
     *
     * @param progressId WProgress.progressId (UUID)
     * @param key        the key to remove
     * @return true if the update was applied
     */
    public boolean removeProgressDataValue(String progressId, String key) {
        Query query = new Query(Criteria.where("progressId").is(progressId)
                .and("progressData." + key).exists(true));
        Update update = new Update()
                .unset("progressData." + key)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WProgress.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("removeProgressDataValue failed: progressId={}, key={}", progressId, key);
        return false;
    }

    /**
     * Atomically increment a numeric value in progressData.
     *
     * @param progressId WProgress.progressId (UUID)
     * @param key        the key to increment
     * @param delta      amount to add (can be negative)
     * @return true if the update was applied
     */
    public boolean incProgressDataValue(String progressId, String key, int delta) {
        Query query = new Query(Criteria.where("progressId").is(progressId));
        Update update = new Update()
                .inc("progressData." + key, delta)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WProgress.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("incProgressDataValue failed: progressId={}, key={}, delta={}", progressId, key, delta);
        return false;
    }

    /**
     * Atomically replace the entire progressData map.
     *
     * @param progressId   MongoDB document id
     * @param progressData the new progressData
     * @return true if the update was applied
     */
    public boolean replaceProgressData(String progressId, Map<String, Object> progressData) {
        Query query = new Query(Criteria.where("progressId").is(progressId));
        Update update = new Update()
                .set("progressData", progressData)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WProgress.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("replaceProgressData failed: progressId={}", progressId);
        return false;
    }

    // --- Block status operations (type="block-status", quest=chunkKey) ---

    private static final String BLOCK_STATUS_TYPE = "block-status";
    private static final String BLOCK_STATUS_PLAYER = "world";

    /**
     * Atomically set a block status entry for a chunk.
     * Creates the WProgress document if it doesn't exist (upsert).
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (e.g. "1:2"), stored as quest
     * @param blockKey Block identifier (block id or world coordinates "x,y,z")
     * @param status   Status value (e.g. "open", "closed")
     */
    public void setBlockStatus(String worldId, String chunkKey, String blockKey, String status) {
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(BLOCK_STATUS_PLAYER)
                .and("type").is(BLOCK_STATUS_TYPE)
                .and("quest").is(chunkKey));
        Update update = new Update()
                .set("progressData." + blockKey, status)
                .set("updatedAt", Instant.now())
                .setOnInsert("worldId", worldId)
                .setOnInsert("playerId", BLOCK_STATUS_PLAYER)
                .setOnInsert("type", BLOCK_STATUS_TYPE)
                .setOnInsert("quest", chunkKey)
                .setOnInsert("progressId", java.util.UUID.randomUUID().toString())
                .setOnInsert("createdAt", Instant.now());

        mongoTemplate.upsert(query, update, WProgress.class);
        blockStatusPublisher.publishStatusChange(worldId, chunkKey, blockKey, status);
        log.debug("Set block status: worldId={}, chunk={}, block={}, status={}", worldId, chunkKey, blockKey, status);
    }

    /**
     * Criteria selecting the block status document of one chunk.
     */
    private Criteria blockStatusChunkCriteria(String worldId, String chunkKey) {
        return Criteria.where("worldId").is(worldId)
                .and("playerId").is(BLOCK_STATUS_PLAYER)
                .and("type").is(BLOCK_STATUS_TYPE)
                .and("quest").is(chunkKey);
    }

    /**
     * Atomically claim a block status: the status is only set if the block does not carry it yet.
     *
     * Used for exclusive interactions like collecting, where two players must not both succeed
     * on the same block. Publishes the change like {@link #setBlockStatus} when the claim wins.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (e.g. "1:2"), stored as quest
     * @param blockKey Block identifier (block id or world coordinates "x,y,z")
     * @param status   Status value to claim (e.g. "empty")
     * @return true if this caller set the status, false if the block already carried it
     */
    public boolean claimBlockStatus(String worldId, String chunkKey, String blockKey, String status) {
        // Ensure the chunk document exists, so the claim below never inserts a second one
        Update createUpdate = new Update()
                .setOnInsert("worldId", worldId)
                .setOnInsert("playerId", BLOCK_STATUS_PLAYER)
                .setOnInsert("type", BLOCK_STATUS_TYPE)
                .setOnInsert("quest", chunkKey)
                .setOnInsert("progressId", java.util.UUID.randomUUID().toString())
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert("updatedAt", Instant.now());
        mongoTemplate.upsert(new Query(blockStatusChunkCriteria(worldId, chunkKey)), createUpdate, WProgress.class);

        // "ne" also matches a missing key, so the first claimer wins and later ones fail
        Query claimQuery = new Query(blockStatusChunkCriteria(worldId, chunkKey)
                .and("progressData." + blockKey).ne(status));
        Update claimUpdate = new Update()
                .set("progressData." + blockKey, status)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(claimQuery, claimUpdate, WProgress.class);
        if (result.getModifiedCount() == 0) {
            log.debug("Block status already claimed: worldId={}, chunk={}, block={}, status={}",
                    worldId, chunkKey, blockKey, status);
            return false;
        }

        blockStatusPublisher.publishStatusChange(worldId, chunkKey, blockKey, status);
        log.debug("Claimed block status: worldId={}, chunk={}, block={}, status={}", worldId, chunkKey, blockKey, status);
        return true;
    }

    /**
     * Atomically remove a block status entry from a chunk.
     * Does nothing if the WProgress document or key doesn't exist.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (e.g. "1:2"), stored as quest
     * @param blockKey Block identifier (block id or world coordinates "x,y,z")
     */
    public void removeBlockStatus(String worldId, String chunkKey, String blockKey) {
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(BLOCK_STATUS_PLAYER)
                .and("type").is(BLOCK_STATUS_TYPE)
                .and("quest").is(chunkKey)
                .and("progressData." + blockKey).exists(true));
        Update update = new Update()
                .unset("progressData." + blockKey)
                .set("updatedAt", Instant.now());

        mongoTemplate.updateFirst(query, update, WProgress.class);
        blockStatusPublisher.publishStatusChange(worldId, chunkKey, blockKey, null);
        log.debug("Removed block status: worldId={}, chunk={}, block={}", worldId, chunkKey, blockKey);
    }

    // --- Block cooldown operations (type="block-cooldown", quest=chunkKey) ---

    private static final String BLOCK_COOLDOWN_TYPE = "block-cooldown";

    /**
     * Set or refresh the cooldown expiry of a single block.
     * Creates the WProgress document if it doesn't exist (upsert).
     *
     * The entry is a pending marker for world-life: once expired, world-life resets the
     * block status and removes the entry. It does not change the block status by itself.
     *
     * @param worldId   World identifier
     * @param chunkKey  Chunk key (e.g. "1:2"), stored as quest
     * @param blockKey  Block identifier (block id or world coordinates "x,y,z")
     * @param expiresAt Epoch millis after which the cooldown is over
     */
    public void setBlockCooldown(String worldId, String chunkKey, String blockKey, long expiresAt) {
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(BLOCK_STATUS_PLAYER)
                .and("type").is(BLOCK_COOLDOWN_TYPE)
                .and("quest").is(chunkKey));
        Update update = new Update()
                .set("progressData." + blockKey, expiresAt)
                .set("updatedAt", Instant.now())
                .setOnInsert("worldId", worldId)
                .setOnInsert("playerId", BLOCK_STATUS_PLAYER)
                .setOnInsert("type", BLOCK_COOLDOWN_TYPE)
                .setOnInsert("quest", chunkKey)
                .setOnInsert("progressId", java.util.UUID.randomUUID().toString())
                .setOnInsert("createdAt", Instant.now());

        mongoTemplate.upsert(query, update, WProgress.class);
        log.debug("Set block cooldown: worldId={}, chunk={}, block={}, expiresAt={}", worldId, chunkKey, blockKey, expiresAt);
    }

    /**
     * Find all pending block cooldowns of a world.
     * Returns a map: chunkKey -> (blockKey -> expiresAt epoch millis).
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> findBlockCooldowns(String worldId) {
        List<WProgress> entries = repository.findByWorldIdAndPlayerIdAndType(worldId, BLOCK_STATUS_PLAYER, BLOCK_COOLDOWN_TYPE);
        Map<String, Map<String, Object>> result = new java.util.HashMap<>();
        for (WProgress entry : entries) {
            if (entry.getProgressData() != null && !entry.getProgressData().isEmpty()) {
                result.put(entry.getQuest(), entry.getProgressData());
            }
        }
        return result;
    }

    /**
     * Atomically claim an expired block cooldown entry by removing it.
     *
     * The removal only succeeds if the stored expiry still equals the expected value, so a
     * cooldown that was refreshed in the meantime is never dropped and only one pod wins the
     * claim when multiple world-life pods sweep the same world.
     *
     * @param worldId   World identifier
     * @param chunkKey  Chunk key (e.g. "1:2"), stored as quest
     * @param blockKey  Block identifier (block id or world coordinates "x,y,z")
     * @param expiresAt Expiry value the caller has read
     * @return true if this caller removed the entry and is responsible for resetting the block
     */
    public boolean claimExpiredBlockCooldown(String worldId, String chunkKey, String blockKey, Object expiresAt) {
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(BLOCK_STATUS_PLAYER)
                .and("type").is(BLOCK_COOLDOWN_TYPE)
                .and("quest").is(chunkKey)
                .and("progressData." + blockKey).is(expiresAt));
        Update update = new Update()
                .unset("progressData." + blockKey)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WProgress.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Delete block cooldown documents of a world that no longer hold any entry.
     *
     * @param worldId World identifier
     * @return number of deleted documents
     */
    public int deleteEmptyBlockCooldowns(String worldId) {
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("playerId").is(BLOCK_STATUS_PLAYER)
                .and("type").is(BLOCK_COOLDOWN_TYPE)
                .and("progressData").is(new org.bson.Document()));

        var result = mongoTemplate.remove(query, WProgress.class);
        return (int) result.getDeletedCount();
    }

    /**
     * Delete a specific progress entry.
     */
    @Transactional
    public boolean delete(String id) {
        return repository.findById(id).map(progress -> {
            repository.delete(progress);
            log.debug("Deleted progress: id={}", id);
            return true;
        }).orElse(false);
    }

    /**
     * Delete all progress for a player in a world.
     */
    @Transactional
    public void deleteByWorldIdAndPlayerId(String worldId, String playerId) {
        repository.deleteByWorldIdAndPlayerId(worldId, playerId);
        log.debug("Deleted all progress: worldId={}, playerId={}", worldId, playerId);
    }

    /**
     * Delete all progress for a world (used for instance cleanup).
     */
    @Transactional
    public void deleteByWorldId(String worldId) {
        repository.deleteByWorldId(worldId);
        log.info("Deleted all progress for worldId={}", worldId);
    }

    /**
     * Delete all progress entries for a world and return the number of removed documents.
     * Used by world-resource cleanup which needs the deleted count for reporting.
     *
     * @param worldId World identifier
     * @return number of deleted progress entries
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WProgress.class
        );
        long deleted = result.getDeletedCount();
        log.info("Deleted {} progress entries for worldId={}", deleted, worldId);
        return (int) deleted;
    }

    /**
     * Return all distinct worldIds that have at least one progress entry.
     * Used to enumerate worlds known to the progress subsystem.
     *
     * @return list of distinct worldIds
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WProgress.class, String.class);
    }
}
