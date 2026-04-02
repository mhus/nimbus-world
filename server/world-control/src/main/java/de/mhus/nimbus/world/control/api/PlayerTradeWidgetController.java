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
import de.mhus.nimbus.world.shared.world.*;
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
 * Allows players to buy/sell items from NPC traders via a WProgress reference.
 * The WProgress (type "trade-access") contains trader configuration in progressData.
 */
@RestController
@RequestMapping("/control/player/trade-widget")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Trade Widget", description = "Trade with NPC merchants via progress reference")
public class PlayerTradeWidgetController extends BaseEditorController {

    private final WTraderService traderService;
    private final WChestService chestService;
    private final WProgressService progressService;
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

        // Resolve trader from progress
        var resolve = resolveTraderFromProgress(progressId, worldId, userId);
        if (resolve.error != null) return resolve.error;
        WTrader trader = resolve.trader;

        // Load character
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

        // Build response
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
     * Buy an item from the trader.
     */
    @PostMapping("/buy")
    @Operation(summary = "Buy item from trader")
    public ResponseEntity<?> buy(
            @RequestBody TradeItemRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (body == null || Strings.isBlank(body.progressId()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("progressId, itemId and amount (> 0) required");
        }

        var resolve = resolveTraderFromProgress(body.progressId(), worldId, userId);
        if (resolve.error != null) return resolve.error;
        WTrader trader = resolve.trader;

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) return notFound("Character not found");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        // Load item for price calculation
        Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, body.itemId());
        if (itemOpt.isEmpty()) return notFound("Item not found");

        long buyPrice = priceCalculator.calculateBuyPrice(itemOpt.get(), trader, character, worldId);
        long totalCost = buyPrice * body.amount();

        // Check shop chest has the item
        var shopChest = chestService.getForWrite(worldId, trader.getChestId());
        if (shopChest.isEmpty()) return notFound("Shop chest not found");

        ItemRef chestItem = findItemInChest(shopChest.get(), body.itemId());
        if (chestItem == null || chestItem.getAmount() < body.amount()) {
            return bad("Not enough items in shop");
        }

        // Take silver from player first
        if (!characterService.changeSilver(character.getId(), -totalCost)) {
            return bad("Not enough silver");
        }

        // Remove item from shop chest
        if (chestItem.getAmount() <= body.amount()) {
            chestService.removeItemAtomic(shopChest.get().getId(), body.itemId());
        } else {
            chestService.updateItemAmountAtomic(shopChest.get().getId(), body.itemId(), chestItem.getAmount() - body.amount());
        }

        // Add item to player backpack
        characterService.addBackpackItem(character.getId(), body.itemId(), body.amount());

        // Add silver to trader
        traderService.changeSilverAmount(trader.getId(), totalCost);

        log.info("Trade buy: player={} bought {}x {} for {} silver from trader={}",
                userId, body.amount(), body.itemId(), totalCost, trader.getEntityId());

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("totalCost", totalCost, "amount", body.amount()));
    }

    /**
     * Sell an item to the trader.
     */
    @PostMapping("/sell")
    @Operation(summary = "Sell item to trader")
    public ResponseEntity<?> sell(
            @RequestBody TradeItemRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (body == null || Strings.isBlank(body.progressId()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("progressId, itemId and amount (> 0) required");
        }

        var resolve = resolveTraderFromProgress(body.progressId(), worldId, userId);
        if (resolve.error != null) return resolve.error;
        WTrader trader = resolve.trader;

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) return notFound("Character not found");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        // Check backpack has the item
        PlayerBackpack backpack = character.getBackpack();
        Map<String, Integer> itemIds = backpack != null ? backpack.getItemIds() : null;
        if (itemIds == null || !itemIds.containsKey(body.itemId()) || itemIds.get(body.itemId()) < body.amount()) {
            return bad("Not enough items in backpack");
        }

        // Load item for price calculation
        Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, body.itemId());
        if (itemOpt.isEmpty()) return notFound("Item not found");

        long sellPrice = priceCalculator.calculateSellPrice(itemOpt.get(), trader, character, worldId);
        long totalRevenue = sellPrice * body.amount();

        // Check trader has enough silver
        if (trader.getSilverAmount() < totalRevenue) {
            return bad("Trader does not have enough silver");
        }

        // Take item from player first
        if (!characterService.removeBackpackItem(character.getId(), body.itemId(), body.amount())) {
            return bad("Failed to remove item from backpack");
        }

        // Add item to trader's pool chest (sold items go to pool, not shop)
        var poolChest = chestService.getForWrite(worldId, trader.getPoolChestId());
        if (poolChest.isPresent()) {
            ItemRef existing = findItemInChest(poolChest.get(), body.itemId());
            if (existing != null) {
                chestService.incItemAmountAtomic(poolChest.get().getId(), body.itemId(), body.amount());
            } else {
                chestService.addItemAtomic(poolChest.get().getId(), ItemRef.builder()
                        .itemId(body.itemId())
                        .amount(body.amount())
                        .build());
            }
        }

        // Give silver to player
        characterService.changeSilver(character.getId(), totalRevenue);

        // Take silver from trader
        traderService.changeSilverAmount(trader.getId(), -totalRevenue);

        log.info("Trade sell: player={} sold {}x {} for {} silver to trader={}",
                userId, body.amount(), body.itemId(), totalRevenue, trader.getEntityId());

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("totalRevenue", totalRevenue, "amount", body.amount()));
    }

    /**
     * Exchange gold for silver.
     */
    @PostMapping("/exchange-gold")
    @Operation(summary = "Exchange gold for silver at the trader")
    public ResponseEntity<?> exchangeGold(
            @RequestBody GoldExchangeRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }
        if (body == null || Strings.isBlank(body.progressId()) || body.goldAmount() <= 0) {
            return bad("progressId and goldAmount (> 0) required");
        }

        var resolve = resolveTraderFromProgress(body.progressId(), worldId, userId);
        if (resolve.error != null) return resolve.error;
        WTrader trader = resolve.trader;

        long silverAmount = priceCalculator.calculateGoldToSilver(body.goldAmount(), trader);

        // Take gold from user first
        if (!userService.changeGold(userId, -body.goldAmount())) {
            return bad("Not enough gold");
        }

        // Give silver to character
        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            // Rollback gold
            userService.changeGold(userId, body.goldAmount());
            return notFound("Character not found");
        }
        characterService.changeSilver(character.getId(), silverAmount);

        log.info("Gold exchange: player={} exchanged {} gold for {} silver at trader={}",
                userId, body.goldAmount(), silverAmount, trader.getEntityId());

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("goldSpent", body.goldAmount(), "silverReceived", silverAmount));
    }

    // --- Helper methods ---

    private TradeResolveResult resolveTraderFromProgress(String progressId, String worldId, String userId) {
        Optional<WProgress> progressOpt = progressService.findByProgressId(progressId);
        if (progressOpt.isEmpty()) {
            return TradeResolveResult.ofError(notFound("Progress not found"));
        }
        WProgress progress = progressOpt.get();

        if (!worldId.equals(progress.getWorldId())) {
            return TradeResolveResult.ofError(bad("Access denied"));
        }
        if (!userId.equals(progress.getPlayerId())) {
            return TradeResolveResult.ofError(bad("Access denied"));
        }
        if (!"trade-access".equals(progress.getType())) {
            return TradeResolveResult.ofError(bad("Invalid progress type"));
        }

        String traderEntityId = progress.getQuest();
        if (Strings.isBlank(traderEntityId)) {
            return TradeResolveResult.ofError(bad("Progress does not reference a trader"));
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
            if (item.getItemId().equals(itemId)) return item;
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

    record TradeItemRequest(String progressId, String itemId, int amount) {}
    record GoldExchangeRequest(String progressId, long goldAmount) {}

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
