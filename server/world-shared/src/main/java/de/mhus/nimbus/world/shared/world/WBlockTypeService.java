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

        var lookupWorld = worldId.mainWorld();
        var collection = WorldCollection.of(lookupWorld, blockId);

        return repository.findByWorldIdAndBlockId(collection.worldId().getId(), collection.path());
    }

    /**
     * Find block types by group for specific world (no COW fallback for lists).
     * Filters out instances and zones.
     */
    @Transactional(readOnly = true)
    public List<WBlockType> findByBlockTypeGroup(WorldId worldId, String blockTypeGroup) {
        var collection = WorldCollection.of(worldId.mainWorld(), blockTypeGroup + ":");
        var lookupWorld = collection.worldId();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Find all block types for specific world (no COW fallback for lists).
     * Filters out instances and zones.
     */
    @Transactional(readOnly = true)
    public List<WBlockType> findByWorldId(WorldId worldId) {
        var lookupWorld = worldId.mainWorld();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Find all enabled block types for specific world (no COW fallback for lists).
     * Filters out instances and zones.
     */
    @Transactional(readOnly = true)
    public List<WBlockType> findAllEnabled(WorldId worldId) {
        var lookupWorld = worldId.mainWorld();
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

        var collection = WorldCollection.of(worldId.mainWorld(), blockId);
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

        // Ensure publicData.id has full blockId with prefix (e.g., "r:wfr" not just "wfr")
        String fullBlockId = collection.typeString() + ":" + collection.path();
        publicData.setId(fullBlockId);

        entity.setPublicData(publicData);
        entity.touchUpdate();

        WBlockType saved = repository.save(entity);
        log.debug("Saved WBlockType: {}", blockId);
        return saved;
    }

    @Transactional
    public List<WBlockType> saveAll(WorldId worldId, List<WBlockType> entities) {
        final List<WBlockType> saved = new ArrayList<>();
        entities.forEach(e -> {
            saved.add(save(worldId, e.getBlockId(), e.getPublicData()));
        });
        return saved;
    }

    /**
     * Update a block type.
     * Filters out instances and zones.
     */
    @Transactional
    public Optional<WBlockType> update(WorldId worldId, String blockId, Consumer<WBlockType> updater) {
        var collection = WorldCollection.of(worldId.mainWorld(), blockId);
        return repository.findByWorldIdAndBlockId(collection.worldId().getId(), collection.path()).map(entity -> {
            updater.accept(entity);
            entity.touchUpdate();
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
        var collection = WorldCollection.of(worldId.mainWorld(), blockId);

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
                                    publicData.getDescription().toLowerCase().contains(lowerQuery));
                })
                .collect(Collectors.toList());
    }

}
