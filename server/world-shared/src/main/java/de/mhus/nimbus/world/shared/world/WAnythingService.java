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
        WorldId parsed = WorldId.of(worldId).orElseThrow();
        if (parsed.isInstance()) {
            throw new IllegalArgumentException("worldId must not contain instance part: " + worldId);
        }
        WorldId world = parsed.toBaseWorldId();

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
        WorldId parsed = WorldId.of(worldId).orElseThrow();
        if (parsed.isInstance()) {
            throw new IllegalArgumentException("worldId must not contain instance part: " + worldId);
        }
        WorldId world = parsed.toBaseWorldId();
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

    /**
     * Repair duplicate WAnything entries (unique: worldId + collection + name).
     * Owner-level operation so callers do not access the WAnything collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WAnything.class, "anything", worldId,
                doc -> {
                    String collection = doc.getString("collection");
                    String docName = doc.getString("name");
                    if (docName == null) return null;
                    return doc.getString("worldId") + "|" + (collection != null ? collection : "") + "|" + docName;
                }
        );
    }

    // ==================== SYNC DOCUMENT FACADE ====================
    // Raw org.bson.Document access for the SYNC cluster (world-control). Keeps
    // data ownership with this service while preserving the raw-document
    // behavior sync requires: _schema/_class fields stay untouched and schema
    // migration is applied externally on the raw JSON. worldId is matched
    // exactly as stored (no base-world normalization).

    /**
     * Export all WAnything documents of a world as raw MongoDB Documents.
     */
    @Transactional(readOnly = true)
    public List<Document> exportDocuments(String worldId) {
        String collectionName = mongoTemplate.getCollectionName(WAnything.class);
        return mongoTemplate.find(new Query(Criteria.where("worldId").is(worldId)), Document.class, collectionName);
    }

    /**
     * Find a single WAnything document by its sync identity.
     * The sync keys WAnything on the stored {@code title} field, so the natural
     * key value is matched against {@code title} to preserve existing behavior.
     */
    @Transactional(readOnly = true)
    public Optional<Document> findDocumentByWorldIdAndCollectionAndName(String worldId, String collection, String name) {
        String collectionName = mongoTemplate.getCollectionName(WAnything.class);
        Query query = new Query(Criteria.where("worldId").is(worldId)
                .and("collection").is(collection)
                .and("title").is(name));
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, collectionName));
    }

    /**
     * Upsert a raw WAnything document, reconciling the {@code _id} by the sync
     * unique key (worldId + collection + title): reuse the existing document's
     * {@code _id} when present, otherwise let MongoDB assign a new one.
     */
    @Transactional
    public Document upsertDocument(Document doc) {
        String collectionName = mongoTemplate.getCollectionName(WAnything.class);
        Query query = new Query(Criteria.where("worldId").is(doc.getString("worldId"))
                .and("collection").is(doc.getString("collection"))
                .and("title").is(doc.getString("title")));
        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
        doc.remove("_id");
        if (existing != null) {
            doc.put("_id", existing.get("_id"));
        }
        return mongoTemplate.save(doc, collectionName);
    }
}
