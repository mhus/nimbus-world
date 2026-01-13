package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    /**
     * Find all items for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItem> findByWorldId(WorldId worldId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldId(regionWorldId.getId());
    }

    /**
     * Find all enabled items for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItem> findEnabledByWorldId(WorldId worldId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndEnabled(regionWorldId.getId(), true);
    }

    /**
     * Find item by itemId.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public Optional<WItem> findByItemId(WorldId worldId, String itemId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndItemId(regionWorldId.getId(), itemId);
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

        var regionWorldId = worldId.toRegionWorldId();

        Optional<WItem> existing = repository.findByWorldIdAndItemId(regionWorldId.getId(), itemId);
        if (existing.isPresent()) {
            WItem item = existing.get();
            item.setPublicData(publicData);
            item.touchUpdate();
            log.debug("Updated item: regionWorldId={}, itemId={}", regionWorldId, itemId);
            return repository.save(item);
        }

        WItem item = WItem.builder()
                .worldId(regionWorldId.getId())
                .itemId(itemId)
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
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndItemId(regionWorldId.getId(), itemId).map(item -> {
            item.setPublicData(publicData);
            item.touchUpdate();
            log.debug("Updated item publicData: regionWorldId={}, itemId={}", regionWorldId, itemId);
            return repository.save(item);
        });
    }

    /**
     * Disable (soft delete) an item.
     * Always disables in region collection.
     */
    @Transactional
    public boolean disable(WorldId worldId, String itemId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndItemId(regionWorldId.getId(), itemId).map(item -> {
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
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndItemId(regionWorldId.getId(), itemId).map(item -> {
            repository.delete(item);
            log.debug("Deleted item: regionWorldId={}, itemId={}", regionWorldId, itemId);
            return true;
        }).orElse(false);
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
        var regionWorldId = worldId.toRegionWorldId();
        List<WItem> all = repository.findByWorldIdAndEnabled(regionWorldId.getId(), true);

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WItem> filterByQuery(List<WItem> items, String query) {
        String lowerQuery = query.toLowerCase();
        return items.stream()
                .filter(item -> {
                    Item publicData = item.getPublicData();
                    if (publicData == null) return false;

                    // Match query against itemId, name, or description
                    return (publicData.getId() != null && publicData.getId().toLowerCase().contains(lowerQuery)) ||
                            (publicData.getName() != null && publicData.getName().toLowerCase().contains(lowerQuery)) ||
                            (publicData.getDescription() != null && publicData.getDescription().toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
