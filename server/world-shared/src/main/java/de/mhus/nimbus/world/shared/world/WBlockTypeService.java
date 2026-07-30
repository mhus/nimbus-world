package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.BlockType;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Service for managing WBlockType entities.
 * Block types are stored per main world (no instances, no zones).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WBlockTypeService {

    private final WBlockTypeRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Find block type by blockId.
     * Instances and zones always look up in their main world.
     */
    @Transactional(readOnly = true)
    public Optional<WBlockType> findByBlockId(WorldId worldId, String blockId) {

        var lookupWorld = worldId.toMainWorld();
        var collection = WorldCollection.of(lookupWorld, blockId);

        return repository.findByWorldIdAndName(collection.worldId().getId(), collection.path());
    }

    /**
     * Find block types by group for specific world.
     * Filters out instances and zones.
     */
    @Transactional(readOnly = true)
    public List<WBlockType> findByBlockTypeGroup(WorldId worldId, String blockTypeGroup) {
        var collection = WorldCollection.of(worldId.toMainWorld(), blockTypeGroup + ":");
        var lookupWorld = collection.worldId();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Find all block types for specific world (no COW fallback for lists).
     * Filters out instances and zones.
     */
    @Transactional(readOnly = true)
    public List<WBlockType> findByWorldId(WorldId worldId) {
        var lookupWorld = worldId.toMainWorld();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Find all enabled block types for specific world.
     * Filters out instances and zones.
     */
    @Transactional(readOnly = true)
    public List<WBlockType> findAllEnabled(WorldId worldId) {
        var lookupWorld = worldId.toMainWorld();
        return repository.findByWorldIdAndEnabled(lookupWorld.getId(), true);
    }

    /**
     * Save or update a block type.
     * Filters out instances and zones - block types are stored per world.
     * Default blockTypeGroup is 'w' if not already set in publicData.
     */
    @Transactional
    public WBlockType save(WorldId worldId, String blockId, BlockType publicData) {
        if (Strings.isBlank(blockId)) {
            throw new IllegalArgumentException("blockId required");
        }
        if (publicData == null) {
            throw new IllegalArgumentException("publicData required");
        }
        if (worldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("Cannot save block type to instance or zone world");
        }
        if (!BlockUtil.isStatus(publicData.getInitialStatus())) {
            throw new IllegalArgumentException("Invalid initial status: " + publicData.getInitialStatus());
        }
        if (!publicData.getModifiers().containsKey(BlockUtil.DEFAULT_STATUS)) {
            throw  new IllegalArgumentException("publicData.modifiers must contain default status");
        }
        if (publicData.getModifiers().keySet().stream().anyMatch(k -> !BlockUtil.isStatus(k))) {
            throw new IllegalArgumentException("publicData.modifiers keys must be valid block statuses");
        }
        var collection = WorldCollection.of(worldId.toMainWorld(), blockId);
        var entityOpt = repository.findByWorldIdAndName(collection.worldId().getId(), collection.path());
        WBlockType entity = null;
        if (entityOpt.isEmpty()) {
            entity = WBlockType.builder()
                    .name(collection.path())
                    .worldId(collection.worldId().getId())
                    .enabled(true)
                    .build();
            entity.touchCreate();
            log.debug("Creating new WBlockType: {}", blockId);
        } else {
            entity = entityOpt.get();
        }

        entity.setName(collection.path()); // maybe update if group changed

        // Ensure publicData.name has full name with path (e.g., "wfr" not "r:wfr")
        publicData.setName(collection.path());

        entity.setPublicData(publicData);
        entity.removeWorldPrefix();
        entity.touchUpdate();

        WBlockType saved = repository.save(entity);
        log.debug("Saved WBlockType: {}", blockId);
        return saved;
    }

    /**
     * Update a block type.
     * Filters out instances and zones.
     */
    @Transactional
    public Optional<WBlockType> update(WorldId worldId, String blockId, Consumer<WBlockType> updater) {
        var collection = WorldCollection.of(worldId.toMainWorld(), blockId);
        return repository.findByWorldIdAndName(collection.worldId().getId(), collection.path()).map(entity -> {
            updater.accept(entity);
            entity.touchUpdate();
            entity.removeWorldPrefix();

            var publicData = entity.getPublicData();
            if (!BlockUtil.isStatus(publicData.getInitialStatus())) {
                throw new IllegalArgumentException("Invalid initial status: " + publicData.getInitialStatus());
            }
            if (!publicData.getModifiers().containsKey(BlockUtil.DEFAULT_STATUS)) {
                throw  new IllegalArgumentException("publicData.modifiers must contain default status");
            }
            if (publicData.getModifiers().keySet().stream().anyMatch(k -> !BlockUtil.isStatus(k))) {
                throw new IllegalArgumentException("publicData.modifiers keys must be valid block statuses");
            }

            WBlockType saved = repository.save(entity);
            log.debug("Updated WBlockType: {}", blockId);
            return saved;
        });
    }

    /**
     * Delete a block type.
     * Filters out instances and zones.
     */
    @Transactional
    public boolean delete(WorldId worldId, String blockId) {
        var collection = WorldCollection.of(worldId.toMainWorld(), blockId);

        return repository.findByWorldIdAndName(collection.worldId().getId(), collection.path()).map(entity -> {
            repository.delete(entity);
            log.debug("Deleted WBlockType: {}", blockId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean disable(WorldId worldId, String blockId) {
        return update(worldId, blockId, entity -> entity.setEnabled(false)).isPresent();
    }

    @Transactional
    public boolean enable(WorldId worldId, String blockId) {
        return update(worldId, blockId, entity -> entity.setEnabled(true)).isPresent();
    }

    public List<WBlockType> findByWorldIdAndQuery(WorldId worldId, String query) {

        // check query for prefix filter
        WorldId lookupWid = worldId;
        if (Strings.isNotBlank(query)) {
            int pos = query.indexOf(':');
            if (pos > 0) {
                var collection = WorldCollection.of(worldId, query);
                query = query.substring(pos + 1); // remaining query after prefix
                lookupWid = collection.worldId();
            }
        }

        List<WBlockType> all = findByWorldId(lookupWid);

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    /**
     * Lookup block types from multiple sources:
     * - Search in @shared collections first (preferred)
     * - If worldId is not a collection, also search in the region collection
     * - Search in the specified worldId and collection last
     * All results are merged and returned as one collection, with @shared results first.
     *
     * @param worldId The world identifier
     * @return List of all block types from all sources (shared first, then region, then world)
     */
    @Transactional(readOnly = true)
    public List<WBlockType> lookupBlockTypes(WorldId worldId) {
        if (worldId.isInstanceOrZone()) {
            worldId = worldId.toMainWorld();
        }

        java.util.Set<String> uniqueIds = new java.util.HashSet<>();
        List<WBlockType> results = new ArrayList<>();

        // 1. Search in @shared collections first (preferred)
        // We check common shared collections: @shared:n, @shared:default
        for (String sharedName : List.of("n", "default")) {
            WorldId sharedCollection = WorldId.of(WorldId.COLLECTION_SHARED, sharedName)
                    .orElse(null);
            if (sharedCollection != null) {
                List<WBlockType> sharedBlocks = repository.findByWorldId(sharedCollection.getId());
                for (WBlockType block : sharedBlocks) {
                    String key = block.getWorldId() + ":" + block.getName();
                    if (uniqueIds.add(key)) {
                        results.add(block);
                    }
                }
                log.debug("Found {} block types in shared collection={}", sharedBlocks.size(), sharedCollection);
            }
        }

        // 2. If worldId is not a collection, also search in the region collection
        if (!worldId.isCollection()) {
            WorldId regionCollection = worldId.toRegionCollection();
            List<WBlockType> regionBlocks = repository.findByWorldId(regionCollection.getId());
            for (WBlockType block : regionBlocks) {
                String key = block.getWorldId() + ":" + block.getName();
                if (uniqueIds.add(key)) {
                    results.add(block);
                }
            }
            log.debug("Found {} block types in region collection={}", regionBlocks.size(), regionCollection);
        }

        // 3. Search in the specified worldId last
        List<WBlockType> worldBlocks = repository.findByWorldId(worldId.getId());
        for (WBlockType block : worldBlocks) {
            String key = block.getWorldId() + ":" + block.getName();
            if (uniqueIds.add(key)) {
                results.add(block);
            }
        }
        log.debug("Found {} block types in worldId={}", worldBlocks.size(), worldId);

        log.debug("Total block types found: {} (from {} unique sources)", results.size(), uniqueIds.size());
        return results;
    }

    /**
     * Lookup block types and filter by query string.
     * Searches in @shared (preferred), region, and world collections.
     *
     * @param worldId The world identifier
     * @param query Search query for filtering results
     * @return Filtered list of block types from all sources
     */
    @Transactional(readOnly = true)
    public List<WBlockType> lookupBlockTypesByQuery(WorldId worldId, String query) {

        // Support collection prefix in query (e.g. "n:door" searches "door" in collection "n:")
        WorldId lookupWid = worldId;
        if (Strings.isNotBlank(query)) {
            int pos = query.indexOf(':');
            if (pos > 0) {
                var collection = WorldCollection.of(worldId, query);
                query = query.substring(pos + 1);
                lookupWid = collection.worldId();
            }
        }

        List<WBlockType> all = lookupBlockTypes(lookupWid);

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WBlockType> filterByQuery(List<WBlockType> blockTypes, String query) {
        String lowerQuery = query.toLowerCase();
        return blockTypes.stream()
                .filter(blockType -> {
                    String blockId = blockType.getName();
                    BlockType publicData = blockType.getPublicData();
                    return (blockId != null && blockId.toLowerCase().contains(lowerQuery)) ||
                            (publicData != null && publicData.getTitle() != null &&
                                    publicData.getTitle().toLowerCase().contains(lowerQuery)) ||
                            (publicData != null && publicData.getDescription() != null &&
                                    publicData.getDescription().toLowerCase().contains(lowerQuery)) ||
                            (publicData != null && publicData.getType() != null &&
                                    publicData.getType().name().toLowerCase().contains(lowerQuery));
                })
                .collect(Collectors.toList());
    }

    /**
     * Delete ALL block types stored under the given (raw) worldId collection.
     * Owner-level bulk operation so callers do not touch the WBlockType
     * repository directly (data ownership). Block types have no external
     * storage or sub-collections, so only the entities themselves are removed.
     *
     * @param worldId the raw world/collection id as stored on the entities
     * @return number of deleted block types
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        List<WBlockType> blockTypes = repository.findByWorldId(worldId);
        repository.deleteAll(blockTypes);
        log.info("Deleted {} block types for world {}", blockTypes.size(), worldId);
        return blockTypes.size();
    }

    /**
     * Distinct world IDs that have block types (owner-level; avoids callers
     * querying the WBlockType collection directly).
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WBlockType.class, String.class);
    }

    /**
     * Duplicate ALL block types from a source (raw) worldId collection into a
     * target world. Owner-level bulk operation preserving the exact copy
     * semantics: name, publicData and enabled flag are carried over, a fresh
     * create-timestamp is set on the target entity.
     *
     * @param sourceWorldId the raw source world/collection id
     * @param targetWorldId the raw target world/collection id
     * @return number of duplicated block types
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WBlockType> sourceBlockTypes = repository.findByWorldId(sourceWorldId);
        int duplicatedCount = 0;
        for (WBlockType sourceBlockType : sourceBlockTypes) {
            WBlockType targetBlockType = WBlockType.builder()
                    .name(sourceBlockType.getName())
                    .publicData(sourceBlockType.getPublicData())
                    .worldId(targetWorldId)
                    .enabled(sourceBlockType.isEnabled())
                    .build();
            targetBlockType.touchCreate();
            repository.save(targetBlockType);
            duplicatedCount++;
        }
        log.info("Duplicated {} block types from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
        return duplicatedCount;
    }

    /**
     * Repair duplicate WBlockType entries (unique: worldId + name).
     * Owner-level operation so callers do not access the WBlockType collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WBlockType.class, "blocktype", worldId,
                doc -> {
                    Object name = doc.get("name");
                    return name != null ? doc.getString("worldId") + "|" + name : null;
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
     * Export all block type documents of a world as raw MongoDB Documents.
     */
    @Transactional(readOnly = true)
    public List<Document> exportDocuments(String worldId) {
        String collectionName = mongoTemplate.getCollectionName(WBlockType.class);
        return mongoTemplate.find(new Query(Criteria.where("worldId").is(worldId)), Document.class, collectionName);
    }

    /**
     * Find a single block type document by worldId + blockId. The sync keys
     * block types on the stored {@code blockId} field, matched here to preserve
     * existing behavior.
     */
    @Transactional(readOnly = true)
    public Optional<Document> findDocumentByWorldIdAndBlockId(String worldId, String blockId) {
        String collectionName = mongoTemplate.getCollectionName(WBlockType.class);
        Query query = new Query(Criteria.where("worldId").is(worldId).and("blockId").is(blockId));
        return Optional.ofNullable(mongoTemplate.findOne(query, Document.class, collectionName));
    }

    /**
     * Upsert a raw block type document, reconciling the {@code _id} by the sync
     * unique key (worldId + blockId): reuse the existing document's {@code _id}
     * when present, otherwise let MongoDB assign a new one.
     */
    @Transactional
    public Document upsertDocument(Document doc) {
        String collectionName = mongoTemplate.getCollectionName(WBlockType.class);
        Query query = new Query(Criteria.where("worldId").is(doc.getString("worldId"))
                .and("blockId").is(doc.getString("blockId")));
        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
        doc.remove("_id");
        if (existing != null) {
            doc.put("_id", existing.get("_id"));
        }
        return mongoTemplate.save(doc, collectionName);
    }

}
