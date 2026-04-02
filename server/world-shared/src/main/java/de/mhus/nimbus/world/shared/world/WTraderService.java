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

        // TODO implement pool cycling logic:
        // 1. Move items outside portfolio from shop → pool
        // 2. Move items inside portfolio from pool → shop (up to maxDisplayItems)
        // 3. Prefer items that match trader categories

        // Update last sync time
        Query query = new Query(Criteria.where("id").is(trader.getId()));
        Update update = new Update()
                .set("lastPoolSync", Instant.now())
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, WTrader.class);

        log.info("Pool synced for trader entityId={}", trader.getEntityId());
        return true;
    }
}
