package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Business logic service for WAnythingEntity.
 * Manages storage and retrieval of arbitrary data objects scoped by worldId and collection.
 * <p>
 * worldId is always required. Region scoping is done via worldId with the format "@region:regionId".
 * Use {@code WorldId.of(WorldId.COLLECTION_REGION, regionId)} to create a region-scoped worldId.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WAnythingService {

    private final WAnythingRepository repository;
    private final MongoTemplate mongoTemplate;

    @Transactional(readOnly = true)
    public Optional<WAnything> findById(String id) {
        return repository.findById(id);
    }

    /**
     * Find single entity by worldId, collection, and name.
     * If multiple entities exist (data inconsistency), returns the newest one and logs a warning.
     */
    @Transactional(readOnly = true)
    public Optional<WAnything> findByWorldIdAndCollectionAndName(String worldId, String collection, String name) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        var result = repository.findByWorldIdAndCollectionAndName(world.getId(), collection, name);
        if (result.isPresent()) {
            return result;
        }

        // Fallback: search all matching entities and return newest
        var all = repository.findByWorldIdAndCollection(world.getId(), collection).stream()
                .filter(e -> name.equals(e.getName()))
                .toList();

        if (all.isEmpty()) {
            return Optional.empty();
        }

        if (all.size() > 1) {
            log.warn("Multiple entities found for worldId={}, collection={}, name={} - returning newest (count: {})",
                    world, collection, name, all.size());
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
     * Find all entities by worldId and collection.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByWorldIdAndCollection(String worldId, String collection) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        return repository.findByWorldIdAndCollection(world.getId(), collection);
    }

    /**
     * Find all enabled entities by worldId and collection.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByWorldIdAndCollectionAndEnabled(String worldId, String collection, boolean enabled) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        return repository.findByWorldIdAndCollectionAndEnabled(world.getId(), collection, enabled);
    }

    /**
     * Find all entities by worldId, collection, and type.
     */
    @Transactional(readOnly = true)
    public List<WAnything> findByWorldIdAndCollectionAndType(String worldId, String collection, String type) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        return repository.findByWorldIdAndCollectionAndType(world.getId(), collection, type);
    }

    /**
     * Create a new entity scoped by worldId.
     */
    @Transactional
    public WAnything create(String worldId, String collection, String name, String title, String description, String type, Object data) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        if (world.isInstance()) {
            throw new IllegalArgumentException("worldId must not contain instance part: " + worldId);
        }

        if (repository.existsByWorldIdAndCollectionAndName(world.getId(), collection, name)) {
            throw new IllegalStateException("Entity already exists: worldId=" + worldId +
                    ", collection=" + collection + ", name=" + name);
        }

        WAnything entity = WAnything.builder()
                .worldId(world.getId())
                .collection(collection)
                .name(name)
                .title(title)
                .description(description)
                .type(type)
                .data(data)
                .build();
        entity.touchCreate();
        entity.removeWorldPrefix();

        repository.save(entity);
        log.debug("WAnythingEntity created: worldId={}, collection={}, name={}, title={}, type={}",
                worldId, collection, name, title, type);
        return entity;
    }

    /**
     * Update an existing entity by ID.
     */
    @Transactional
    public Optional<WAnything> update(String id, java.util.function.Consumer<WAnything> updater) {
        return repository.findById(id).map(existing -> {
            updater.accept(existing);
            existing.touchUpdate();
            existing.removeWorldPrefix();
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
        entity.removeWorldPrefix();
        WAnything saved = repository.save(entity);
        log.debug("WAnythingEntity saved: id={}, collection={}, name={}",
                saved.getId(), saved.getCollection(), saved.getName());
        return saved;
    }

    /**
     * Delete entity by worldId, collection, and name.
     */
    @Transactional
    public void deleteByWorldIdAndCollectionAndName(String worldId, String collection, String name) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        if (world.isInstance()) {
            throw new IllegalArgumentException("worldId must not contain instance part: " + worldId);
        }
        repository.deleteByWorldIdAndCollectionAndName(world.getId(), collection, name);
        log.debug("WAnythingEntity deleted: worldId={}, collection={}, name={}", worldId, collection, name);
    }

    /**
     * Check if entity exists by worldId, collection, and name.
     */
    @Transactional(readOnly = true)
    public boolean exists(String worldId, String collection, String name) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        return repository.existsByWorldIdAndCollectionAndName(world.getId(), collection, name);
    }

    /**
     * Get distinct collection names filtered by worldId.
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctCollections(String worldId) {
        WorldId world = WorldId.of(worldId).orElseThrow().toBaseWorldId();
        var query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("worldId").is(world.getId()));

        List<String> collections = mongoTemplate.findDistinct(query, "collection", WAnything.class, String.class);
        log.debug("Found {} distinct collections (worldId={})", collections.size(), worldId);

        return collections.stream()
                .sorted()
                .toList();
    }

    /**
     * Delete ALL WAnything entities of a world. Owner-level bulk operation so
     * callers do not access the WAnything collection directly (data ownership).
     * <p>
     * Matches the raw worldId exactly (no base-world normalization) to preserve
     * the previous caller semantics.
     *
     * @return number of deleted entities
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WAnything.class
        );
        long deleted = result.getDeletedCount();
        log.info("Deleted {} anythings for world {}", deleted, worldId);
        return (int) deleted;
    }

    /**
     * Distinct world IDs that have WAnything entities (owner-level; avoids
     * callers querying the WAnything collection directly).
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WAnything.class, String.class);
    }

    /**
     * Duplicate all WAnything entities from a source world to a target world.
     * <p>
     * Uses raw MongoDB Documents to preserve the dynamic {@code data} field
     * structure exactly. New copies get a fresh id and updated timestamps.
     *
     * @return number of duplicated entities
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        String collectionName = mongoTemplate.getCollectionName(WAnything.class);

        Query query = new Query(Criteria.where("worldId").is(sourceWorldId));
        List<Document> sourceDocuments = mongoTemplate.find(query, Document.class, collectionName);
        log.info("Found {} anythings in source world {}", sourceDocuments.size(), sourceWorldId);

        int duplicatedCount = 0;
        Instant now = Instant.now();

        for (Document source : sourceDocuments) {
            Document target = new Document(source);
            target.remove("_id");
            target.put("worldId", targetWorldId);
            target.put("createdAt", now);
            target.put("updatedAt", now);

            mongoTemplate.save(target, collectionName);
            duplicatedCount++;
        }

        log.info("Duplicated {} anythings from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
        return duplicatedCount;
    }
}
