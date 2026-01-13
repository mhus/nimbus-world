package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic service for WAnythingEntity.
 * Manages storage and retrieval of arbitrary data objects with flexible scoping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WAnythingService {

    private final WAnythingRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Find entity by collection and name only.
     */
    @Transactional(readOnly = true)
    public Optional<WAnything> findByCollectionAndName(String collection, String name) {
        return repository.findByCollectionAndName(collection, name);
    }

    /**
     * Find single entity scoped by world, collection, and name.
     * If multiple entities exist (data inconsistency), returns the newest one and logs a warning.
     */
    @Transactional(readOnly = true)
    public Optional<WAnything> findByWorldIdAndCollectionAndName(String worldId, String collection, String name) {
        var result = repository.findByWorldIdAndCollectionAndName(worldId, collection, name);
        if (result.isPresent()) {
            return result;
        }

        // Fallback: search all matching entities and return newest
        var all = repository.findByWorldIdAndCollection(worldId, collection).stream()
                .filter(e -> name.equals(e.getName()))
                .toList();

        if (all.isEmpty()) {
            return Optional.empty();
        }

        if (all.size() > 1) {
            log.warn("Multiple entities found for worldId={}, collection={}, name={} - returning newest (count: {})",
                    worldId, collection, name, all.size());
        }

        return all.stream()
                .max((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return -1;
                    if (b.getUpdatedAt() == null) return 1;
                    return a.getUpdatedAt().compareTo(b.getUpdatedAt());
                });
    }

    /**
     * Find single entity scoped by region, collection, and name.
     * If multiple entities exist (data inconsistency), returns the newest one and logs a warning.
     */
    @Transactional(readOnly = true)
    public Optional<WAnything> findByRegionIdAndCollectionAndName(String regionId, String collection, String name) {
        var result = repository.findByRegionIdAndCollectionAndName(regionId, collection, name);
        if (result.isPresent()) {
            return result;
        }

        // Fallback: search all matching entities and return newest
        var all = repository.findByRegionIdAndCollection(regionId, collection).stream()
                .filter(e -> name.equals(e.getName()))
                .toList();

        if (all.isEmpty()) {
            return Optional.empty();
        }

        if (all.size() > 1) {
            log.warn("Multiple entities found for regionId={}, collection={}, name={} - returning newest (count: {})",
                    regionId, collection, name, all.size());
        }

        return all.stream()
                .max((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return -1;
                    if (b.getUpdatedAt() == null) return 1;
                    return a.getUpdatedAt().compareTo(b.getUpdatedAt());
                });
    }

    /**
     * Find single entity scoped by region, world, collection, and name.
     * If multiple entities exist (data inconsistency), returns the newest one and logs a warning.
     */
    @Transactional(readOnly = true)
    public Optional<WAnything> findByRegionIdAndWorldIdAndCollectionAndName(
            String regionId, String worldId, String collection, String name) {
        var result = repository.findByRegionIdAndWorldIdAndCollectionAndName(regionId, worldId, collection, name);
        if (result.isPresent()) {
            return result;
        }

        // Fallback: search all matching entities and return newest
        var all = repository.findByRegionIdAndWorldIdAndCollection(regionId, worldId, collection).stream()
                .filter(e -> name.equals(e.getName()))
                .toList();

        if (all.isEmpty()) {
            return Optional.empty();
        }

        if (all.size() > 1) {
            log.warn("Multiple entities found for regionId={}, worldId={}, collection={}, name={} - returning newest (count: {})",
                    regionId, worldId, collection, name, all.size());
        }

        return all.stream()
                .max((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return -1;
                    if (b.getUpdatedAt() == null) return 1;
                    return a.getUpdatedAt().compareTo(b.getUpdatedAt());
                });
    }

    /**
     * Find all entities in a collection.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByCollection(String collection) {
        return repository.findByCollection(collection);
    }

    /**
     * Find all entities in a collection scoped by world.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByWorldIdAndCollection(String worldId, String collection) {
        return repository.findByWorldIdAndCollection(worldId, collection);
    }

    /**
     * Find all entities in a collection scoped by region.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByRegionIdAndCollection(String regionId, String collection) {
        return repository.findByRegionIdAndCollection(regionId, collection);
    }

    /**
     * Find all entities in a collection scoped by region and world.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByRegionIdAndWorldIdAndCollection(
            String regionId, String worldId, String collection) {
        return repository.findByRegionIdAndWorldIdAndCollection(regionId, worldId, collection);
    }

    /**
     * Find all enabled entities in a collection.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByCollectionAndEnabled(String collection, boolean enabled) {
        return repository.findByCollectionAndEnabled(collection, enabled);
    }

    /**
     * Find all enabled entities in a collection scoped by world.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByWorldIdAndCollectionAndEnabled(String worldId, String collection, boolean enabled) {
        return repository.findByWorldIdAndCollectionAndEnabled(worldId, collection, enabled);
    }

    /**
     * Find all enabled entities in a collection scoped by region.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByRegionIdAndCollectionAndEnabled(String regionId, String collection, boolean enabled) {
        return repository.findByRegionIdAndCollectionAndEnabled(regionId, collection, enabled);
    }

    /**
     * Find all enabled entities in a collection scoped by region and world.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByRegionIdAndWorldIdAndCollectionAndEnabled(
            String regionId, String worldId, String collection, boolean enabled) {
        return repository.findByRegionIdAndWorldIdAndCollectionAndEnabled(regionId, worldId, collection, enabled);
    }

    /**
     * Find all entities by collection and type.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByCollectionAndType(String collection, String type) {
        return repository.findByCollectionAndType(collection, type);
    }

    /**
     * Find all entities by world, collection, and type.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByWorldIdAndCollectionAndType(String worldId, String collection, String type) {
        return repository.findByWorldIdAndCollectionAndType(worldId, collection, type);
    }

    /**
     * Find all entities by region, collection, and type.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByRegionIdAndCollectionAndType(String regionId, String collection, String type) {
        return repository.findByRegionIdAndCollectionAndType(regionId, collection, type);
    }

    /**
     * Find all entities by region, world, collection, and type.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByRegionIdAndWorldIdAndCollectionAndType(
            String regionId, String worldId, String collection, String type) {
        return repository.findByRegionIdAndWorldIdAndCollectionAndType(regionId, worldId, collection, type);
    }

    /**
     * Create a new entity with minimal scoping (collection and name only).
     */
    @Transactional
    public WAnything create(String collection, String name, String title, String description, String type, Object data) {
        if (repository.existsByCollectionAndName(collection, name)) {
            throw new IllegalStateException("Entity already exists: collection=" + collection + ", name=" + name);
        }
        return saveNew(null, null, collection, name, title, description, type, data);
    }

    /**
     * Create a new entity scoped by world.
     */
    @Transactional
    public WAnything createWithWorldId(String worldId, String collection, String name, String title, String description, String type, Object data) {
        if (repository.existsByWorldIdAndCollectionAndName(worldId, collection, name)) {
            throw new IllegalStateException("Entity already exists: worldId=" + worldId +
                    ", collection=" + collection + ", name=" + name);
        }
        return saveNew(null, worldId, collection, name, title, description, type, data);
    }

    /**
     * Create a new entity scoped by region.
     */
    @Transactional
    public WAnything createWithRegionId(String regionId, String collection, String name, String title, String description, String type, Object data) {
        if (repository.existsByRegionIdAndCollectionAndName(regionId, collection, name)) {
            throw new IllegalStateException("Entity already exists: regionId=" + regionId +
                    ", collection=" + collection + ", name=" + name);
        }
        return saveNew(regionId, null, collection, name, title, description, type, data);
    }

    /**
     * Create a new entity scoped by region and world.
     */
    @Transactional
    public WAnything createWithRegionIdAndWorldId(
            String regionId, String worldId, String collection, String name, String title, String description, String type, Object data) {
        if (repository.existsByRegionIdAndWorldIdAndCollectionAndName(regionId, worldId, collection, name)) {
            throw new IllegalStateException("Entity already exists: regionId=" + regionId +
                    ", worldId=" + worldId + ", collection=" + collection + ", name=" + name);
        }
        return saveNew(regionId, worldId, collection, name, title, description, type, data);
    }

    /**
     * Update an existing entity by ID.
     */
    @Transactional
    public Optional<WAnything> update(String id, java.util.function.Consumer<WAnything> updater) {
        return repository.findById(id).map(existing -> {
            updater.accept(existing);
            existing.touchUpdate();
            repository.save(existing);
            log.debug("WAnythingEntity updated: id={}, collection={}, name={}",
                    id, existing.getCollection(), existing.getName());
            return existing;
        });
    }

    /**
     * Save an entity (update timestamp and persist).
     */
    @Transactional
    public WAnything save(WAnything entity) {
        entity.touchUpdate();
        WAnything saved = repository.save(entity);
        log.debug("WAnythingEntity saved: id={}, collection={}, name={}",
                saved.getId(), saved.getCollection(), saved.getName());
        return saved;
    }

    /**
     * Delete entity by collection and name.
     */
    @Transactional
    public void deleteByCollectionAndName(String collection, String name) {
        repository.deleteByCollectionAndName(collection, name);
        log.debug("WAnythingEntity deleted: collection={}, name={}", collection, name);
    }

    /**
     * Delete entity scoped by world, collection, and name.
     */
    @Transactional
    public void deleteByWorldIdAndCollectionAndName(String worldId, String collection, String name) {
        repository.deleteByWorldIdAndCollectionAndName(worldId, collection, name);
        log.debug("WAnythingEntity deleted: worldId={}, collection={}, name={}", worldId, collection, name);
    }

    /**
     * Delete entity scoped by region, collection, and name.
     */
    @Transactional
    public void deleteByRegionIdAndCollectionAndName(String regionId, String collection, String name) {
        repository.deleteByRegionIdAndCollectionAndName(regionId, collection, name);
        log.debug("WAnythingEntity deleted: regionId={}, collection={}, name={}", regionId, collection, name);
    }

    /**
     * Delete entity scoped by region, world, collection, and name.
     */
    @Transactional
    public void deleteByRegionIdAndWorldIdAndCollectionAndName(
            String regionId, String worldId, String collection, String name) {
        repository.deleteByRegionIdAndWorldIdAndCollectionAndName(regionId, worldId, collection, name);
        log.debug("WAnythingEntity deleted: regionId={}, worldId={}, collection={}, name={}",
                regionId, worldId, collection, name);
    }

    /**
     * Check if entity exists.
     */
    @Transactional(readOnly = true)
    public boolean exists(String collection, String name) {
        return repository.existsByCollectionAndName(collection, name);
    }

    /**
     * Check if entity exists with world scope.
     */
    @Transactional(readOnly = true)
    public boolean existsWithWorldId(String worldId, String collection, String name) {
        return repository.existsByWorldIdAndCollectionAndName(worldId, collection, name);
    }

    /**
     * Check if entity exists with region scope.
     */
    @Transactional(readOnly = true)
    public boolean existsWithRegionId(String regionId, String collection, String name) {
        return repository.existsByRegionIdAndCollectionAndName(regionId, collection, name);
    }

    /**
     * Check if entity exists with region and world scope.
     */
    @Transactional(readOnly = true)
    public boolean existsWithRegionIdAndWorldId(String regionId, String worldId, String collection, String name) {
        return repository.existsByRegionIdAndWorldIdAndCollectionAndName(regionId, worldId, collection, name);
    }

    /**
     * Internal helper to create and save a new entity.
     */
    private WAnything saveNew(String regionId, String worldId, String collection, String name, String title, String description, String type, Object data) {
        WAnything entity = WAnything.builder()
                .regionId(regionId)
                .worldId(worldId)
                .collection(collection)
                .name(name)
                .title(title)
                .description(description)
                .type(type)
                .data(data)
                .build();
        entity.touchCreate();
        repository.save(entity);
        log.debug("WAnythingEntity created: regionId={}, worldId={}, collection={}, name={}, title={}, type={}",
                regionId, worldId, collection, name, title, type);
        return entity;
    }

    /**
     * Get distinct collection names, optionally filtered by regionId and/or worldId.
     * Uses MongoDB distinct query for efficient retrieval.
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctCollections(String regionId, String worldId) {
        var query = new org.springframework.data.mongodb.core.query.Query();

        if (regionId != null && !regionId.isBlank()) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("regionId").is(regionId));
        }

        if (worldId != null && !worldId.isBlank()) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("worldId").is(worldId));
        }

        List<String> collections = mongoTemplate.findDistinct(query, "collection", WAnything.class, String.class);
        log.debug("Found {} distinct collections (regionId={}, worldId={})", collections.size(), regionId, worldId);

        return collections.stream()
                .sorted()
                .toList();
    }
}
