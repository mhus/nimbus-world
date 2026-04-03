package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemRef;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing WChest entities.
 * Provides business logic for chest operations including creation, retrieval, and item management.
 * Supports COW (Copy-on-Write) for instance worlds: base world chests are copied to the instance
 * layer on first write, so mutations never affect the base world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WChestService {

    private final WChestRepository repository;
    private final MongoTemplate mongoTemplate;

    // ===== Read operations (COW-aware) =====

    /**
     * Find chest by worldId and name.
     * COW-aware: for instance worlds, checks instance layer first, then base world.
     * Falls back to region collection worldId (@region:regionId) for bank/transfer chests.
     */
    @Transactional(readOnly = true)
    public Optional<WChest> getByWorldIdAndName(String worldId, String name) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);

        // For player instance worlds (not editor): check instance layer first, then base world (COW)
        if (parsedWorldId != null && parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance()) {
            var instanceEntry = repository.findByWorldIdAndName(worldId, name).orElse(null);
            var baseEntry = repository.findByWorldIdAndName(
                    parsedWorldId.toBaseWorldId().getId(), name).orElse(null);
            return Optional.ofNullable(CowUtil.findOne(instanceEntry, baseEntry));
        }

        // Editor instances and base worlds: lookup by base worldId
        String lookupWorldId = (parsedWorldId != null && parsedWorldId.isEditorInstance())
                ? parsedWorldId.toBaseWorldId().getId()
                : worldId;
        var result = repository.findByWorldIdAndName(lookupWorldId, name);
        if (result.isPresent()) {
            return result;
        }
        // Fallback: try region collection worldId
        if (parsedWorldId != null && !parsedWorldId.isCollection()) {
            String regionWorldId = parsedWorldId.toRegionCollection().getId();
            return repository.findByWorldIdAndName(regionWorldId, name);
        }
        return result;
    }

    /**
     * Find all chests for a specific world.
     * COW-aware: for instance worlds, merges base world chests with instance overrides.
     */
    @Transactional(readOnly = true)
    public List<WChest> findByWorldId(String worldId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        // Player instances (not editor): COW merge
        if (parsedWorldId != null && parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance()) {
            var baseList = repository.findByWorldId(parsedWorldId.toBaseWorldId().getId());
            var instanceList = repository.findByWorldId(worldId);
            return CowUtil.merge(baseList, instanceList);
        }
        // Editor instances: lookup by base worldId
        String lookupWorldId = (parsedWorldId != null && parsedWorldId.isEditorInstance())
                ? parsedWorldId.toBaseWorldId().getId()
                : worldId;
        return repository.findByWorldId(lookupWorldId);
    }

    // ===== COW copy-on-write support =====

    /**
     * Ensure a COW copy exists for a chest in an instance world.
     * If the chest belongs to the base world, creates a deep copy in the instance layer.
     * If the chest already belongs to the instance (or is a region chest), returns it as-is.
     *
     * @param worldId The instance worldId (must be an instance)
     * @param chest The chest to ensure a copy for
     * @return The instance-layer chest (either existing or newly created copy)
     */
    @Transactional
    public WChest ensureCowCopy(String worldId, WChest chest) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);

        // Not an instance world, editor instance, or chest is already in instance/region layer - no copy needed
        if (parsedWorldId == null || !parsedWorldId.isInstance() || parsedWorldId.isEditorInstance()) {
            return chest;
        }

        // Chest already belongs to this instance
        if (worldId.equals(chest.getWorldId())) {
            return chest;
        }

        // Chest is a region collection chest (bank/transfer) - not COW-managed
        var chestWorldId = WorldId.of(chest.getWorldId()).orElse(null);
        if (chestWorldId != null && chestWorldId.isCollection()) {
            return chest;
        }

        // Check if an instance copy already exists
        var existingCopy = repository.findByWorldIdAndName(worldId, chest.getName());
        if (existingCopy.isPresent()) {
            var copy = existingCopy.get();
            // Tombstone check: if tombstoned, the chest is "deleted" in this instance
            if (copy.isTombstone()) {
                log.warn("COW chest is tombstoned in instance: worldId={}, name={}", worldId, chest.getName());
                return copy;
            }
            return copy;
        }

        // Create COW copy: deep copy items to avoid sharing mutable list
        List<ItemRef> copiedItems = new ArrayList<>();
        if (chest.getItems() != null) {
            for (ItemRef item : chest.getItems()) {
                copiedItems.add(ItemRef.builder()
                        .itemId(item.getName())
                        .name(item.getName())
                        .texture(item.getTexture())
                        .amount(item.getAmount())
                        .build());
            }
        }

        WChest copy = WChest.builder()
                .worldId(worldId)
                .name(chest.getName())
                .title(chest.getTitle())
                .description(chest.getDescription())
                .playerId(chest.getPlayerId())
                .type(chest.getType())
                .pin(chest.getPin())
                .capacity(chest.getCapacity())
                .keyId(chest.getKeyId())
                .lockPickingDifficulty(chest.getLockPickingDifficulty())
                .items(copiedItems)
                .enabled(true)
                .build();
        copy.touchCreate();
        repository.save(copy);

        log.info("COW copy created for chest: worldId={}, name={}, baseWorldId={}",
                worldId, chest.getName(), chest.getWorldId());
        return copy;
    }

    /**
     * Get a mutable chest for an instance world.
     * If the chest is from the base world, creates a COW copy first.
     * This is the entry point for all write operations on instance chests.
     *
     * @param worldId The worldId (can be instance or base)
     * @param name The chest name
     * @return The mutable chest (COW copy if needed), or empty if not found
     */
    @Transactional
    public Optional<WChest> getForWrite(String worldId, String name) {
        var chest = getByWorldIdAndName(worldId, name);
        if (chest.isEmpty()) return Optional.empty();
        return Optional.of(ensureCowCopy(worldId, chest.get()));
    }

    // ===== Bank/Transfer chests (region-level, not COW-affected) =====

    /**
     * Get or create the user's bank chest in a world.
     * Bank chests are stored at region level and not affected by COW.
     */
    @Transactional
    public WChest getOrCreateUserBankChest(String worldId, PlayerId playerId) {
        var parsedWorldId = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("Invalid worldId: " + worldId));
        String regionWorldId = parsedWorldId.toRegionCollection().getId();
        String userId = playerId.getUserId();
        String playerIdStr = playerId.getId();

        // Try all combinations: regionWorldId and plain worldId x userId and playerId
        for (String wId : List.of(regionWorldId, worldId)) {
            for (String uId : List.of(playerIdStr, userId)) {
                var result = repository.findFirstByWorldIdAndPlayerIdAndType(wId, uId, WChest.ChestType.BANK);
                if (result.isPresent()) {
                    return result.get();
                }
            }
        }

        // No bank chest found - create one
        log.info("Creating bank chest for player: worldId={}, playerId={}", regionWorldId, playerIdStr);
        String name = "bank_" + playerIdStr.replace("@", "").replace(":", "_");
        WChest chest = WChest.builder()
                .worldId(regionWorldId)
                .name(name)
                .title("Bank")
                .playerId(playerIdStr)
                .type(WChest.ChestType.BANK)
                .capacity(10)
                .build();
        chest.touchCreate();
        return repository.save(chest);
    }

    /**
     * Get or create the user's transfer chest in a world.
     * Transfer chests are stored at region level and not affected by COW.
     */
    @Transactional
    public WChest getOrCreateUserTransferChest(String worldId, PlayerId playerId) {
        var parsedWorldId = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("Invalid worldId: " + worldId));
        String regionWorldId = parsedWorldId.toRegionCollection().getId();
        String userId = playerId.getUserId();
        String playerIdStr = playerId.getId();

        for (String wId : List.of(regionWorldId, worldId)) {
            for (String uId : List.of(playerIdStr, userId)) {
                var result = repository.findFirstByWorldIdAndPlayerIdAndType(wId, uId, WChest.ChestType.TRANSFER);
                if (result.isPresent()) {
                    return result.get();
                }
            }
        }

        log.info("Creating transfer chest for player: worldId={}, playerId={}", regionWorldId, playerIdStr);
        String name = "transfer_" + playerIdStr.replace("@", "").replace(":", "_");
        WChest chest = WChest.builder()
                .worldId(regionWorldId)
                .name(name)
                .title("Transfer")
                .playerId(playerIdStr)
                .type(WChest.ChestType.TRANSFER)
                .capacity(10)
                .build();
        chest.touchCreate();
        return repository.save(chest);
    }

    // ===== Additional read operations =====

    /**
     * Find all chests for a specific player in a world.
     */
    @Transactional(readOnly = true)
    public List<WChest> findByWorldIdAndPlayerId(String worldId, String playerId) {
        return repository.findByWorldIdAndPlayerId(worldId, playerId);
    }

    /**
     * Find all chests of a specific type in a world.
     */
    @Transactional(readOnly = true)
    public List<WChest> findByWorldIdAndType(String worldId, WChest.ChestType type) {
        return repository.findByWorldIdAndType(worldId, type);
    }

    // ===== Create / Update / Delete =====

    /**
     * Create a new chest.
     */
    @Transactional
    public WChest createChest(String worldId, String name, String title,
                              String description, String playerId, WChest.ChestType type) {
        if (repository.findByWorldIdAndName(worldId, name).isPresent()) {
            throw new IllegalStateException("Chest with name already exists in region: " + name);
        }
        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isSharedCollection() || parsedWorldId.isPublicRegion()) {
            throw new IllegalArgumentException("WChest can't be in shared or public region");
        }

        WChest chest = WChest.builder()
                .worldId(worldId)
                .name(name)
                .title(title)
                .description(description)
                .playerId(playerId)
                .type(type)
                .build();
        chest.touchCreate();
        repository.save(chest);
        log.debug("Chest created: worldId={}, name={}, type={}", worldId, name, type);
        return chest;
    }

    /**
     * Update an existing chest.
     */
    @Transactional
    public Optional<WChest> updateChest(String chestId, java.util.function.Consumer<WChest> updater) {
        return repository.findById(chestId).map(existing -> {
            updater.accept(existing);
            existing.touchUpdate();
            repository.save(existing);
            log.debug("Chest updated: id={}", chestId);
            return existing;
        });
    }

    /**
     * Add an item reference to a chest.
     * Simply adds the item. Does NOT check for duplicates or merge amounts.
     */
    @Transactional
    public Optional<WChest> addItem(String chestId, ItemRef itemRef) {
        return updateChest(chestId, chest -> {
            chest.getItems().add(itemRef);
            log.info("ItemRef added to chest: chestId={}, itemId={}, amount={}, totalItems={}",
                    chestId, itemRef.getItemId(), itemRef.getAmount(), chest.getItems().size());
        });
    }

    /**
     * Update the amount of an existing item in a chest.
     */
    @Transactional
    public Optional<WChest> updateItemAmount(String chestId, String itemId, int newAmount) {
        if (newAmount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0. Use removeItem() to delete.");
        }

        return updateChest(chestId, chest -> {
            int existingIndex = -1;
            for (int i = 0; i < chest.getItems().size(); i++) {
                if (chest.getItems().get(i).getItemId().equals(itemId)) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                ItemRef existing = chest.getItems().get(existingIndex);
                ItemRef updated = ItemRef.builder()
                        .itemId(existing.getName())
                        .name(existing.getName())
                        .texture(existing.getTexture())
                        .amount(newAmount)
                        .build();

                chest.getItems().set(existingIndex, updated);
                log.info("ItemRef amount updated in chest: chestId={}, itemId={}, oldAmount={}, newAmount={}",
                        chestId, itemId, existing.getAmount(), newAmount);
            } else {
                log.warn("ItemRef not found for amount update: chestId={}, itemId={}", chestId, itemId);
                throw new IllegalArgumentException("Item not found in chest: " + itemId);
            }
        });
    }

    /**
     * Remove an item reference from a chest by item ID.
     * Only removes the first occurrence.
     */
    @Transactional
    public Optional<WChest> removeItem(String chestId, String itemId) {
        return updateChest(chestId, chest -> {
            log.debug("Removing ItemRef from chest: chestId={}, itemId={}, currentItems={}",
                    chestId, itemId, chest.getItems().size());

            int indexToRemove = -1;
            for (int i = 0; i < chest.getItems().size(); i++) {
                if (chest.getItems().get(i).getItemId().equals(itemId)) {
                    indexToRemove = i;
                    break;
                }
            }

            if (indexToRemove >= 0) {
                ItemRef removed = chest.getItems().remove(indexToRemove);
                log.info("ItemRef removed from chest: chestId={}, itemId={}, removedAmount={}, remainingItems={}",
                        chestId, itemId, removed.getAmount(), chest.getItems().size());
            } else {
                log.warn("ItemRef not found in chest: chestId={}, itemId={}", chestId, itemId);
            }
        });
    }

    // ===== Atomic MongoTemplate operations =====

    /**
     * Atomically add an item to a chest's items array.
     * COW-safe: caller must pass the ID of a COW copy (from ensureCowCopy/getForWrite).
     *
     * @param chestId MongoDB document id (must be instance copy, not base world)
     * @param itemRef the ItemRef to add
     * @return true if the update was applied
     */
    public boolean addItemAtomic(String chestId, ItemRef itemRef) {
        Query query = new Query(Criteria.where("id").is(chestId));
        Update update = new Update()
                .push("items", itemRef)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WChest.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("addItemAtomic failed: chestId={}, itemId={}", chestId, itemRef.getItemId());
        return false;
    }

    /**
     * Atomically update the amount of an existing item in a chest.
     * COW-safe: caller must pass the ID of a COW copy.
     */
    public boolean updateItemAmountAtomic(String chestId, String itemId, int newAmount) {
        Query query = new Query(Criteria.where("id").is(chestId)
                .and("items.itemId").is(itemId));
        Update update = new Update()
                .set("items.$.amount", newAmount)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WChest.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("updateItemAmountAtomic failed: chestId={}, itemId={}", chestId, itemId);
        return false;
    }

    /**
     * Atomically increment the amount of an existing item in a chest.
     * COW-safe: caller must pass the ID of a COW copy.
     */
    public boolean incItemAmountAtomic(String chestId, String itemId, int delta) {
        Query query = new Query(Criteria.where("id").is(chestId)
                .and("items.itemId").is(itemId));
        Update update = new Update()
                .inc("items.$.amount", delta)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WChest.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("incItemAmountAtomic failed: chestId={}, itemId={}, delta={}", chestId, itemId, delta);
        return false;
    }

    /**
     * Atomically remove an item from a chest's items array.
     * COW-safe: caller must pass the ID of a COW copy.
     */
    public boolean removeItemAtomic(String chestId, String itemId) {
        Query query = new Query(Criteria.where("id").is(chestId));
        Update update = new Update()
                .pull("items", new org.bson.Document("itemId", itemId))
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WChest.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("removeItemAtomic failed: chestId={}, itemId={}", chestId, itemId);
        return false;
    }

    // ===== Save / Delete =====

    /**
     * Save a chest (updates modification timestamp).
     */
    @Transactional
    public WChest save(WChest chest) {
        chest.touchUpdate();
        WorldId parsedWorldId = WorldId.of(chest.getWorldId()).orElseThrow();
        if (parsedWorldId.isSharedCollection() || parsedWorldId.isPublicRegion()) {
            throw new IllegalArgumentException("WChest can't be in shared or public region");
        }
        WChest saved = repository.save(chest);
        log.debug("Chest saved: id={}", chest.getId());
        return saved;
    }

    /**
     * Delete a chest by ID.
     */
    @Transactional
    public boolean deleteChestById(String chestId) {
        return repository.findById(chestId).map(chest -> {
            repository.delete(chest);
            log.debug("Chest deleted: id={}", chestId);
            return true;
        }).orElse(false);
    }

    /**
     * Delete a chest by worldId and name.
     * COW-aware: for instance worlds, creates a tombstone instead of deleting the base chest.
     */
    @Transactional
    public void deleteChest(String worldId, String name) {
        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isSharedCollection() || parsedWorldId.isPublicRegion()) {
            throw new IllegalArgumentException("WChest can't be in shared or public region");
        }

        // Resolve lookup worldId: editor instances use base world directly
        String lookupWorldId = parsedWorldId.isEditorInstance()
                ? parsedWorldId.toBaseWorldId().getId()
                : worldId;

        // Check for existing entry
        var directEntry = repository.findByWorldIdAndName(lookupWorldId, name);
        if (directEntry.isPresent()) {
            if (parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance()) {
                // Player instance copy exists: mark as tombstone
                WChest chest = directEntry.get();
                chest.setEnabled(false);
                chest.touchUpdate();
                repository.save(chest);
                log.debug("Chest tombstoned in instance: worldId={}, name={}", worldId, name);
            } else {
                // Base world or editor instance: hard delete
                repository.delete(directEntry.get());
                log.debug("Chest deleted: worldId={}, name={}", lookupWorldId, name);
            }
            return;
        }

        // For player instance worlds: check if chest exists in base world and create tombstone
        if (parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance()) {
            var baseEntry = repository.findByWorldIdAndName(
                    parsedWorldId.toBaseWorldId().getId(), name);
            if (baseEntry.isPresent()) {
                WChest tombstone = WChest.builder()
                        .worldId(worldId)
                        .name(name)
                        .title(baseEntry.get().getTitle())
                        .type(baseEntry.get().getType())
                        .tombstone(true)
                        .build();
                tombstone.touchCreate();
                repository.save(tombstone);
                log.info("COW tombstone created for chest: worldId={}, name={}", worldId, name);
            }
        }
    }

    /**
     * Delete all chests for a world (used for instance cleanup).
     */
    @Transactional
    public void deleteByWorldId(String worldId) {
        repository.deleteByWorldId(worldId);
        log.info("Deleted all chests for worldId={}", worldId);
    }
}
