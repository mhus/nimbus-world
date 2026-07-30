package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing WItem entities (inventory/template items without position).
 * Items are always stored in the @region collection and shared across the entire region.
 * Branches cannot have their own items.
 * Items do NOT support storage functionality (no itemGroup field).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WItemService {

    private final WItemRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Find all items for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItem> findByWorldId(WorldId worldId) {
        var regionWorldId = worldId.toRegionCollection(); // could also be a regular other collection
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        return repository.findByWorldId(regionWorldId.getId());
    }

    /**
     * Find all enabled items for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItem> findEnabledByWorldId(WorldId worldId) {
        var regionWorldId = worldId.toRegionCollection(); // could also be a regular other collection
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        return repository.findByWorldIdAndEnabled(regionWorldId.getId(), true);
    }

    /**
     * Find item by itemId.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public Optional<WItem> findByItemId(WorldId worldId, String itemId) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        return repository.findByWorldIdAndName(regionWorldId.getId(), itemId);
    }

    /**
     * Duplicates the item with the given itemId and creates a new item with the given new name as itemId.
     *
     * @param worldId The worldId of the region collection where the item exists. Must be a region collection.
     * @param itemId The itemId of the existing item to duplicate. Must exist in the region collection.
     * @param newName The name/itemId for the new item. Must not already exist.
     * @return The newly created item with the new itemId.
     */
    @Transactional
    public WItem duplicate(WorldId worldId, String itemId, String newName) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        var existing = repository.findByWorldIdAndName(regionWorldId.getId(), itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        var publicData = existing.getPublicData();
        publicData.setName(newName);
        return create(worldId, publicData);
    }

    @Transactional
    public WItem create(WorldId worldId, Item publicData) {
        String itemId;
        if (Strings.isBlank(publicData.getName())) {
            itemId = "i_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0,6);
            publicData.setName(itemId);
        } else {
            itemId = publicData.getName();
            var regionWorldId = worldId.toRegionCollection();
            if (repository.findByWorldIdAndName(regionWorldId.getId(), itemId).isPresent()) {
                throw new IllegalArgumentException("Item with itemId already exists: " + itemId);
            }
        }
        return save(worldId, itemId, publicData);
    }

    /**
     * Save a new item or update existing.
     * Always saves to region collection (shared across entire region).
     */
    @Transactional
    public WItem save(WorldId worldId, String itemId, Item publicData) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId is required");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }

        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }

        Optional<WItem> existing = repository.findByWorldIdAndName(regionWorldId.getId(), itemId);
        if (existing.isPresent()) {
            WItem item = existing.get();
            item.setPublicData(publicData);
            item.touchUpdate();
            log.debug("Updated item: regionWorldId={}, itemId={}", regionWorldId, itemId);
            return repository.save(item);
        }

        WItem item = WItem.builder()
                .worldId(regionWorldId.getId())
                .name(itemId)
                .publicData(publicData)
                .enabled(true)
                .build();
        item.touchCreate();

        log.debug("Created item: regionWorldId={}, itemId={}", regionWorldId, itemId);
        return repository.save(item);
    }

    /**
     * Update item publicData.
     * Always updates in region collection.
     */
    @Transactional
    public Optional<WItem> update(WorldId worldId, String itemId, Item publicData) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        return repository.findByWorldIdAndName(regionWorldId.getId(), itemId).map(item -> {
            item.setPublicData(publicData);
            item.touchUpdate();
            log.debug("Updated item publicData: regionWorldId={}, itemId={}", regionWorldId, itemId);
            return repository.save(item);
        });
    }

    /**
     * Rename itemId of an existing item.
     * Checks that the new itemId does not already exist in the same world.
     */
    @Transactional
    public WItem renameItemId(WorldId worldId, String oldItemId, String newItemId) {
        if (Strings.isBlank(newItemId)) {
            throw new IllegalArgumentException("newItemId is required");
        }
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        WItem item = repository.findByWorldIdAndName(regionWorldId.getId(), oldItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + oldItemId));
        if (oldItemId.equals(newItemId)) {
            return item;
        }
        if (repository.findByWorldIdAndName(regionWorldId.getId(), newItemId).isPresent()) {
            throw new IllegalArgumentException("Item with itemId already exists: " + newItemId);
        }
        item.setName(newItemId);
        item.getPublicData().setName(newItemId);
        item.touchUpdate();
        log.debug("Renamed item: regionWorldId={}, oldItemId={}, newItemId={}", regionWorldId, oldItemId, newItemId);
        return repository.save(item);
    }

    /**
     * Disable (soft delete) an item.
     * Always disables in region collection.
     */
    @Transactional
    public boolean disable(WorldId worldId, String itemId) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        return repository.findByWorldIdAndName(regionWorldId.getId(), itemId).map(item -> {
            if (!item.isEnabled()) return false;
            item.setEnabled(false);
            item.touchUpdate();
            repository.save(item);
            log.debug("Disabled item: regionWorldId={}, itemId={}", regionWorldId, itemId);
            return true;
        }).orElse(false);
    }

    /**
     * Hard delete an item.
     * Always deletes from region collection.
     */
    @Transactional
    public boolean delete(WorldId worldId, String itemId) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        return repository.findByWorldIdAndName(regionWorldId.getId(), itemId).map(item -> {
            repository.delete(item);
            log.debug("Deleted item: regionWorldId={}, itemId={}", regionWorldId, itemId);
            return true;
        }).orElse(false);
    }

    /**
     * Save a WItem entity directly (e.g. after modifying server-side fields).
     */
    @Transactional
    public WItem saveEntity(WItem item) {
        item.touchUpdate();
        return repository.save(item);
    }

    /**
     * Save all items (batch operation for import).
     */
    @Transactional
    public List<WItem> saveAll(WorldId worldId, List<WItem> items) {
        return repository.saveAll(items);
    }

    /**
     * Find enabled items with optional query filter.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItem> findEnabledByWorldIdAndQuery(WorldId worldId, String query) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        List<WItem> all = repository.findByWorldIdAndEnabled(regionWorldId.getId(), true);

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    /**
     * Update parameters for all items matching a specific itemType.
     * Merges the given parameters into each item's existing parameters.
     *
     * @return number of updated items
     */
    @Transactional
    public int updateParametersByItemType(WorldId worldId, String itemType, Map<String, String> parameters) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        List<WItem> items = repository.findByWorldIdAndItemType(regionWorldId.getId(), itemType);
        for (WItem item : items) {
            Item publicData = item.getPublicData();
            if (publicData == null) continue;
            if (publicData.getParameters() == null) {
                publicData.setParameters(new HashMap<>(parameters));
            } else {
                publicData.getParameters().putAll(parameters);
            }
            item.touchUpdate();
            repository.save(item);
        }
        log.debug("Updated parameters for {} items with itemType={}", items.size(), itemType);
        return items.size();
    }

    /**
     * Merge parameters into a single item identified by itemId.
     *
     * @return the updated item, or empty if not found
     */
    @Transactional
    public Optional<WItem> updateParametersByItemId(WorldId worldId, String itemId, Map<String, String> parameters) {
        var regionWorldId = worldId.toRegionCollection();
        if (!regionWorldId.isRegionCollection()) {
            throw new IllegalArgumentException("worldId must be a region collection: " + worldId);
        }
        return repository.findByWorldIdAndName(regionWorldId.getId(), itemId).map(item -> {
            Item publicData = item.getPublicData();
            if (publicData.getParameters() == null) {
                publicData.setParameters(new HashMap<>(parameters));
            } else {
                publicData.getParameters().putAll(parameters);
            }
            item.touchUpdate();
            log.debug("Updated parameters for item: itemId={}", itemId);
            return repository.save(item);
        });
    }

    /**
     * Delete ALL items stored under the given raw worldId. Owner-level bulk
     * operation so callers do not touch the WItem collection directly (data
     * ownership). Operates on the raw worldId as-is (no region-collection
     * resolution) to match world-cleanup semantics.
     *
     * @return number of deleted items
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        List<WItem> items = repository.findByWorldId(worldId);
        repository.deleteAll(items);
        log.info("Deleted {} items for world {}", items.size(), worldId);
        return items.size();
    }

    /**
     * Distinct world IDs that have items (owner-level; avoids callers querying
     * the WItem collection directly).
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WItem.class, String.class);
    }

    /**
     * Duplicate ALL items from a source world into a target world. Owner-level
     * bulk operation operating on raw worldIds as-is (no region-collection
     * resolution) to match world-duplication semantics. Copies name, publicData,
     * server parameters and enabled flag into freshly created items.
     *
     * @return number of duplicated items
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WItem> sourceItems = repository.findByWorldId(sourceWorldId);
        int duplicatedCount = 0;
        for (WItem source : sourceItems) {
            WItem target = WItem.builder()
                    .worldId(targetWorldId)
                    .name(source.getName())
                    .publicData(source.getPublicData())
                    .server(source.getServer() != null ? new HashMap<>(source.getServer()) : null)
                    .enabled(source.isEnabled())
                    .build();
            target.touchCreate();
            repository.save(target);
            duplicatedCount++;
        }
        log.info("Duplicated {} items from world {} to {}", duplicatedCount, sourceWorldId, targetWorldId);
        return duplicatedCount;
    }

    private List<WItem> filterByQuery(List<WItem> items, String query) {
        String lowerQuery = query.toLowerCase();
        return items.stream()
                .filter(item -> {
                    Item publicData = item.getPublicData();
                    if (publicData == null) return false;

                    // Match query against itemId, name, or description
                    return (publicData.getName() != null && publicData.getName().toLowerCase().contains(lowerQuery)) ||
                            (publicData.getName() != null && publicData.getName().toLowerCase().contains(lowerQuery)) ||
                            (publicData.getDescription() != null && publicData.getDescription().toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
