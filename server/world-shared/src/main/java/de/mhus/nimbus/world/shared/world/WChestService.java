package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemRef;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing WChest entities.
 * Provides business logic for chest operations including creation, retrieval, and item management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WChestService {

    private final WChestRepository repository;

    /**
     * Find chest by worldId and name.
     * If not found with the given worldId, tries with the region collection worldId (@region:regionId).
     */
    @Transactional(readOnly = true)
    public Optional<WChest> getByWorldIdAndName(String worldId, String name) {
        var result = repository.findByWorldIdAndName(worldId, name);
        if (result.isPresent()) {
            return result;
        }
        // Fallback: try region collection worldId
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId != null && !parsedWorldId.isCollection()) {
            String regionWorldId = parsedWorldId.toRegionCollection().getId();
            return repository.findByWorldIdAndName(regionWorldId, name);
        }
        return result;
    }

    /**
     * Get or create the user's bank chest in a world.
     * Tries multiple worldId formats (@region:regionId, plain worldId) and userId/playerId combinations.
     * If no bank chest exists, one is automatically created.
     *
     * @param worldId worldId from session
     * @param playerId PlayerId (@userId:characterId)
     * @return the user's bank chest, never null
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
                var result = repository.findFirstByWorldIdAndPlayerIdAndTypeAndBank(wId, uId, WChest.ChestType.PLAYER, true);
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
                .type(WChest.ChestType.PLAYER)
                .bank(true)
                .capacity(10)
                .build();
        chest.touchCreate();
        return repository.save(chest);
    }

    /**
     * Find all chests for a specific world.
     */
    @Transactional(readOnly = true)
    public List<WChest> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

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

    /**
     * Create a new chest.
     *
     * @param worldId World identifier (optional)
     * @param name Internal name/identifier
     * @param title Display name (optional)
     * @param description Description
     * @param playerId Player identifier (optional, for player-specific chests)
     * @param type Chest type
     * @return Created chest entity
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
     *
     * @param chestId Chest ID
     * @param updater Consumer to apply updates
     * @return Updated chest if found
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
     * Use updateItemAmount() to change existing item amounts.
     *
     * @param chestId Chest ID
     * @param itemRef ItemRef to add
     * @return Updated chest if found
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
     *
     * @param chestId Chest ID
     * @param itemId Item ID to update
     * @param newAmount New amount (must be > 0)
     * @return Updated chest if found
     */
    @Transactional
    public Optional<WChest> updateItemAmount(String chestId, String itemId, int newAmount) {
        if (newAmount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0. Use removeItem() to delete.");
        }

        return updateChest(chestId, chest -> {
            // Find item by itemId
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
                        .itemId(existing.getItemId())
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
     *
     * @param chestId Chest ID
     * @param itemId Item ID to remove
     * @return Updated chest if found
     */
    @Transactional
    public Optional<WChest> removeItem(String chestId, String itemId) {
        return updateChest(chestId, chest -> {
            log.debug("Removing ItemRef from chest: chestId={}, itemId={}, currentItems={}",
                    chestId, itemId, chest.getItems().size());

            // Find and remove only the first matching item
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

    /**
     * Save a chest (updates modification timestamp).
     *
     * @param chest Chest to save
     * @return Saved chest
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
     *
     * @param chestId Chest ID
     * @return true if chest was deleted
     */
    @Transactional
    public boolean deleteChestById(String chestId) {
        return repository.findById(chestId).map(chest -> {
            repository.delete(chest);
            log.debug("Chest deleted: id={}", chestId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public void deleteChest(String worldId, String name) {
        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isSharedCollection() || parsedWorldId.isPublicRegion()) {
            throw new IllegalArgumentException("WChest can't be in shared or public region");
        }
        getByWorldIdAndName(worldId, name).ifPresent(chest  ->{
            repository.delete(chest);
            log.debug("Chest deleted: worldId={}, name={}", worldId, name);
        });
    }
}
