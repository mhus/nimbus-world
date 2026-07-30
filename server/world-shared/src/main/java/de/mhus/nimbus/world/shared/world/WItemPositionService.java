package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
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

/**
 * Service for managing item positions in the world.
 * Items are stored per chunk for efficient spatial queries.
 *
 * Item positions exist separately for each world/zone/instance.
 * Each world context is treated as a separate instance.
 * No storage functionality supported (always world-instance-specific).
 * List loading does NOT fall back to main world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WItemPositionService {

    private final WItemPositionRepository repository;
    private final WWorldService worldService;
    private final MongoTemplate mongoTemplate;

    /**
     * Save or update an item position.
     * Automatically calculates chunk key from item position.
     * For instance worlds: creates a COW copy in the instance layer.
     * Single document per worldId+itemId — no epoch pull needed (see readme/EPOCH_ENTITY_MANAGEMENT.md).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemBlockRef ItemBlockRef containing position and display data
     * @return Saved item position entity
     */
    @Transactional
    public WItemPosition saveItemPosition(WorldId worldId, ItemBlockRef itemBlockRef) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (itemBlockRef == null) {
            throw new IllegalArgumentException("itemBlockRef required");
        }
        if (itemBlockRef.getName() == null || itemBlockRef.getName().isBlank()) {
            throw new IllegalArgumentException("itemBlockRef.id required");
        }
        if (itemBlockRef.getPosition() == null) {
            throw new IllegalArgumentException("itemBlockRef.position required");
        }
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }

        String itemId = itemBlockRef.getName();
        Vector3 position = itemBlockRef.getPosition();
        // Editor instances: write directly to base world (no COW)
        WorldId lookupWorld = worldId.isEditorInstance() ? worldId.toBaseWorldId() : worldId;
        WWorld world = worldService.getByWorldId(worldId.toBaseWorldId().getId()).get();
        String chunk = world.getChunkKey((int)position.getX(), (int)position.getZ());

        // For player instance worlds: write to instance layer (COW)
        // For editor instances: write to base world
        WItemPosition itemPosition = repository.findByWorldIdAndItemId(lookupWorld.getId(), itemId)
                .orElseGet(() -> {
                    WItemPosition neu = WItemPosition.builder()
                            .worldId(lookupWorld.getId())
                            .itemId(itemId)
                            .chunk(chunk)
                            .enabled(true)
                            .build();
                    neu.touchCreate();
                    log.debug("Creating new item position: world={}, itemId={}, chunk={}",
                            worldId, itemId, chunk);
                    return neu;
                });

        itemPosition.setPublicData(itemBlockRef);
        itemPosition.setChunk(chunk);
        itemPosition.setEnabled(true);
        itemPosition.touchUpdate();

        WItemPosition saved = repository.save(itemPosition);
        log.debug("Saved item position: world={}, itemId={}, chunk={}",
                worldId, itemId, chunk);
        return saved;
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Get all items in a specific chunk.
     * Returns only enabled items.
     * For instance worlds: merges base world items with instance overrides (COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return List of ItemBlockRef objects for the chunk
     */
    @Transactional(readOnly = true)
    public List<ItemBlockRef> getItemsInChunk(WorldId worldId, int cx, int cz) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        String chunk = TypeUtil.toStringChunkCoord(cx, cz);

        List<WItemPosition> positions;
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            // Player instance: COW merge
            var baseList = repository.findByWorldIdAndChunkAndEnabled(
                    worldId.toBaseWorldId().getId(), chunk, true);
            var instanceList = repository.findByWorldIdAndChunk(
                    worldId.getId(), chunk);
            positions = CowUtil.merge(baseList, instanceList);
        } else {
            // Base world or editor instance: read directly from base world
            positions = repository.findByWorldIdAndChunkAndEnabled(
                    worldId.toBaseWorldId().getId(), chunk, true);
        }

        return positions.stream()
                .map(WItemPosition::getPublicData)
                .filter(data -> data != null)
                .toList();
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Get all items in a world.
     * Returns only enabled items.
     * For player instance worlds: merges base world items with instance overrides (COW).
     * For editor instances: reads directly from base world (no COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @return List of all item positions
     */
    @Transactional(readOnly = true)
    public List<WItemPosition> getAllItems(WorldId worldId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            // Player instance: COW merge
            var baseList = repository.findByWorldId(worldId.toBaseWorldId().getId());
            var instanceList = repository.findByWorldId(worldId.getId());
            return CowUtil.merge(baseList, instanceList);
        }
        // Base world or editor instance
        return repository.findByWorldId(worldId.toBaseWorldId().getId());
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find a specific item by ID.
     * For player instance worlds: checks instance layer first, falls back to base world (COW).
     * For editor instances: reads directly from base world (no COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemId Item identifier
     * @return Optional containing the item position if found (empty if tombstoned or not found)
     */
    @Transactional(readOnly = true)
    public Optional<WItemPosition> findItem(WorldId worldId, String itemId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            // Player instance: COW lookup
            var instanceEntry = repository.findByWorldIdAndItemId(worldId.getId(), itemId).orElse(null);
            var baseEntry = repository.findByWorldIdAndItemId(worldId.toBaseWorldId().getId(), itemId).orElse(null);
            return Optional.ofNullable(CowUtil.findOne(instanceEntry, baseEntry));
        }
        // Base world or editor instance
        return repository.findByWorldIdAndItemId(worldId.toBaseWorldId().getId(), itemId);
    }

    /**
     * Delete an item position.
     * Performs soft delete by creating a COW tombstone.
     * For player instance worlds: creates a tombstone in the instance layer (COW).
     * For editor instances: deletes directly from base world (no COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemId Item identifier
     * @return True if item was found and disabled
     */
    @Transactional
    public boolean deleteItemPosition(WorldId worldId, String itemId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }

        // Editor instance: operate directly on base world
        if (worldId.isEditorInstance()) {
            String baseId = worldId.toBaseWorldId().getId();
            Optional<WItemPosition> itemOpt = repository.findByWorldIdAndItemId(baseId, itemId);
            if (itemOpt.isPresent()) {
                WItemPosition item = itemOpt.get();
                item.setEnabled(false);
                item.touchUpdate();
                repository.save(item);
                log.info("Soft deleted item (editor instance): world={}, itemId={}", baseId, itemId);
                return true;
            }
            log.debug("Item not found for deletion (editor instance): world={}, itemId={}", baseId, itemId);
            return false;
        }

        // Check if item exists in instance layer
        Optional<WItemPosition> itemOpt = repository.findByWorldIdAndItemId(worldId.getId(), itemId);
        if (itemOpt.isPresent()) {
            WItemPosition item = itemOpt.get();
            item.setEnabled(false);
            item.touchUpdate();
            repository.save(item);
            log.info("Soft deleted item: world={}, itemId={}", worldId, itemId);
            return true;
        }

        // For player instance worlds: check if item exists in base world and create tombstone
        if (worldId.isInstance()) {
            Optional<WItemPosition> baseOpt = repository.findByWorldIdAndItemId(
                    worldId.toBaseWorldId().getId(), itemId);
            if (baseOpt.isPresent()) {
                WItemPosition tombstone = WItemPosition.builder()
                        .worldId(worldId.getId())
                        .itemId(itemId)
                        .chunk(baseOpt.get().getChunk())
                        .tombstone(true)
                        .build();
                tombstone.touchCreate();
                repository.save(tombstone);
                log.info("Created COW tombstone for item: world={}, itemId={}", worldId, itemId);
                return true;
            }
        }

        log.debug("Item not found for deletion: world={}, itemId={}", worldId, itemId);
        return false;
    }

    /**
     * Permanently delete an item position.
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemId Item identifier
     */
    @Transactional
    public void hardDeleteItemPosition(WorldId worldId, String itemId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        repository.deleteByWorldIdAndItemId(worldId.getId(), itemId);
        log.info("Hard deleted item: world={}, itemId={}",
                worldId, itemId);
    }

    /**
     * Save multiple item positions in batch.
     * Single document per worldId+itemId — no epoch pull needed (see readme/EPOCH_ENTITY_MANAGEMENT.md).
     *
     * @param items List of item positions to save
     * @return List of saved item positions
     */
    @Transactional
    public List<WItemPosition> saveAll(WorldId worldId, List<WItemPosition> items) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        items.forEach(item -> {
            if (item.getCreatedAt() == null) {
                item.touchCreate();
            }
            item.touchUpdate();
        });

        List<WItemPosition> saved = repository.saveAll(items);
        log.debug("Saved {} item positions", saved.size());
        return saved;
    }

    // ==================== EPOCH-AWARE QUERIES ====================

    /**
     * Get all items in a specific chunk filtered by epoch.
     * Returns only enabled items.
     * For instance worlds: merges base world items with instance overrides (COW).
     */
    @Transactional(readOnly = true)
    public List<ItemBlockRef> getItemsInChunk(WorldId worldId, int cx, int cz, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        String chunk = TypeUtil.toStringChunkCoord(cx, cz);

        List<WItemPosition> positions;
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            // Player instance: COW merge with epoch filter on base
            var baseList = repository.findByWorldIdAndChunkAndEnabledAndEpochesContaining(
                    worldId.toBaseWorldId().getId(), chunk, true, epoch);
            var instanceList = repository.findByWorldIdAndChunk(
                    worldId.getId(), chunk);
            positions = CowUtil.merge(baseList, instanceList);
        } else {
            // Base world or editor instance
            positions = repository.findByWorldIdAndChunkAndEnabledAndEpochesContaining(
                    worldId.toBaseWorldId().getId(), chunk, true, epoch);
        }

        return positions.stream()
                .map(WItemPosition::getPublicData)
                .filter(data -> data != null)
                .toList();
    }

    /**
     * Get all items in a world filtered by epoch.
     * Returns only enabled items.
     */
    @Transactional(readOnly = true)
    public List<WItemPosition> getAllItems(WorldId worldId, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            // Player instance: COW merge
            var baseList = repository.findByWorldIdAndEpochesContaining(worldId.toBaseWorldId().getId(), epoch);
            var instanceList = repository.findByWorldId(worldId.getId());
            return CowUtil.merge(baseList, instanceList);
        }
        // Base world or editor instance
        return repository.findByWorldIdAndEpochesContaining(worldId.toBaseWorldId().getId(), epoch);
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Find an item at exact world coordinates.
     * Searches within the chunk that contains the coordinates.
     * For instance worlds: uses COW merge (instance overrides base).
     *
     * @param worldId World identifier
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @return Optional containing the item position if found at exact coordinates
     */
    @Transactional(readOnly = true)
    public Optional<WItemPosition> getItemAt(WorldId worldId, int x, int y, int z) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        WWorld world = worldService.getByWorldId(worldId.toBaseWorldId().getId()).orElse(null);
        if (world == null) {
            return Optional.empty();
        }
        String chunk = world.getChunkKey(x, z);

        List<WItemPosition> positions;
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            // Player instance: COW merge
            var baseList = repository.findByWorldIdAndChunkAndEnabled(
                    worldId.toBaseWorldId().getId(), chunk, true);
            var instanceList = repository.findByWorldIdAndChunk(
                    worldId.getId(), chunk);
            positions = CowUtil.merge(baseList, instanceList);
        } else {
            // Base world or editor instance
            positions = repository.findByWorldIdAndChunkAndEnabled(
                    worldId.toBaseWorldId().getId(), chunk, true);
        }

        return positions.stream()
                .filter(item -> {
                    var data = item.getPublicData();
                    if (data == null || data.getPosition() == null) return false;
                    var pos = data.getPosition();
                    return (int) pos.getX() == x
                            && (int) pos.getY() == y
                            && (int) pos.getZ() == z;
                })
                .findFirst();
    }

    /**
     * Find an item at exact world coordinates, filtered by epoch.
     */
    @Transactional(readOnly = true)
    public Optional<WItemPosition> getItemAt(WorldId worldId, int x, int y, int z, int epoch) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        WWorld world = worldService.getByWorldId(worldId.toBaseWorldId().getId()).orElse(null);
        if (world == null) {
            return Optional.empty();
        }
        String chunk = world.getChunkKey(x, z);

        List<WItemPosition> positions;
        if (worldId.isInstance() && !worldId.isEditorInstance()) {
            var baseList = repository.findByWorldIdAndChunkAndEnabledAndEpochesContaining(
                    worldId.toBaseWorldId().getId(), chunk, true, epoch);
            var instanceList = repository.findByWorldIdAndChunk(
                    worldId.getId(), chunk);
            positions = CowUtil.merge(baseList, instanceList);
        } else {
            positions = repository.findByWorldIdAndChunkAndEnabledAndEpochesContaining(
                    worldId.toBaseWorldId().getId(), chunk, true, epoch);
        }

        return positions.stream()
                .filter(item -> {
                    var data = item.getPublicData();
                    if (data == null || data.getPosition() == null) return false;
                    var pos = data.getPosition();
                    return (int) pos.getX() == x
                            && (int) pos.getY() == y
                            && (int) pos.getZ() == z;
                })
                .findFirst();
    }

    /**
     * Delete all item positions for a world (used for instance cleanup).
     */
    @Transactional
    public void deleteByWorldId(String worldId) {
        repository.deleteByWorldId(worldId);
        log.info("Deleted all item positions for worldId={}", worldId);
    }

    /**
     * Delete ALL item positions of a world. Owner-level bulk operation so callers
     * do not touch the WItemPosition repository directly (data ownership).
     *
     * @param worldId World identifier
     * @return number of deleted item positions
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        List<WItemPosition> itemPositions = repository.findByWorldId(worldId);
        repository.deleteAll(itemPositions);
        log.info("Deleted {} item positions for world {}", itemPositions.size(), worldId);
        return itemPositions.size();
    }

    /**
     * Duplicate ALL item positions from a source world into a target world.
     * Owner-level bulk operation so callers do not touch the WItemPosition
     * repository directly (data ownership).
     *
     * @param sourceWorldId Source world identifier
     * @param targetWorldId Target world identifier
     * @return number of duplicated item positions
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WItemPosition> sourceItemPositions = repository.findByWorldId(sourceWorldId);
        int duplicatedCount = 0;
        for (WItemPosition sourceItemPosition : sourceItemPositions) {
            WItemPosition targetItemPosition = WItemPosition.builder()
                    .worldId(targetWorldId)
                    .itemId(sourceItemPosition.getItemId())
                    .chunk(sourceItemPosition.getChunk())
                    .publicData(sourceItemPosition.getPublicData())
                    .enabled(sourceItemPosition.isEnabled())
                    .build();
            targetItemPosition.touchCreate();
            repository.save(targetItemPosition);
            duplicatedCount++;
        }
        log.info("Duplicated {} item positions from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
        return duplicatedCount;
    }

    /**
     * Distinct world IDs that have item positions (owner-level; avoids callers
     * querying the WItemPosition collection directly).
     *
     * @return list of distinct world identifiers
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(
                new Query(),
                "worldId",
                WItemPosition.class,
                String.class
        );
    }

    // ==================== EPOCH MANAGEMENT (data ownership) ====================

    /**
     * Validate epoch consistency for this world's item-position documents.
     */
    public EpochProcessResult validateEpochs(String worldId, List<WEpochMeta> epochMetas) {
        return EpochArrayHelper.validate(mongoTemplate, WItemPosition.class, "item_position", worldId, epochMetas);
    }

    /**
     * Propagate a new epoch by copying it into documents that hold the source epoch.
     */
    public EpochProcessResult createEpoch(String worldId, int sourceEpoch, int newEpoch) {
        return EpochArrayHelper.create(mongoTemplate, WItemPosition.class, "item_position", worldId, sourceEpoch, newEpoch);
    }

    /**
     * Remove an epoch from all of this world's item-position documents.
     */
    public EpochProcessResult deleteEpoch(String worldId, int epoch) {
        return EpochArrayHelper.delete(mongoTemplate, WItemPosition.class, "item_position", worldId, epoch);
    }

    /**
     * Repair duplicate WItemPosition entries (unique: worldId + itemId).
     * Owner-level operation so callers do not access the WItemPosition collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WItemPosition.class, "itemposition", worldId,
                doc -> {
                    String itemId = doc.getString("itemId");
                    return itemId != null ? doc.getString("worldId") + "|" + itemId : null;
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
     * Export all item position documents of a world as raw MongoDB Documents.
     */
    @Transactional(readOnly = true)
    public List<Document> exportDocuments(String worldId) {
        String collectionName = mongoTemplate.getCollectionName(WItemPosition.class);
        return mongoTemplate.find(new Query(Criteria.where("worldId").is(worldId)), Document.class, collectionName);
    }

    /**
     * Find a single item position document by worldId + itemId (unique key).
     */
    @Transactional(readOnly = true)
    public Optional<Document> findDocumentByWorldIdAndItemId(String worldId, String itemId) {
        String collectionName = mongoTemplate.getCollectionName(WItemPosition.class);
        Query query = new Query(Criteria.where("worldId").is(worldId).and("itemId").is(itemId));
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, collectionName));
    }

    /**
     * Upsert a raw item position document, reconciling the {@code _id} by the
     * unique key (worldId + itemId): reuse the existing document's {@code _id}
     * when present, otherwise let MongoDB assign a new one.
     */
    @Transactional
    public Document upsertDocument(Document doc) {
        String collectionName = mongoTemplate.getCollectionName(WItemPosition.class);
        Query query = new Query(Criteria.where("worldId").is(doc.getString("worldId"))
                .and("itemId").is(doc.getString("itemId")));
        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
        doc.remove("_id");
        if (existing != null) {
            doc.put("_id", existing.get("_id"));
        }
        return mongoTemplate.save(doc, collectionName);
    }

    /**
     * Delete an item position document by its raw MongoDB {@code _id}.
     */
    @Transactional
    public void deleteDocumentById(Object id) {
        String collectionName = mongoTemplate.getCollectionName(WItemPosition.class);
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), collectionName);
    }

}
