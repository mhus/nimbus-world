package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Service for managing WEntityModel entities.
 * Entity models are always stored in the @region collection and shared across the entire region.
 * Branches cannot have their own entity models.
 * Entity models support storage functionality with default 'r' (region), but NOT 'w' (world).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WEntityModelService {

    private final WEntityModelRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Find entity model by modelId.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public Optional<WEntityModel> findByModelId(WorldId worldId, String modelId) {
        var lookupWorld = worldId.toMainWorld();
        var collection = WorldCollection.of(lookupWorld, modelId);
        lookupWorld = collection.worldId();
        lookupWorld = lookupWorld.toCollection(); // if the result is a world id, convert to region collection
        return repository.findByWorldIdAndName(lookupWorld.getId(), collection.path());
    }

    /**
     * Find all entity models for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WEntityModel> findByWorldId(WorldId worldId) {
        var regionWorldId = worldId.toRegionCollection();
        return repository.findByWorldId(regionWorldId.getId());
    }

    /**
     * Find all enabled entity models for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WEntityModel> findAllEnabled(WorldId worldId) {
        var regionWorldId = worldId.toCollection();
        return repository.findByWorldIdAndEnabled(regionWorldId.getId(), true);
    }

    /**
     * Save or update an entity model.
     * Always saves to region collection (shared across entire region).
     */
    @Transactional
    public WEntityModel save(WorldId worldId, String modelId, EntityModel publicData) {
        if (modelId == null) {
            throw new IllegalArgumentException("modelId required");
        }
        if (publicData == null) {
            throw new IllegalArgumentException("publicData required");
        }

        // Strip world prefix from modelId (e.g., "r:rover" → "rover") and resolve worldId
        var collection = WorldCollection.of(worldId, modelId);
        final var resolvedWorldId = collection.worldId();
        final var resolvedModelId = collection.path();

        if (!resolvedWorldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a collection id");
        }

        WEntityModel entity = repository.findByWorldIdAndName(resolvedWorldId.getId(), resolvedModelId).orElseGet(() -> {
            WEntityModel neu = WEntityModel.builder()
                    .name(resolvedModelId)
                    .worldId(resolvedWorldId.getId())
                    .enabled(true)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WEntityModel: {}", resolvedModelId);
            return neu;
        });

        entity.setPublicData(publicData);
        entity.touchUpdate();
        entity.removeWorldPrefix();

        WEntityModel saved = repository.save(entity);
        log.debug("Saved WEntityModel: {}", modelId);
        return saved;
    }

    @Transactional
    public List<WEntityModel> saveAll(WorldId worldId, List<WEntityModel> entities) {
        if (!worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a collection id");
        }
        entities.forEach(e -> {
            if (e.getCreatedAt() == null) {
                e.touchCreate();
            }
            e.setWorldId(worldId.getId());
            e.touchUpdate();
            e.removeWorldPrefix();
        });
        List<WEntityModel> saved = repository.saveAll(entities);
        log.debug("Saved {} WEntityModel entities", saved.size());
        return saved;
    }

    /**
     * Update an entity model.
     * Always updates in region collection.
     */
    @Transactional
    public Optional<WEntityModel> update(WorldId worldId, String modelId, Consumer<WEntityModel> updater) {
        // Strip world prefix from modelId and resolve worldId
        var collection = WorldCollection.of(worldId, modelId);
        final var resolvedWorldId = collection.worldId();
        final var resolvedModelId = collection.path();

        if (!resolvedWorldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a collection id");
        }
        return repository.findByWorldIdAndName(resolvedWorldId.getId(), resolvedModelId).map(entity -> {
            updater.accept(entity);
            entity.touchUpdate();
            entity.removeWorldPrefix();
            WEntityModel saved = repository.save(entity);
            log.debug("Updated WEntityModel: {}", resolvedModelId);
            return saved;
        });
    }

    /**
     * Delete an entity model.
     * Always deletes from region collection.
     */
    @Transactional
    public boolean delete(WorldId worldId, String modelId) {
        var collection = WorldCollection.of(worldId, modelId);
        final var resolvedWorldId = collection.worldId();
        final var resolvedModelId = collection.path();

        if (!resolvedWorldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a collection id");
        }
        return repository.findByWorldIdAndName(resolvedWorldId.getId(), resolvedModelId).map(entity -> {
            repository.delete(entity);
            log.debug("Deleted WEntityModel: {}", modelId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean disable(WorldId worldId, String modelId) {
        return update(worldId, modelId, entity -> entity.setEnabled(false)).isPresent();
    }

    @Transactional
    public boolean enable(WorldId worldId, String modelId) {
        return update(worldId, modelId, entity -> entity.setEnabled(true)).isPresent();
    }

    /**
     * Find all entity models for the region with optional query filter.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WEntityModel> findByWorldIdAndQuery(WorldId worldId, String query) {
        var regionWorldId = worldId.toCollection();
        List<WEntityModel> all = repository.findByWorldId(regionWorldId.getId());

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    /**
     * Find all enabled entity models for the given world with a specific type (e.g., 'avatar').
     */
    @Transactional(readOnly = true)
    public List<WEntityModel> findByWorldIdAndType(WorldId worldId, String type) {
        var regionWorldId = worldId.toCollection();
        return repository.findByWorldIdAndEnabled(regionWorldId.getId(), true).stream()
                .filter(m -> m.getPublicData() != null && type.equals(m.getPublicData().getType()))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<WEntityModel> filterByQuery(List<WEntityModel> models, String query) {
        String lowerQuery = query.toLowerCase();
        return models.stream()
                .filter(model -> {
                    String modelId = model.getName();
                    return (modelId != null && modelId.toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Delete ALL entity models stored directly under the given raw world id and
     * return the number of deleted documents. Owner-level bulk operation so callers
     * do not touch the WEntityModel repository directly (data ownership).
     *
     * @param worldId the raw world id whose entity models should be deleted
     * @return number of deleted entity models
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        List<WEntityModel> models = repository.findByWorldId(worldId);
        repository.deleteAll(models);
        log.info("Deleted {} entity models for world {}", models.size(), worldId);
        return models.size();
    }

    /**
     * Distinct world IDs that have entity models (owner-level; avoids callers
     * querying the WEntityModel collection directly).
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WEntityModel.class, String.class);
    }

    /**
     * Duplicate ALL entity models stored directly under the source world id to the
     * target world id. Owner-level bulk operation so callers do not touch the
     * WEntityModel repository directly (data ownership). Each copy carries the same
     * name, public data and enabled flag as the source.
     *
     * @param sourceWorldId world id to copy entity models from
     * @param targetWorldId world id to copy entity models to (must already exist)
     * @return number of duplicated entity models
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WEntityModel> sourceModels = repository.findByWorldId(sourceWorldId);
        log.info("Found {} entity models in source world {}", sourceModels.size(), sourceWorldId);

        int modelCount = 0;
        for (WEntityModel sourceModel : sourceModels) {
            WEntityModel targetModel = WEntityModel.builder()
                    .name(sourceModel.getName())
                    .publicData(sourceModel.getPublicData())
                    .worldId(targetWorldId)
                    .enabled(sourceModel.isEnabled())
                    .build();

            targetModel.touchCreate();
            repository.save(targetModel);
            modelCount++;
        }

        log.info("Duplicated {} entity models from world {} to {}",
                modelCount, sourceWorldId, targetWorldId);
        return modelCount;
    }

    /**
     * Repair duplicate WEntityModel entries (unique: worldId + name).
     * Owner-level operation so callers do not access the WEntityModel collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WEntityModel.class, "entitymodel", worldId,
                doc -> {
                    String modelId = doc.getString("name");
                    return modelId != null ? doc.getString("worldId") + "|" + modelId : null;
                }
        );
    }

    // ==================== SYNC DOCUMENT FACADE ====================
    // Raw org.bson.Document access for the SYNC cluster (world-control). Keeps
    // data ownership with this service while preserving the raw-document
    // behavior sync requires: _schema/_class fields stay untouched and schema
    // migration is applied externally on the raw JSON. worldId is matched
    // exactly as stored.

    /**
     * Export all entity model documents of a world as raw MongoDB Documents.
     */
    @Transactional(readOnly = true)
    public List<Document> exportDocuments(String worldId) {
        String collectionName = mongoTemplate.getCollectionName(WEntityModel.class);
        return mongoTemplate.find(new Query(Criteria.where("worldId").is(worldId)), Document.class, collectionName);
    }

    /**
     * Find a single entity model document by worldId + name (unique key).
     * BUG FIX: the previous sync type queried the non-existent {@code modelId}
     * field with the name value; the natural key is {@code name}.
     */
    @Transactional(readOnly = true)
    public Optional<Document> findDocumentByWorldIdAndName(String worldId, String name) {
        String collectionName = mongoTemplate.getCollectionName(WEntityModel.class);
        Query query = new Query(Criteria.where("worldId").is(worldId).and("name").is(name));
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, collectionName));
    }

    /**
     * Upsert a raw entity model document, reconciling the {@code _id} by the
     * unique key (worldId + name): reuse the existing document's {@code _id}
     * when present, otherwise let MongoDB assign a new one.
     * BUG FIX: reconciles on {@code name}, the actual natural key.
     */
    @Transactional
    public Document upsertDocument(Document doc) {
        String collectionName = mongoTemplate.getCollectionName(WEntityModel.class);
        Query query = new Query(Criteria.where("worldId").is(doc.getString("worldId"))
                .and("name").is(doc.getString("name")));
        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
        doc.remove("_id");
        if (existing != null) {
            doc.put("_id", existing.get("_id"));
        }
        return mongoTemplate.save(doc, collectionName);
    }

}
