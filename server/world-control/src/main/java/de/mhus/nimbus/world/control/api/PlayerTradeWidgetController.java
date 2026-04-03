package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.generated.types.ItemRef;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WChestService;
import de.mhus.nimbus.world.shared.world.WChest;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WLease;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import de.mhus.nimbus.world.shared.world.WTrader;
import de.mhus.nimbus.world.shared.world.WTraderService;
import de.mhus.nimbus.world.shared.world.TradePriceCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for trade widget operations.
 * Players prepare a trade (buy/sell cart) in the UI and submit it as one atomic action via /apply.
 */
@RestController
@RequestMapping("/control/player/trade-widget")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Trade Widget", description = "Trade with NPC merchants via progress reference")
public class PlayerTradeWidgetController extends BaseEditorController {

    private final WTraderService traderService;
    private final WChestService chestService;
    private final WLeaseService leaseService;
    private final RCharacterService characterService;
    private final RUserService userService;
    private final WItemService wItemService;
    private final TradePriceCalculator priceCalculator;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;

    /**
     * Get trade shop view: trader items with buy prices, backpack items with sell prices.
     */
    @GetMapping
    @Operation(summary = "Get trade shop items and prices via progress reference")
    public ResponseEntity<?> getTradeShop(
            @RequestParam String progressId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (Strings.isBlank(progressId)) {
            return bad("progressId required");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        var resolve = resolveTraderFromLease(progressId, worldId, userId);
        if (resolve.error != null) return resolve.error;
        WTrader trader = resolve.trader;

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        // Load shop chest items
        var shopChest = chestService.getByWorldIdAndName(worldId, trader.getChestId());
        List<Map<String, Object>> shopItems = new ArrayList<>();
        if (shopChest.isPresent()) {
            List<ItemRef> allItems = shopChest.get().getItems();
            List<ItemRef> displayItems = selectDisplayItems(allItems, trader.getMaxDisplayItems());
            shopItems = enrichTraderItems(parsedWorldId, displayItems, trader, character, worldId);
        }

        // Load backpack items with sell prices
        PlayerBackpack backpack = character.getBackpack();
        Map<String, Integer> itemIds = backpack != null ? backpack.getItemIds() : null;
        List<Map<String, Object>> backpackItems = enrichBackpackItemsWithSellPrice(
                parsedWorldId, itemIds, trader, character, worldId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("worldId", worldId);
        result.put("traderEntityId", trader.getEntityId());
        result.put("traderType", trader.getTraderType().name());
        result.put("categories", trader.getCategories());
        result.put("goldExchangeRate", trader.getGoldExchangeRate());
        result.put("shopItems", shopItems);
        result.put("backpackItems", backpackItems);
        result.put("silver", character.getSilver());
        result.put("gold", userService.getByUsername(userId).map(u -> u.getGold()).orElse(0L));

        return ResponseEntity.ok(result);
    }

    /**
     * Apply a complete trade: buy items, sell items, exchange gold — all in one atomic action.
     */
    @PostMapping("/apply")
    @Operation(summary = "Apply a complete trade (buy + sell + gold exchange)")
    public ResponseEntity<?> apply(
            @RequestBody TradeApplyRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (body == null || Strings.isBlank(body.progressId())) {
            return bad("progressId required");
        }

        var resolve = resolveTraderFromLease(body.progressId(), worldId, userId);
        if (resolve.error != null) return resolve.error;
        WTrader trader = resolve.trader;

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) return notFound("Character not found");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        // Load shop chest (read-only for validation, COW-write only for buy operations)
        var shopChestOpt = chestService.getByWorldIdAndName(worldId, trader.getChestId());
        if (shopChestOpt.isEmpty()) return notFound("Shop chest not found");
        WChest shopChest = shopChestOpt.get();

        // Calculate totals
        long totalBuyCost = 0;
        long totalSellRevenue = 0;
        long goldExchangeSilver = 0;

        // Validate buys
        List<TradeItem> buys = body.buys() != null ? body.buys() : List.of();
        for (TradeItem buy : buys) {
            if (buy.amount() <= 0) return bad("Buy amount must be > 0");
            ItemRef chestItem = findItemInChest(shopChest, buy.itemId());
            if (chestItem == null || chestItem.getAmount() < buy.amount()) {
                return bad("Not enough items in shop: " + buy.itemId());
            }
            Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, buy.itemId());
            if (itemOpt.isEmpty()) return bad("Item not found: " + buy.itemId());
            long price = priceCalculator.calculateBuyPrice(itemOpt.get(), trader, character, worldId);
            totalBuyCost += price * buy.amount();
        }

        // Validate sells
        List<TradeItem> sells = body.sells() != null ? body.sells() : List.of();
        PlayerBackpack backpack = character.getBackpack();
        Map<String, Integer> backpackItems = backpack != null ? backpack.getItemIds() : Map.of();
        for (TradeItem sell : sells) {
            if (sell.amount() <= 0) return bad("Sell amount must be > 0");
            int available = backpackItems.getOrDefault(sell.itemId(), 0);
            if (available < sell.amount()) {
                return bad("Not enough items in backpack: " + sell.itemId());
            }
            Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, sell.itemId());
            if (itemOpt.isEmpty()) return bad("Item not found: " + sell.itemId());
            long price = priceCalculator.calculateSellPrice(itemOpt.get(), trader, character, worldId);
            totalSellRevenue += price * sell.amount();
        }

        // Validate gold exchange
        long goldAmount = body.goldExchange() != null ? body.goldExchange() : 0;
        if (goldAmount > 0) {
            goldExchangeSilver = priceCalculator.calculateGoldToSilver(goldAmount, trader);
        }

        // Check player can afford (net silver change)
        long netSilverChange = totalSellRevenue + goldExchangeSilver - totalBuyCost;
        if (netSilverChange < 0 && character.getSilver() < -netSilverChange) {
            return bad("Not enough silver");
        }

        // Check trader can afford sells
        if (totalSellRevenue > 0 && trader.getSilverAmount() < totalSellRevenue) {
            return bad("Trader does not have enough silver");
        }

        // Check gold
        if (goldAmount > 0) {
            long userGold = userService.getByUsername(userId).map(u -> u.getGold()).orElse(0L);
            if (userGold < goldAmount) {
                return bad("Not enough gold");
            }
        }

        // === Phase 1: Take resources (can fail — validates availability) ===

        // 1a. Take gold from user
        if (goldAmount > 0) {
            if (!userService.changeGold(userId, -goldAmount)) {
                return bad("Not enough gold");
            }
        }

        // 1b. Take silver from player
        if (totalBuyCost > 0) {
            if (!characterService.changeSilver(character.getId(), -totalBuyCost)) {
                if (goldAmount > 0) userService.changeGold(userId, goldAmount);
                return bad("Not enough silver");
            }
        }

        // 1c. Take silver from trader (for sells)
        if (totalSellRevenue > 0) {
            if (!traderService.changeSilverAmount(trader.getId(), -totalSellRevenue)) {
                // Rollback player silver and gold
                if (totalBuyCost > 0) characterService.changeSilver(character.getId(), totalBuyCost);
                if (goldAmount > 0) userService.changeGold(userId, goldAmount);
                return bad("Trader does not have enough silver");
            }
        }

        // 1d. Take items from player backpack (sells)
        for (TradeItem sell : sells) {
            if (!characterService.removeBackpackItem(character.getId(), sell.itemId(), sell.amount())) {
                log.error("Trade partial failure: could not remove backpack item {}x{} for player={}",
                        sell.itemId(), sell.amount(), userId);
            }
        }

        // 1e. Remove items from shop chest (buys)
        if (!buys.isEmpty()) {
            WChest writeShopChest = chestService.ensureCowCopy(worldId, shopChest);
            for (TradeItem buy : buys) {
                ItemRef chestItem = findItemInChest(writeShopChest, buy.itemId());
                if (chestItem != null) {
                    if (chestItem.getAmount() <= buy.amount()) {
                        chestService.removeItemAtomic(writeShopChest.getId(), buy.itemId());
                    } else {
                        chestService.updateItemAmountAtomic(writeShopChest.getId(), buy.itemId(), chestItem.getAmount() - buy.amount());
                    }
                }
            }
        }

        // === Phase 2: Give resources (should not fail) ===

        // 2a. Give items to player backpack (buys)
        for (TradeItem buy : buys) {
            characterService.addBackpackItem(character.getId(), buy.itemId(), buy.amount());
        }

        // 2b. Add sold items to pool chest (no COW — merchant pool belongs to base world)
        if (!sells.isEmpty() && !Strings.isBlank(trader.getPoolChestId())) {
            var poolChestOpt = chestService.getByWorldIdAndName(worldId, trader.getPoolChestId());
            if (poolChestOpt.isPresent()) {
                WChest poolChest = poolChestOpt.get();
                for (TradeItem sell : sells) {
                    ItemRef existing = findItemInChest(poolChest, sell.itemId());
                    if (existing != null) {
                        chestService.incItemAmountAtomic(poolChest.getId(), sell.itemId(), sell.amount());
                    } else {
                        chestService.addItemAtomic(poolChest.getId(), ItemRef.builder()
                                .itemId(sell.itemId())
                                .amount(sell.amount())
                                .build());
                    }
                }
            }
        }

        // 2c. Give silver to player (sells + gold exchange)
        long silverToGive = totalSellRevenue + goldExchangeSilver;
        if (silverToGive > 0) {
            characterService.changeSilver(character.getId(), silverToGive);
        }

        // 2d. Give silver to trader (buys)
        if (totalBuyCost > 0) {
            traderService.changeSilverAmount(trader.getId(), totalBuyCost);
        }

        log.info("Trade applied: player={}, buyCost={}, sellRevenue={}, goldExchange={}, trader={}",
                userId, totalBuyCost, totalSellRevenue, goldAmount, trader.getEntityId());

        notifyPlayer(worldId, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBuyCost", totalBuyCost);
        result.put("totalSellRevenue", totalSellRevenue);
        result.put("goldExchanged", goldAmount);
        result.put("silverFromGold", goldExchangeSilver);
        return ResponseEntity.ok(result);
    }

    // --- Helper methods ---

    private TradeResolveResult resolveTraderFromLease(String leaseId, String worldId, String userId) {
        var leaseOpt = leaseService.validate(leaseId, worldId, userId, "trade-access");
        if (leaseOpt.isEmpty()) {
            return TradeResolveResult.ofError(notFound("Lease not found or access denied"));
        }
        WLease lease = leaseOpt.get();

        String traderEntityId = lease.getResourceId();
        if (Strings.isBlank(traderEntityId)) {
            return TradeResolveResult.ofError(bad("Lease does not reference a trader"));
        }

        Optional<WTrader> traderOpt = traderService.findByWorldIdAndEntityId(worldId, traderEntityId);
        if (traderOpt.isEmpty()) {
            return TradeResolveResult.ofError(notFound("Trader not found"));
        }

        return TradeResolveResult.ofTrader(traderOpt.get());
    }

    private List<ItemRef> selectDisplayItems(List<ItemRef> allItems, int maxDisplay) {
        if (allItems == null || allItems.isEmpty()) return List.of();
        if (maxDisplay <= 0 || allItems.size() <= maxDisplay) return allItems;

        List<ItemRef> shuffled = new ArrayList<>(allItems);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, maxDisplay);
    }

    private ItemRef findItemInChest(WChest chest, String itemId) {
        if (chest.getItems() == null) return null;
        for (ItemRef item : chest.getItems()) {
            if (item.getName().equals(itemId)) return item;
        }
        return null;
    }

    private List<Map<String, Object>> enrichTraderItems(WorldId parsedWorldId, List<ItemRef> items,
                                                         WTrader trader, RCharacter character, String worldId) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        if (items == null) return enriched;

        for (ItemRef ref : items) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("itemId", ref.getItemId());
            info.put("amount", ref.getAmount());

            Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, ref.getItemId());
            if (itemOpt.isPresent()) {
                WItem wItem = itemOpt.get();
                Item publicData = wItem.getPublicData();
                if (publicData != null) {
                    info.put("name", publicData.getName());
                    info.put("itemType", publicData.getItemType());
                    info.put("texture", publicData.getTexture());
                    info.put("description", publicData.getDescription());
                }
                info.put("buyPrice", priceCalculator.calculateBuyPrice(wItem, trader, character, worldId));
            } else {
                info.put("name", ref.getName() != null ? ref.getName() : ref.getItemId());
                info.put("buyPrice", 0);
            }

            enriched.add(info);
        }
        return enriched;
    }

    private List<Map<String, Object>> enrichBackpackItemsWithSellPrice(WorldId parsedWorldId,
                                                                        Map<String, Integer> itemIds, WTrader trader,
                                                                        RCharacter character, String worldId) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (itemIds == null) return items;

        for (var entry : itemIds.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue();

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("itemId", itemId);
            info.put("count", count);

            Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, itemId);
            if (itemOpt.isPresent()) {
                WItem wItem = itemOpt.get();
                Item publicData = wItem.getPublicData();
                if (publicData != null) {
                    info.put("name", publicData.getName());
                    info.put("itemType", publicData.getItemType());
                    info.put("texture", publicData.getTexture());
                    info.put("description", publicData.getDescription());
                }
                info.put("sellPrice", priceCalculator.calculateSellPrice(wItem, trader, character, worldId));
            } else {
                info.put("name", itemId);
                info.put("sellPrice", 0);
            }

            items.add(info);
        }
        return items;
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return null;
        String regionId = parsedWorldId.getRegionId();
        return characterService.getCharacter(userId, regionId, characterId).orElse(null);
    }

    private void notifyPlayer(String worldId, HttpServletRequest request) {
        String sessionId = (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);
        if (Strings.isBlank(sessionId)) return;
        var wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) return;
        worldClientService.sendPlayerCommand(worldId, sessionId, wSession.get().getPlayerUrl(),
                "BackpackModified", List.of(), null);
    }

    // --- DTOs ---

    record TradeItem(String itemId, int amount) {}
    record TradeApplyRequest(
            String progressId,
            List<TradeItem> buys,
            List<TradeItem> sells,
            Long goldExchange
    ) {}

    private static class TradeResolveResult {
        final WTrader trader;
        final ResponseEntity<?> error;

        private TradeResolveResult(WTrader trader, ResponseEntity<?> error) {
            this.trader = trader;
            this.error = error;
        }

        static TradeResolveResult ofTrader(WTrader trader) {
            return new TradeResolveResult(trader, null);
        }

        static TradeResolveResult ofError(ResponseEntity<?> error) {
            return new TradeResolveResult(null, error);
        }
    }
}
