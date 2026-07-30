package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Backdrop;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for managing WBackdrop entities.
 * Backdrops are only stored in main worlds (no branches, no instances, no zones).
 * Similar to assets, backdrops must be created in the main world to be used in branches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WBackdropService {

    private final WBackdropRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Find backdrop by ID.
     * Always looks up in main world only (no branches, no instances, no zones).
     */
    @Transactional(readOnly = true)
    public Optional<WBackdrop> findByBackdropId(WorldId worldId, String backdropId) {
        var lookupWorld = worldId.toMainWorld();
        var collection = WorldCollection.of(lookupWorld, backdropId);
        return repository.findByWorldIdAndBackdropId(collection.worldId().getId(), backdropId);
    }

    /**
     * Find all backdrops for a world.
     * Always looks up in main world only (no branches, no instances, no zones).
     */
    @Transactional(readOnly = true)
    public List<WBackdrop> findByWorldId(WorldId worldId) {
        var lookupWorld = worldId.toMainWorld();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Find all enabled backdrops for a world.
     * Always looks up in main world only (no branches, no instances, no zones).
     */
    @Transactional(readOnly = true)
    public List<WBackdrop> findAllEnabled(WorldId worldId) {
        var lookupWorld = worldId.toMainWorld();
        return repository.findByWorldIdAndEnabled(lookupWorld.getId(), true);
    }

    /**
     * Save or update a backdrop.
     * Always saves to main world only (no branches, no instances, no zones).
     */
    @Transactional
    public WBackdrop save(WorldId worldId, String backdropId, Backdrop publicData) {
        if (Strings.isBlank(backdropId)) {
            throw new IllegalArgumentException("backdropId required");
        }
        if (publicData == null) {
            throw new IllegalArgumentException("publicData required");
        }
        if (worldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("Cannot save backdrop to instance or zone world");
        }

        WBackdrop entity = repository.findByWorldIdAndBackdropId(worldId.getId(), backdropId).orElseGet(() -> {
            WBackdrop neu = WBackdrop.builder()
                    .backdropId(backdropId)
                    .worldId(worldId.getId())
                    .enabled(true)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WBackdrop: {}", backdropId);
            return neu;
        });

        entity.setPublicData(publicData);
        entity.touchUpdate();
        entity.removeWorldPrefix();

        WBackdrop saved = repository.save(entity);
        log.debug("Saved WBackdrop: {}", backdropId);
        return saved;
    }

    @Transactional
    public List<WBackdrop> saveAll(WorldId worldId, List<WBackdrop> entities) {
        if (worldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("Cannot save backdrop to instance or zone world");
        }
        entities.forEach(e -> {
            if (e.getCreatedAt() == null) {
                e.touchCreate();
            }
            e.touchUpdate();
            e.setWorldId(worldId.getId());
            e.removeWorldPrefix();
        });
        List<WBackdrop> saved = repository.saveAll(entities);
        log.debug("Saved {} WBackdrop entities", saved.size());
        return saved;
    }

    /**
     * Update a backdrop.
     * Always updates in main world only (no branches, no instances, no zones).
     */
    @Transactional
    public Optional<WBackdrop> update(WorldId worldId, String backdropId, Consumer<WBackdrop> updater) {
        if (worldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("Cannot save backdrop to instance or zone world");
        }
        return repository.findByWorldIdAndBackdropId(worldId.getId(), backdropId).map(entity -> {
            updater.accept(entity);
            entity.touchUpdate();
            entity.removeWorldPrefix();
            WBackdrop saved = repository.save(entity);
            log.debug("Updated WBackdrop: {}", backdropId);
            return saved;
        });
    }

    /**
     * Delete a backdrop.
     * Always deletes from main world only (no branches, no instances, no zones).
     */
    @Transactional
    public boolean delete(WorldId worldId, String backdropId) {
        if (worldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("Cannot save backdrop to instance or zone world");
        }
        return repository.findByWorldIdAndBackdropId(worldId.getId(), backdropId).map(entity -> {
            repository.delete(entity);
            log.debug("Deleted WBackdrop: {}", backdropId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean disable(WorldId worldId, String backdropId) {
        return update(worldId, backdropId, entity -> entity.setEnabled(false)).isPresent();
    }

    @Transactional
    public boolean enable(WorldId worldId, String backdropId) {
        return update(worldId, backdropId, entity -> entity.setEnabled(true)).isPresent();
    }

    /**
     * Find all backdrops for a world with optional query filter.
     * Always looks up in main world only (no branches, no instances, no zones).
     */
    @Transactional(readOnly = true)
    public List<WBackdrop> findByWorldIdAndQuery(WorldId worldId, String query) {
        var lookupWorld = worldId.toMainWorld();
        List<WBackdrop> all = repository.findByWorldId(lookupWorld.getId());

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    /**
     * Delete ALL backdrops of a world (matched by the raw stored worldId, no
     * main-world resolution). Owner-level bulk operation so callers do not touch
     * the WBackdrop collection directly (data ownership).
     *
     * @return number of deleted backdrops
     */
    @Transactional
    public int deleteByWorldId(String worldId) {
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WBackdrop.class
        );
        long deleted = result.getDeletedCount();
        log.info("Deleted {} backdrops for world {}", deleted, worldId);
        return (int) deleted;
    }

    /**
     * Distinct world IDs that have backdrops (owner-level; avoids callers
     * querying the WBackdrop collection directly).
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WBackdrop.class, String.class);
    }

    /**
     * Duplicate all backdrops from a source world into a target world (matched by
     * the raw stored worldId, no main-world resolution). Owner-level bulk
     * operation so callers do not touch the WBackdrop collection directly.
     *
     * @return number of duplicated backdrops
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WBackdrop> sourceBackdrops = repository.findByWorldId(sourceWorldId);
        int duplicatedCount = 0;
        for (WBackdrop source : sourceBackdrops) {
            WBackdrop target = WBackdrop.builder()
                    .worldId(targetWorldId)
                    .backdropId(source.getBackdropId())
                    .publicData(source.getPublicData())
                    .enabled(source.isEnabled())
                    .build();
            target.touchCreate();
            repository.save(target);
            duplicatedCount++;
        }
        log.info("Duplicated {} backdrops from world {} to {}", duplicatedCount, sourceWorldId, targetWorldId);
        return duplicatedCount;
    }

    private List<WBackdrop> filterByQuery(List<WBackdrop> backdrops, String query) {
        String lowerQuery = query.toLowerCase();
        return backdrops.stream()
                .filter(backdrop -> {
                    String backdropId = backdrop.getBackdropId();
                    return (backdropId != null && backdropId.toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Repair duplicate WBackdrop entries (unique: worldId + backdropId).
     * Owner-level operation so callers do not access the WBackdrop collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WBackdrop.class, "backdrop", worldId,
                doc -> {
                    String backdropId = doc.getString("backdropId");
                    return backdropId != null ? doc.getString("worldId") + "|" + backdropId : null;
                }
        );
    }

    // ==================== SYNC DOCUMENT FACADE ====================
    // Raw org.bson.Document access for the SYNC cluster (world-control). Keeps
    // data ownership with this service while preserving the raw-document
    // behavior sync requires: _schema/_class fields stay untouched and schema
    // migration is applied externally on the raw JSON. worldId is matched
    // exactly as stored (no main-world resolution).

    /**
     * Export all backdrop documents of a world as raw MongoDB Documents.
     */
    @Transactional(readOnly = true)
    public List<Document> exportDocuments(String worldId) {
        String collectionName = mongoTemplate.getCollectionName(WBackdrop.class);
        return mongoTemplate.find(new Query(Criteria.where("worldId").is(worldId)), Document.class, collectionName);
    }

    /**
     * Find a single backdrop document by worldId + backdropId (unique key).
     */
    @Transactional(readOnly = true)
    public Optional<Document> findDocumentByWorldIdAndBackdropId(String worldId, String backdropId) {
        String collectionName = mongoTemplate.getCollectionName(WBackdrop.class);
        Query query = new Query(Criteria.where("worldId").is(worldId).and("backdropId").is(backdropId));
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, collectionName));
    }

    /**
     * Upsert a raw backdrop document, reconciling the {@code _id} by the unique
     * key (worldId + backdropId): reuse the existing document's {@code _id} when
     * present, otherwise let MongoDB assign a new one.
     */
    @Transactional
    public Document upsertDocument(Document doc) {
        String collectionName = mongoTemplate.getCollectionName(WBackdrop.class);
        Query query = new Query(Criteria.where("worldId").is(doc.getString("worldId"))
                .and("backdropId").is(doc.getString("backdropId")));
        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
        doc.remove("_id");
        if (existing != null) {
            doc.put("_id", existing.get("_id"));
        }
        return mongoTemplate.save(doc, collectionName);
    }

}
