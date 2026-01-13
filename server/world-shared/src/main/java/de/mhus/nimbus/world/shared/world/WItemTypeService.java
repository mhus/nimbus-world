package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemType;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for managing WItemType entities.
 * Item types are always stored in the @region collection and shared across the entire region.
 * Branches cannot have their own item types.
 * Item types support storage functionality with default 'r' (region), but NOT 'w' (world).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WItemTypeService {

    private final WItemTypeRepository repository;

    /**
     * Find item type by itemType ID.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public Optional<WItemType> findByItemType(WorldId worldId, String itemType) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndItemType(regionWorldId.getId(), itemType);
    }

    /**
     * Find all item types for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItemType> findByWorldId(WorldId worldId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldId(regionWorldId.getId());
    }

    /**
     * Find all enabled item types for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItemType> findAllEnabled(WorldId worldId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndEnabled(regionWorldId.getId(), true);
    }

    /**
     * Save or update an item type.
     * Always saves to region collection (shared across entire region).
     * Default itemTypeGroup is 'r' (region).
     */
    @Transactional
    public WItemType save(WorldId worldId, String itemType, ItemType publicData) {
        if (blank(itemType)) {
            throw new IllegalArgumentException("itemType required");
        }
        if (publicData == null) {
            throw new IllegalArgumentException("publicData required");
        }

        var regionWorldId = worldId.toRegionWorldId();

        WItemType entity = repository.findByWorldIdAndItemType(regionWorldId.getId(), itemType).orElseGet(() -> {
            WItemType neu = WItemType.builder()
                    .itemType(itemType)
                    .worldId(regionWorldId.getId())
                    .enabled(true)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WItemType: {}", itemType);
            return neu;
        });

        entity.setPublicData(publicData);
        entity.touchUpdate();

        WItemType saved = repository.save(entity);
        log.debug("Saved WItemType: {}", itemType);
        return saved;
    }

    @Transactional
    public List<WItemType> saveAll(WorldId worldId, List<WItemType> entities) {
        entities.forEach(e -> {
            if (e.getCreatedAt() == null) {
                e.touchCreate();
            }
            e.touchUpdate();
        });
        List<WItemType> saved = repository.saveAll(entities);
        log.debug("Saved {} WItemType entities", saved.size());
        return saved;
    }

    /**
     * Update an item type.
     * Always updates in region collection.
     */
    @Transactional
    public Optional<WItemType> update(WorldId worldId, String itemType, Consumer<WItemType> updater) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndItemType(regionWorldId.getId(), itemType).map(entity -> {
            updater.accept(entity);
            entity.touchUpdate();
            WItemType saved = repository.save(entity);
            log.debug("Updated WItemType: {}", itemType);
            return saved;
        });
    }

    /**
     * Delete an item type.
     * Always deletes from region collection.
     */
    @Transactional
    public boolean delete(WorldId worldId, String itemType) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndItemType(regionWorldId.getId(), itemType).map(entity -> {
            repository.delete(entity);
            log.debug("Deleted WItemType: {}", itemType);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean disable(WorldId worldId, String itemType) {
        return update(worldId, itemType, entity -> entity.setEnabled(false)).isPresent();
    }

    @Transactional
    public boolean enable(WorldId worldId, String itemType) {
        return update(worldId, itemType, entity -> entity.setEnabled(true)).isPresent();
    }

    /**
     * Find all item types for the region with optional query filter.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WItemType> findByWorldIdAndQuery(WorldId worldId, String query) {
        var regionWorldId = worldId.toRegionWorldId();
        List<WItemType> all = repository.findByWorldId(regionWorldId.getId());

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WItemType> filterByQuery(List<WItemType> itemTypes, String query) {
        String lowerQuery = query.toLowerCase();
        return itemTypes.stream()
                .filter(itemType -> {
                    String type = itemType.getItemType();
                    ItemType publicData = itemType.getPublicData();
                    return (type != null && type.toLowerCase().contains(lowerQuery)) ||
                            (publicData != null && publicData.getName() != null &&
                                    publicData.getName().toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
