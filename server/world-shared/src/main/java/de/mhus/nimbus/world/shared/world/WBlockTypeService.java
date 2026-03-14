package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.BlockType;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
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

    /**
     * Find block type by blockId.
     * Instances and zones always look up in their main world.
     */
    @Transactional(readOnly = true)
    public Optional<WBlockType> findByBlockId(WorldId worldId, String blockId) {

        var lookupWorld = worldId.toMainWorld();
        var collection = WorldCollection.of(lookupWorld, blockId);

        return repository.findByWorldIdAndBlockId(collection.worldId().getId(), collection.path());
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
        var entityOpt = repository.findByWorldIdAndBlockId(collection.worldId().getId(), collection.path());
        WBlockType entity = null;
        if (entityOpt.isEmpty()) {
            entity = WBlockType.builder()
                    .blockId(collection.path())
                    .worldId(collection.worldId().getId())
                    .enabled(true)
                    .build();
            entity.touchCreate();
            log.debug("Creating new WBlockType: {}", blockId);
        } else {
            entity = entityOpt.get();
        }

        entity.setBlockId(collection.path()); // maybe update if group changed

        // Ensure publicData.id has NOT full blockId with prefix (e.g., "wfr" not "r:wfr")
        String fullBlockId = collection.path();
        publicData.setId(fullBlockId);

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
        return repository.findByWorldIdAndBlockId(collection.worldId().getId(), collection.path()).map(entity -> {
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

        return repository.findByWorldIdAndBlockId(collection.worldId().getId(), collection.path()).map(entity -> {
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
                    String key = block.getWorldId() + ":" + block.getBlockId();
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
                String key = block.getWorldId() + ":" + block.getBlockId();
                if (uniqueIds.add(key)) {
                    results.add(block);
                }
            }
            log.debug("Found {} block types in region collection={}", regionBlocks.size(), regionCollection);
        }

        // 3. Search in the specified worldId last
        List<WBlockType> worldBlocks = repository.findByWorldId(worldId.getId());
        for (WBlockType block : worldBlocks) {
            String key = block.getWorldId() + ":" + block.getBlockId();
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
        List<WBlockType> all = lookupBlockTypes(worldId);

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
                    String blockId = blockType.getBlockId();
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

}
