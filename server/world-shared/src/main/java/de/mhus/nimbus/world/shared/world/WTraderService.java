package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemRef;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing WTrader entities and trade operations.
 * Handles CRUD, atomic silver changes, and pool synchronization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WTraderService {

    private final WTraderRepository repository;
    private final WChestService chestService;
    private final WItemService itemService;
    private final MongoTemplate mongoTemplate;

    // ===== Read operations =====

    @Transactional(readOnly = true)
    public Optional<WTrader> findByWorldIdAndEntityId(String worldId, String entityId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        String lookupWorldId = (parsedWorldId != null && parsedWorldId.isInstance())
                ? parsedWorldId.toBaseWorldId().getId()
                : worldId;
        return repository.findByWorldIdAndEntityId(lookupWorldId, entityId);
    }

    @Transactional(readOnly = true)
    public List<WTrader> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

    @Transactional(readOnly = true)
    public List<WTrader> findByWorldIdAndTraderType(String worldId, TraderType traderType) {
        return repository.findByWorldIdAndTraderType(worldId, traderType);
    }

    // ===== Create / Update / Delete =====

    @Transactional
    public WTrader save(WTrader trader) {
        if (trader.getCreatedAt() == null) {
            trader.touchCreate();
        } else {
            trader.touchUpdate();
        }
        return repository.save(trader);
    }

    @Transactional
    public boolean delete(String worldId, String entityId) {
        return repository.findByWorldIdAndEntityId(worldId, entityId)
                .map(trader -> {
                    repository.delete(trader);
                    log.debug("Trader deleted: worldId={}, entityId={}", worldId, entityId);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void deleteByWorldId(String worldId) {
        repository.deleteByWorldId(worldId);
        log.info("Deleted all traders for worldId={}", worldId);
    }

    /**
     * Delete all traders belonging to the given world and return the number of removed documents.
     *
     * @param worldId the world whose traders should be removed
     * @return number of deleted trader documents
     */
    @Transactional
    public int deleteAllByWorldId(String worldId) {
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WTrader.class
        );
        long deleted = result.getDeletedCount();
        log.info("Deleted {} traders for worldId={}", deleted, worldId);
        return (int) deleted;
    }

    /**
     * Return the distinct worldIds that currently have traders stored.
     *
     * @return distinct worldIds
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WTrader.class, String.class);
    }

    /**
     * Duplicate all traders from the source world to the target world.
     * Each trader is copied field-by-field with the target worldId and a fresh creation timestamp.
     * The target world must already exist.
     *
     * @param sourceWorldId world to copy from
     * @param targetWorldId world to copy to
     * @return number of duplicated traders
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WTrader> sourceTraders = repository.findByWorldId(sourceWorldId);
        log.info("Found {} traders in source world {}", sourceTraders.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WTrader source : sourceTraders) {
            WTrader target = WTrader.builder()
                    .worldId(targetWorldId)
                    .entityId(source.getEntityId())
                    .traderType(source.getTraderType())
                    .categories(source.getCategories() != null ? new ArrayList<>(source.getCategories()) : new ArrayList<>())
                    .personalityModifier(source.getPersonalityModifier())
                    .silverAmount(source.getSilverAmount())
                    .chestId(source.getChestId())
                    .poolChestId(source.getPoolChestId())
                    .questItems(source.getQuestItems() != null ? new ArrayList<>(source.getQuestItems()) : new ArrayList<>())
                    .maxDisplayItems(source.getMaxDisplayItems())
                    .goldExchangeRate(source.getGoldExchangeRate())
                    .trainableSkills(source.getTrainableSkills() != null ? new ArrayList<>(source.getTrainableSkills()) : new ArrayList<>())
                    .maxSkillPoints(source.getMaxSkillPoints())
                    .costPerSkillPoint(source.getCostPerSkillPoint())
                    .repairTypes(source.getRepairTypes() != null ? new ArrayList<>(source.getRepairTypes()) : new ArrayList<>())
                    .repairCostPerPoint(source.getRepairCostPerPoint())
                    .poolSyncIntervalSeconds(source.getPoolSyncIntervalSeconds())
                    .enabled(source.isEnabled())
                    .build();

            target.touchCreate();
            repository.save(target);
            duplicatedCount++;
        }

        log.info("Duplicated {} traders from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
        return duplicatedCount;
    }

    // ===== Atomic silver operations =====

    /**
     * Atomically change a trader's silver amount.
     * For negative amounts, ensures the trader has enough silver (guard condition).
     *
     * @param traderId MongoDB document id
     * @param amount positive to add, negative to deduct
     * @return true if the update was applied
     */
    public boolean changeSilverAmount(String traderId, long amount) {
        if (amount == 0) return true;

        Query query;
        if (amount < 0) {
            query = new Query(Criteria.where("id").is(traderId)
                    .and("silverAmount").gte(-amount));
        } else {
            query = new Query(Criteria.where("id").is(traderId));
        }

        Update update = new Update()
                .inc("silverAmount", amount)
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WTrader.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        if (amount < 0) {
            log.warn("changeSilverAmount failed: traderId={}, amount={} - insufficient silver", traderId, amount);
        }
        return false;
    }

    // ===== Pool synchronization =====

    /**
     * Synchronize the trader's pool chest with the shop chest if the sync interval has elapsed.
     * - Items outside the trader's portfolio are moved from shop to pool.
     * - Items inside the trader's portfolio are moved from pool to shop (up to maxDisplayItems).
     *
     * @param trader the trader to sync
     * @return true if sync was performed
     */
    @Transactional
    public boolean syncPoolIfDue(WTrader trader) {
        if (trader.getLastPoolSync() != null) {
            long elapsed = Instant.now().getEpochSecond() - trader.getLastPoolSync().getEpochSecond();
            if (elapsed < trader.getPoolSyncIntervalSeconds()) {
                return false;
            }
        }

        String worldId = trader.getWorldId();
        var shopChest = chestService.getByWorldIdAndName(worldId, trader.getChestId());
        var poolChest = chestService.getByWorldIdAndName(worldId, trader.getPoolChestId());

        if (shopChest.isEmpty() || poolChest.isEmpty()) {
            log.warn("Pool sync skipped: shop or pool chest not found for trader entityId={}", trader.getEntityId());
            return false;
        }

        WChest shop = shopChest.get();
        WChest pool = poolChest.get();
        List<String> portfolio = trader.getCategories() != null ? trader.getCategories() : List.of();
        int maxDisplay = trader.getMaxDisplayItems() > 0 ? trader.getMaxDisplayItems() : 12;

        var parsedWorldId = WorldId.of(worldId).orElse(null);

        // 1. Move items outside portfolio from shop → pool
        if (!portfolio.isEmpty() && shop.getItems() != null) {
            List<String> toMoveToPool = new ArrayList<>();
            for (ItemRef ref : shop.getItems()) {
                String itemType = resolveItemType(parsedWorldId, ref.getItemId());
                if (itemType != null && !portfolio.contains(itemType)) {
                    toMoveToPool.add(ref.getItemId());
                }
            }
            for (String itemId : toMoveToPool) {
                moveItem(shop, pool, itemId);
            }
        }

        // 2. Move items inside portfolio from pool → shop (up to maxDisplayItems)
        int shopCount = shop.getItems() != null ? shop.getItems().size() : 0;
        if (pool.getItems() != null && shopCount < maxDisplay) {
            // Shuffle pool items for variety
            List<ItemRef> poolItems = new ArrayList<>(pool.getItems());
            Collections.shuffle(poolItems);

            for (ItemRef ref : poolItems) {
                if (shopCount >= maxDisplay) break;
                String itemType = resolveItemType(parsedWorldId, ref.getItemId());
                // Prefer items that match portfolio, or move any if portfolio is empty
                if (portfolio.isEmpty() || (itemType != null && portfolio.contains(itemType))) {
                    moveItem(pool, shop, ref.getItemId());
                    shopCount++;
                }
            }
        }

        // Update last sync time
        Query query = new Query(Criteria.where("id").is(trader.getId()));
        Update update = new Update()
                .set("lastPoolSync", Instant.now())
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, WTrader.class);

        log.info("Pool synced for trader entityId={}: shop={} items, pool={} items",
                trader.getEntityId(),
                shop.getItems() != null ? shop.getItems().size() : 0,
                pool.getItems() != null ? pool.getItems().size() : 0);
        return true;
    }

    /**
     * Move an item from source chest to target chest (full amount).
     */
    private void moveItem(WChest source, WChest target, String itemId) {
        ItemRef sourceRef = null;
        if (source.getItems() != null) {
            for (ItemRef ref : source.getItems()) {
                if (ref.getItemId().equals(itemId)) {
                    sourceRef = ref;
                    break;
                }
            }
        }
        if (sourceRef == null) return;

        // Check if target already has this item
        ItemRef targetRef = null;
        if (target.getItems() != null) {
            for (ItemRef ref : target.getItems()) {
                if (ref.getItemId().equals(itemId)) {
                    targetRef = ref;
                    break;
                }
            }
        }

        // Remove from source
        chestService.removeItemAtomic(source.getId(), itemId);
        source.getItems().removeIf(r -> r.getItemId().equals(itemId));

        // Add to target
        if (targetRef != null) {
            chestService.incItemAmountAtomic(target.getId(), itemId, sourceRef.getAmount());
            targetRef = ItemRef.builder()
                    .itemId(targetRef.getItemId())
                    .name(targetRef.getName())
                    .texture(targetRef.getTexture())
                    .amount(targetRef.getAmount() + sourceRef.getAmount())
                    .build();
        } else {
            chestService.addItemAtomic(target.getId(), sourceRef);
            if (target.getItems() == null) {
                // Should not happen with builder default, but be safe
                return;
            }
            target.getItems().add(sourceRef);
        }

        log.debug("Moved item {} (x{}) from chest {} to {}", itemId, sourceRef.getAmount(), source.getName(), target.getName());
    }

    /**
     * Resolve the item type (category) for a given itemId.
     * Returns Item.type (e.g., "food", "weapon") or null if not found.
     */
    private String resolveItemType(WorldId worldId, String itemId) {
        if (worldId == null) return null;
        try {
            return itemService.findByItemId(worldId, itemId)
                    .map(item -> item.getPublicData() != null ? item.getPublicData().getType() : null)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve item type for {}: {}", itemId, e.getMessage());
            return null;
        }
    }
}
