package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic service for WWorldCollection.
 * Manages world collections which group related worlds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WWorldCollectionService {

    private final WWorldCollectionRepository repository;

    /**
     * Find all world collections.
     *
     * @return List of all world collections
     */
    @Transactional(readOnly = true)
    public List<WWorldCollection> findAll() {
        return repository.findAll();
    }

    /**
     * Find all enabled world collections.
     *
     * @return List of enabled world collections
     */
    @Transactional(readOnly = true)
    public List<WWorldCollection> findAllEnabled() {
        return repository.findByEnabled(true);
    }

    /**
     * Find a world collection by worldId.
     *
     * @param worldId The worldId (must start with '@')
     * @return Optional containing the collection if found
     */
    @Transactional(readOnly = true)
    public Optional<WWorldCollection> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

    /**
     * Find a world collection by WorldId object.
     *
     * @param worldId The WorldId object
     * @return Optional containing the collection if found
     */
    @Transactional(readOnly = true)
    public Optional<WWorldCollection> findByWorldId(WorldId worldId) {
        return repository.findByWorldId(worldId.getId());
    }

    /**
     * Check if a world collection exists by worldId.
     *
     * @param worldId The worldId (must start with '@')
     * @return true if exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsByWorldId(String worldId) {
        return repository.existsByWorldId(worldId);
    }

    /**
     * Create a new world collection.
     *
     * @param worldId The worldId (must start with '@')
     * @param title The title
     * @param description The description
     * @return The created collection
     */
    @Transactional
    public WWorldCollection create(String worldId, String title, String description) {
        if (!worldId.startsWith("@")) {
            throw new IllegalArgumentException("Collection worldId must start with '@': " + worldId);
        }

        if (repository.existsByWorldId(worldId)) {
            throw new IllegalStateException("Collection already exists: " + worldId);
        }

        WWorldCollection collection = WWorldCollection.builder()
                .worldId(worldId)
                .title(title)
                .description(description)
                .enabled(true)
                .build();
        collection.touchCreate();
        repository.save(collection);

        log.debug("World collection created: worldId={}, title={}", worldId, title);
        return collection;
    }

    /**
     * Update an existing world collection.
     *
     * @param worldId The worldId
     * @param updater Consumer to update the collection
     * @return Optional containing the updated collection
     */
    @Transactional
    public Optional<WWorldCollection> update(String worldId, java.util.function.Consumer<WWorldCollection> updater) {
        return repository.findByWorldId(worldId).map(existing -> {
            updater.accept(existing);
            existing.touchUpdate();
            repository.save(existing);
            log.debug("World collection updated: worldId={}", worldId);
            return existing;
        });
    }

    /**
     * Save a world collection (update timestamp and persist).
     *
     * @param collection The collection to save
     * @return The saved collection
     */
    @Transactional
    public WWorldCollection save(WWorldCollection collection) {
        collection.touchUpdate();
        WWorldCollection saved = repository.save(collection);
        log.debug("World collection saved: worldId={}", collection.getWorldId());
        return saved;
    }

    /**
     * Delete a world collection by worldId.
     *
     * @param worldId The worldId
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean delete(String worldId) {
        if (!repository.existsByWorldId(worldId)) {
            return false;
        }
        repository.deleteByWorldId(worldId);
        log.debug("World collection deleted: worldId={}", worldId);
        return true;
    }

    /**
     * Delete a world collection by WorldId object.
     *
     * @param worldId The WorldId object
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean delete(WorldId worldId) {
        return delete(worldId.getId());
    }
}
