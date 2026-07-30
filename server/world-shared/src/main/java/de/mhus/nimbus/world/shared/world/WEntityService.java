package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.generated.types.Vector3;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for managing WEntity instances in the world.
 * Entities exist separately for each world/zone/instance.
 * No storage functionality supported (always world-instance-specific).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WEntityService {

    private final WEntityRepository repository;
    private final WWorldService worldService;
    private final MongoTemplate mongoTemplate;

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find entity by entityId.
     * Instances always look up in their world.
     */
    @Transactional(readOnly = true)
    public Optional<WEntity> findByWorldIdAndName(WorldId worldId, String entityId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndName(lookupWorld.getId(), entityId);
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find all entities for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByWorldId(WorldId worldId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldId(lookupWorld.getId());
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find entities by modelId for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByModelId(WorldId worldId, String modelId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndModelId(lookupWorld.getId(), modelId);
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find all enabled entities for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findAllEnabled(WorldId worldId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndEnabled(lookupWorld.getId(), true);
    }

    /**
     * Save or update an entity.
     * Filters out instances.
     * Single document per worldId+entityId — no epoch pull needed (see readme/EPOCH_ENTITY_MANAGEMENT.md).
     */
    @Transactional
    public WEntity save(WorldId worldId, String entityId, Entity publicData, String modelId) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(entityId)) {
            throw new IllegalArgumentException("entityId required");
        }
        if (publicData == null) {
            throw new IllegalArgumentException("publicData required");
        }
        if ((worldId.isInstance() && !worldId.isEditorInstance()) || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no player instance, no collection)");
        }
        var lookupWorld = worldId.toBaseWorldId();

        WEntity entity = repository.findByWorldIdAndName(lookupWorld.getId(), entityId).orElseGet(() -> {
            WEntity neu = WEntity.builder()
                    .worldId(lookupWorld.getId())
                    .name(entityId)
                    .modelId(modelId)
                    .enabled(true)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WEntity: world={}, entityId={}", lookupWorld, entityId);
            return neu;
        });

        entity.setPublicData(publicData);
        entity.setModelId(modelId);
        computeAffectedChunks(entity);
        entity.touchUpdate();

        WEntity saved = repository.save(entity);
        log.debug("Saved WEntity: world={}, entityId={}", lookupWorld, entityId);
        return saved;
    }

    // See readme/EPOCH_ENTITY_MANAGEMENT.md — single doc per key, no epoch pull needed
    @Transactional
    public List<WEntity> saveAll(List<WEntity> entities) {
        entities.forEach(e -> {
            computeAffectedChunks(e);
            if (e.getCreatedAt() == null) {
                e.touchCreate();
            }
            e.touchUpdate();
        });
        List<WEntity> saved = repository.saveAll(entities);
        log.debug("Saved {} WEntity entities", saved.size());
        return saved;
    }

    /**
     * Update an entity.
     * Denies out instances and collections.
     * Single document per worldId+entityId — no epoch pull needed (see readme/EPOCH_ENTITY_MANAGEMENT.md).
     */
    @Transactional
    public Optional<WEntity> update(WorldId worldId, String entityId, Consumer<WEntity> updater) {
        if ((worldId.isInstance() && !worldId.isEditorInstance()) || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no player instance, no collection)");
        }
        var lookupWorld = worldId.toBaseWorldId();

        return repository.findByWorldIdAndName(lookupWorld.getId(), entityId).map(entity -> {
            updater.accept(entity);
            computeAffectedChunks(entity);
            entity.touchUpdate();
            WEntity saved = repository.save(entity);
            log.debug("Updated WEntity: world={}, entityId={}", lookupWorld, entityId);
            return saved;
        });
    }

    /**
     * Delete an entity.
     * Denies out instances and collections.
     */
    @Transactional
    public boolean delete(WorldId worldId, String entityId) {
        if ((worldId.isInstance() && !worldId.isEditorInstance()) || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no player instance, no collection)");
        }
        var lookupWorld = worldId.toBaseWorldId();

        return repository.findByWorldIdAndName(lookupWorld.getId(), entityId).map(entity -> {
            repository.delete(entity);
            log.debug("Deleted WEntity: world={}, entityId={}", lookupWorld, entityId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean enable(WorldId worldId, String entityId) {
        return update(worldId, entityId, entity -> entity.setEnabled(true)).isPresent();
    }

    @Transactional
    public boolean disable(WorldId worldId, String entityId) {
        return update(worldId, entityId, entity -> entity.setEnabled(false)).isPresent();
    }

    /**
     * Delete all entities whose entityId starts with the given prefix.
     * Used to clean up generated entities (e.g., fauna) before regeneration.
     *
     * @param worldId the world identifier (must not be instance or collection)
     * @param prefix  the entityId prefix to match
     * @return number of deleted entities
     */
    @Transactional
    public int deleteByEntityIdPrefix(WorldId worldId, String prefix) {
        if ((worldId.isInstance() && !worldId.isEditorInstance()) || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no player instance, no collection)");
        }
        var lookupWorld = worldId.toBaseWorldId();
        List<WEntity> entities = repository.findByWorldIdAndNameStartingWith(
                lookupWorld.getId(), prefix);
        if (!entities.isEmpty()) {
            repository.deleteAll(entities);
            log.debug("Deleted {} WEntities with prefix '{}' in world {}", entities.size(), prefix, worldId);
        }
        return entities.size();
    }

    /**
     * Delete all entities with a given source that have any of the specified affected chunks.
     * Used to clean up generated entities in specific hex grid areas before regeneration.
     *
     * @param worldId   the world identifier (must not be instance or collection)
     * @param source    the source identifier (e.g., "fauna-generator")
     * @param chunkKeys collection of chunk keys to match against affectedChunks
     * @return number of deleted entities
     */
    @Transactional
    public int deleteBySourceAndAffectedChunks(WorldId worldId, String source, Collection<String> chunkKeys) {
        if ((worldId.isInstance() && !worldId.isEditorInstance()) || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no player instance, no collection)");
        }
        var lookupWorld = worldId.toBaseWorldId();
        List<WEntity> entities = repository.findByWorldIdAndSourceAndAffectedChunksIn(
                lookupWorld.getId(), source, chunkKeys);
        if (!entities.isEmpty()) {
            repository.deleteAll(entities);
            log.debug("Deleted {} WEntities with source '{}' in {} chunks in world {}",
                    entities.size(), source, chunkKeys.size(), worldId);
        }
        return entities.size();
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find all enabled entities that have any of the specified affected chunks.
     * Used for chunk-based entity loading in world-life.
     *
     * @param worldId   the world identifier (must not be instance or collection)
     * @param chunkKeys collection of chunk keys to match against affectedChunks
     * @return list of enabled entities in the specified chunks
     */
    @Transactional(readOnly = true)
    public List<WEntity> findEnabledByChunks(WorldId worldId, Collection<String> chunkKeys) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndEnabledAndAffectedChunksIn(lookupWorld.getId(), true, chunkKeys);
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find all entities for specific world with optional query filter.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByWorldIdAndQuery(WorldId worldId, String query) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        List<WEntity> all = repository.findByWorldId(lookupWorld.getId());

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    // ==================== EPOCH-AWARE QUERIES ====================

    /**
     * Find entity by entityId filtered by epoch.
     */
    @Transactional(readOnly = true)
    public Optional<WEntity> findByWorldIdAndName(WorldId worldId, String entityId, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndNameAndEpochesContaining(lookupWorld.getId(), entityId, epoch);
    }

    /**
     * Find all entities for specific world filtered by epoch.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByWorldId(WorldId worldId, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndEpochesContaining(lookupWorld.getId(), epoch);
    }

    /**
     * Find all enabled entities for specific world filtered by epoch.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findAllEnabled(WorldId worldId, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndEnabledAndEpochesContaining(lookupWorld.getId(), true, epoch);
    }

    /**
     * Find all enabled entities in specified chunks filtered by epoch.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findEnabledByChunks(WorldId worldId, Collection<String> chunkKeys, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        return repository.findByWorldIdAndEnabledAndAffectedChunksInAndEpochesContaining(lookupWorld.getId(), true, chunkKeys, epoch);
    }

    /**
     * Find all entities for specific world with optional query filter, filtered by epoch.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByWorldIdAndQuery(WorldId worldId, String query, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.toBaseWorldId();
        List<WEntity> all = repository.findByWorldIdAndEpochesContaining(lookupWorld.getId(), epoch);

        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    /**
     * Compute affected chunks for an entity based on its position and the world's chunk size.
     * Includes the chunk at the entity's position and all direct neighbors (3x3 grid).
     */
    private void computeAffectedChunks(WEntity entity) {
        Vector3 pos = entity.getPosition();
        if (pos == null || entity.getWorldId() == null) {
            entity.setAffectedChunks(new ArrayList<>());
            return;
        }
        int chunkSize = getChunkSize(entity.getWorldId());
        if (chunkSize <= 0) {
            entity.setAffectedChunks(new ArrayList<>());
            return;
        }
        int cx = Math.floorDiv((int) pos.getX(), chunkSize);
        int cz = Math.floorDiv((int) pos.getZ(), chunkSize);

        List<String> chunks = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.add((cx + dx) + ":" + (cz + dz));
            }
        }
        entity.setAffectedChunks(chunks);
    }

    private int getChunkSize(String worldId) {
        return worldService.getByWorldId(worldId)
                .filter(w -> w.getPublicData() != null)
                .map(w -> w.getPublicData().getChunkSize())
                .orElse(0);
    }

    private List<WEntity> filterByQuery(List<WEntity> entities, String query) {
        String lowerQuery = query.toLowerCase();
        return entities.stream()
                .filter(entity -> {
                    String entityId = entity.getName();
                    Entity publicData = entity.getPublicData();
                    return (entityId != null && entityId.toLowerCase().contains(lowerQuery)) ||
                            (publicData != null && publicData.getName() != null &&
                                    publicData.getName().toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Delete all entities for a world (used for instance cleanup).
     */
    @Transactional
    public void deleteByWorldId(String worldId) {
        repository.deleteByWorldId(worldId);
        log.info("Deleted all entities for worldId={}", worldId);
    }

    /**
     * Delete ALL entity instances of a world and return the number of deleted
     * documents. Owner-level bulk operation so callers do not touch the WEntity
     * repository directly (data ownership).
     *
     * @param worldId the raw world id whose entity instances should be deleted
     * @return number of deleted entity instances
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        List<WEntity> entities = repository.findByWorldId(worldId);
        repository.deleteAll(entities);
        log.info("Deleted {} entity instances for world {}", entities.size(), worldId);
        return entities.size();
    }

    /**
     * Distinct world IDs that have entity instances (owner-level; avoids callers
     * querying the WEntity collection directly).
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WEntity.class, String.class);
    }

    /**
     * Duplicate ALL entity instances of a source world to a target world. Owner-level
     * bulk operation so callers do not touch the WEntity repository directly
     * (data ownership). Each copy carries the same public data, chunks, model,
     * position, rotation, behavior and enabled flag as the source.
     *
     * @param sourceWorldId world id to copy entity instances from
     * @param targetWorldId world id to copy entity instances to (must already exist)
     * @return number of duplicated entity instances
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WEntity> sourceEntities = repository.findByWorldId(sourceWorldId);
        log.info("Found {} entity instances in source world {}", sourceEntities.size(), sourceWorldId);

        int entityCount = 0;
        for (WEntity sourceEntity : sourceEntities) {
            WEntity targetEntity = WEntity.builder()
                    .worldId(targetWorldId)
                    .name(sourceEntity.getName())
                    .publicData(sourceEntity.getPublicData())
                    .chunks(sourceEntity.getChunks())
                    .modelId(sourceEntity.getModelId())
                    .position(sourceEntity.getPosition())
                    .rotation(sourceEntity.getRotation())
                    .middlePoint(sourceEntity.getMiddlePoint())
                    .speed(sourceEntity.getSpeed())
                    .behaviorModel(sourceEntity.getBehaviorModel())
                    .behaviorConfig(sourceEntity.getBehaviorConfig())
                    .enabled(sourceEntity.isEnabled())
                    .build();

            targetEntity.touchCreate();
            repository.save(targetEntity);
            entityCount++;
        }

        log.info("Duplicated {} entity instances from world {} to {}",
                entityCount, sourceWorldId, targetWorldId);
        return entityCount;
    }

    // ==================== EPOCH MANAGEMENT (data ownership) ====================

    /**
     * Validate epoch consistency for this world's entity documents.
     */
    public EpochProcessResult validateEpochs(String worldId, List<WEpochMeta> epochMetas) {
        return EpochArrayHelper.validate(mongoTemplate, WEntity.class, "entity", worldId, epochMetas);
    }

    /**
     * Propagate a new epoch by copying it into documents that hold the source epoch.
     */
    public EpochProcessResult createEpoch(String worldId, int sourceEpoch, int newEpoch) {
        return EpochArrayHelper.create(mongoTemplate, WEntity.class, "entity", worldId, sourceEpoch, newEpoch);
    }

    /**
     * Remove an epoch from all of this world's entity documents.
     */
    public EpochProcessResult deleteEpoch(String worldId, int epoch) {
        return EpochArrayHelper.delete(mongoTemplate, WEntity.class, "entity", worldId, epoch);
    }

    /**
     * Repair duplicate WEntity entries (unique: worldId + entityId).
     * Owner-level operation so callers do not access the WEntity collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WEntity.class, "entity", worldId,
                doc -> {
                    String entityId = doc.getString("entityId");
                    return entityId != null ? doc.getString("worldId") + "|" + entityId : null;
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
     * Export all entity documents of a world as raw MongoDB Documents.
     */
    @Transactional(readOnly = true)
    public List<Document> exportDocuments(String worldId) {
        String collectionName = mongoTemplate.getCollectionName(WEntity.class);
        return mongoTemplate.find(new Query(Criteria.where("worldId").is(worldId)), Document.class, collectionName);
    }

    /**
     * Find a single entity document by worldId + entityId (unique key).
     */
    @Transactional(readOnly = true)
    public Optional<Document> findDocumentByWorldIdAndEntityId(String worldId, String entityId) {
        String collectionName = mongoTemplate.getCollectionName(WEntity.class);
        Query query = new Query(Criteria.where("worldId").is(worldId).and("entityId").is(entityId));
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, collectionName));
    }

    /**
     * Upsert a raw entity document, reconciling the {@code _id} by the unique
     * key (worldId + entityId): reuse the existing document's {@code _id} when
     * present, otherwise let MongoDB assign a new one.
     */
    @Transactional
    public Document upsertDocument(Document doc) {
        String collectionName = mongoTemplate.getCollectionName(WEntity.class);
        Query query = new Query(Criteria.where("worldId").is(doc.getString("worldId"))
                .and("entityId").is(doc.getString("entityId")));
        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
        doc.remove("_id");
        if (existing != null) {
            doc.put("_id", existing.get("_id"));
        }
        return mongoTemplate.save(doc, collectionName);
    }

}
