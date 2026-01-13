package de.mhus.nimbus.world.shared.region;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class RUserItemsService {

    private final RUserItemsRepository repository;

    /**
     * Create or update an item for a user in a specific region
     */
    public RUserItems saveItem(String userId, String regionId, String itemId, Integer amount, String texture, String name, Set<String> labels) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is blank");
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId is blank");
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId is blank");

        Optional<RUserItems> existingOpt = repository.findByUserIdAndRegionIdAndItemId(userId, regionId, itemId);
        RUserItems item;
        if (existingOpt.isPresent()) {
            item = existingOpt.get();
            item.setAmount(amount);
            item.setTexture(texture);
            item.setName(name);
            item.setLabels(labels);
        } else {
            item = new RUserItems(userId, regionId, itemId, amount, texture, name, labels);
        }
        return repository.save(item);
    }

    /**
     * Get all items for a user in a specific region
     */
    public List<RUserItems> getItems(String userId, String regionId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is blank");
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId is blank");
        return repository.findByUserIdAndRegionId(userId, regionId);
    }

    /**
     * Get all items for a user across all regions
     */
    public List<RUserItems> getAllItemsForUser(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is blank");
        return repository.findByUserId(userId);
    }

    /**
     * Get a specific item
     */
    public Optional<RUserItems> getItem(String userId, String regionId, String itemId) {
        return repository.findByUserIdAndRegionIdAndItemId(userId, regionId, itemId);
    }

    /**
     * Delete a specific item
     */
    public void deleteItem(String userId, String regionId, String itemId) {
        repository.deleteByUserIdAndRegionIdAndItemId(userId, regionId, itemId);
    }

    /**
     * Delete all items for a user in a specific region
     */
    public void deleteAllItems(String userId, String regionId) {
        repository.deleteByUserIdAndRegionId(userId, regionId);
    }

    /**
     * Find items by label
     */
    public List<RUserItems> findItemsByLabel(String userId, String regionId, String label) {
        return getItems(userId, regionId).stream()
                .filter(item -> item.hasLabel(label))
                .collect(Collectors.toList());
    }

    /**
     * Find items with any of the specified labels
     */
    public List<RUserItems> findItemsByAnyLabel(String userId, String regionId, Collection<String> labels) {
        return getItems(userId, regionId).stream()
                .filter(item -> item.hasAnyLabel(labels))
                .collect(Collectors.toList());
    }

    /**
     * Find items with all of the specified labels
     */
    public List<RUserItems> findItemsByAllLabels(String userId, String regionId, Collection<String> labels) {
        return getItems(userId, regionId).stream()
                .filter(item -> item.hasAllLabels(labels))
                .collect(Collectors.toList());
    }

    /**
     * Add a label to an item
     */
    public RUserItems addLabel(String userId, String regionId, String itemId, String label) {
        RUserItems item = repository.findByUserIdAndRegionIdAndItemId(userId, regionId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        item.addLabel(label);
        return repository.save(item);
    }

    /**
     * Remove a label from an item
     */
    public RUserItems removeLabel(String userId, String regionId, String itemId, String label) {
        RUserItems item = repository.findByUserIdAndRegionIdAndItemId(userId, regionId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        item.removeLabel(label);
        return repository.save(item);
    }

    /**
     * Update item amount
     */
    public RUserItems updateAmount(String userId, String regionId, String itemId, Integer amount) {
        RUserItems item = repository.findByUserIdAndRegionIdAndItemId(userId, regionId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        item.setAmount(amount);
        return repository.save(item);
    }

    /**
     * Increment item amount
     */
    public RUserItems incrementAmount(String userId, String regionId, String itemId, int delta) {
        RUserItems item = repository.findByUserIdAndRegionIdAndItemId(userId, regionId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        int newAmount = (item.getAmount() != null ? item.getAmount() : 0) + delta;
        item.setAmount(Math.max(0, newAmount));
        return repository.save(item);
    }
}
