package de.mhus.nimbus.world.shared.world;

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
     * Create or update a progress entry.
     * If a matching entry (worldId + playerId + type + quest) exists, it is updated.
     * Otherwise a new entry is created.
     */
    @Transactional
    public WProgress save(String worldId, String playerId, String type, String quest, Map<String, Object> progressData) {
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
     * @param progressId MongoDB document id
     * @param key        the key to set
     * @param value      the value to set
     * @return true if the update was applied
     */
    public boolean setProgressDataValue(String progressId, String key, Object value) {
        Query query = new Query(Criteria.where("id").is(progressId));
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
     * @param progressId MongoDB document id
     * @param values     key-value pairs to set
     * @return true if the update was applied
     */
    public boolean setProgressDataValues(String progressId, Map<String, Object> values) {
        Query query = new Query(Criteria.where("id").is(progressId));
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
     * @param progressId MongoDB document id
     * @param key        the key to remove
     * @return true if the update was applied
     */
    public boolean removeProgressDataValue(String progressId, String key) {
        Query query = new Query(Criteria.where("id").is(progressId)
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
     * @param progressId MongoDB document id
     * @param key        the key to increment
     * @param delta      amount to add (can be negative)
     * @return true if the update was applied
     */
    public boolean incProgressDataValue(String progressId, String key, int delta) {
        Query query = new Query(Criteria.where("id").is(progressId));
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
        Query query = new Query(Criteria.where("id").is(progressId));
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
}
