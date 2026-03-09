package de.mhus.nimbus.world.player.gameplay.adventure.handler;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.BasicGameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles inventory management: caching backpack/wearing/shortcut items,
 * resolving shortcut item IDs and actions, sending item use feedback,
 * and creating synthetic fist/block items.
 */
@Slf4j
public class InventoryHandler {

    private final AdventureGameplay gameplay;

    public InventoryHandler(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    /**
     * Reload character data from DB and refresh inventory cache in AdventureData.
     * Loads all items referenced by backpack, wearings, and shortcuts.
     */
    public void refreshInventoryCache(PlayerSession session, AdventureData data) {
        try {
            String entityId = session.getEntityId();
            if (entityId == null || session.getWorldId() == null) return;

            PlayerId playerId = PlayerId.of(entityId).orElse(null);
            if (playerId == null) return;

            String regionId = session.getWorldId().getRegionId();
            var freshData = gameplay.getPlayerService().getPlayer(playerId, session.getClientType(), regionId);
            if (freshData.isEmpty()) return;

            // Update session with fresh player data
            session.setPlayer(freshData.get());

            var character = freshData.get().character();
            PlayerBackpack backpack = character.getBackpack();
            PlayerInfo playerInfo = character.getPublicData();

            // Cache raw data
            data.setCachedBackpack(backpack != null ? backpack : new PlayerBackpack());
            data.setCachedShortcuts(playerInfo != null && playerInfo.getShortcuts() != null
                    ? playerInfo.getShortcuts() : Map.of());

            // Reuse already cached items (items don't change during a session)
            Map<String, WItem> existingItems = data.getCachedItems();
            Map<String, WItem> items = existingItems != null ? new HashMap<>(existingItems) : new HashMap<>();
            var worldId = session.getWorldId();

            // Backpack items
            if (backpack != null && backpack.getItemIds() != null) {
                for (String itemId : backpack.getItemIds().keySet()) {
                    if (!items.containsKey(itemId)) {
                        gameplay.getItemService().findByItemId(worldId, itemId).ifPresent(item -> items.put(itemId, item));
                    }
                }
            }

            // Wearing items
            if (backpack != null && backpack.getWearingItemIds() != null) {
                for (String itemId : backpack.getWearingItemIds().values()) {
                    if (itemId != null && !items.containsKey(itemId)) {
                        gameplay.getItemService().findByItemId(worldId, itemId).ifPresent(item -> items.put(itemId, item));
                    }
                }
            }

            // Shortcut-referenced items
            if (playerInfo != null && playerInfo.getShortcuts() != null) {
                for (ShortcutDefinition shortcut : playerInfo.getShortcuts().values()) {
                    if (shortcut != null && shortcut.getItemId() != null && !items.containsKey(shortcut.getItemId())) {
                        gameplay.getItemService().findByItemId(worldId, shortcut.getItemId())
                                .ifPresent(item -> items.put(shortcut.getItemId(), item));
                    }
                }
            }

            // Add synthetic fist/block items (always available)
            items.put(BasicGameplay.FIST_ITEM_ID, createSyntheticFistItem(data));
            items.put(BasicGameplay.BLOCK_ITEM_ID, createSyntheticBlockItem(data));

            data.setCachedItems(items);

            // Recalculate passive stats from wearings + skills
            gameplay.getStatsHandler().recalculatePassiveStats(data);

            log.debug("Refreshed inventory cache for player {}: backpack={}, wearings={}, shortcuts={}, items={}",
                    entityId,
                    backpack != null && backpack.getItemIds() != null ? backpack.getItemIds().size() : 0,
                    backpack != null && backpack.getWearingItemIds() != null ? backpack.getWearingItemIds().size() : 0,
                    data.getCachedShortcuts().size(),
                    items.size());
        } catch (Exception e) {
            log.error("Failed to refresh inventory cache for session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Refresh inventory cache when backpack is modified.
     */
    public void onBackpackModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshInventoryCache(session, data);
        }
    }

    /**
     * Refresh inventory cache when wearings are modified.
     */
    public void onWearingModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshInventoryCache(session, data);
        }
    }

    /**
     * Refresh inventory cache when shortcuts are modified.
     */
    public void onShortcutModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshInventoryCache(session, data);
        }
    }

    /**
     * Resolve the itemId from a shortcut key using cached data.
     * Handles shortcut types: 'use' -> shortcutDef.itemId, hand types -> wearing slot.
     * Returns null for 'interact', 'none', 'cmd' or if no item is found.
     */
    public String resolveShortcutItemId(PlayerSession session, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return null;
        if (shortcutKey == null) return null;

        var shortcuts = data.getCachedShortcuts();
        if (shortcuts == null) return null;

        var shortcutDef = shortcuts.get(shortcutKey);
        if (shortcutDef == null) return null;

        String type = shortcutDef.getType();
        if (type == null) return null;

        return switch (type) {
            case "use" -> shortcutDef.getItemId();
            case "left_hand_1" -> getWearingItemId(data, WEARABLE_SLOT.LEFT_HAND_1);
            case "right_hand_1" -> getWearingItemId(data, WEARABLE_SLOT.RIGHT_HAND_1);
            case "left_hand_2" -> getWearingItemId(data, WEARABLE_SLOT.LEFT_HAND_2);
            case "right_hand_2" -> getWearingItemId(data, WEARABLE_SLOT.RIGHT_HAND_2);
            case "fist" -> BasicGameplay.FIST_ITEM_ID;
            case "block" -> BasicGameplay.BLOCK_ITEM_ID;
            case "interact" -> BasicGameplay.SHORTCUT_INTERACT_ACTION;
            default -> null; // 'none', 'cmd' -> no item
        };
    }

    /**
     * Resolve the item's action from the shortcut key using cached data.
     * Falls back to loading from DB if item is not in cache.
     */
    public String resolveShortcutItemAction(PlayerSession session, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) {
            return null;
        }

        String itemId = resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return null;
        if (itemId.equals(BasicGameplay.SHORTCUT_INTERACT_ACTION)) {
            return BasicGameplay.SHORTCUT_INTERACT_ACTION;
        }

        var cachedItems = data.getCachedItems();
        if (cachedItems != null) {
            WItem item = cachedItems.get(itemId);
            if (item != null && item.getServer() != null) {
                return item.getServer().get("action");
            }
        }

        // Fallback: item not in cache, load from DB
        WItem item = gameplay.getItemService().findByItemId(session.getWorldId(), itemId).orElse(null);
        if (item == null || item.getServer() == null) return null;
        return item.getServer().get("action");
    }

    /**
     * Send visual feedback to the client after a successful item use.
     */
    public void sendItemUseFeedback(PlayerSession session, String shortcutKey) {
        if (shortcutKey == null) return;
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        String itemId = resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return;

        var cachedItems = data.getCachedItems();
        WItem item = cachedItems != null ? cachedItems.get(itemId) : null;
        if (item == null) {
            item = gameplay.getItemService().findByItemId(session.getWorldId(), itemId).orElse(null);
        }
        if (item == null || item.getPublicData() == null) return;

        String texture = item.getPublicData().getTexture();
        if (texture == null || texture.isBlank()) return;

        gameplay.getClientService().sendCommand(session, "flashImage", List.of(texture, "500", "0.5"));
    }

    /**
     * Create synthetic fist item with attack stats derived from character skills.
     * Base physical damage from combat.melee skill level.
     */
    public WItem createSyntheticFistItem(AdventureData data) {
        var skills = data.getCachedSkills();
        int melee = skills != null ? AdventureSkills.COMBAT_MELEE.getValue(skills) : 0;
        double physDmg = 2.0 + melee * 0.1;
        double physAcc = 0.6 + melee * 0.005;

        return WItem.builder()
                .itemId(BasicGameplay.FIST_ITEM_ID)
                .publicData(Item.builder()
                        .name(BasicGameplay.FIST_ITEM_ID)
                        .title("Fist")
                        .texture("n:textures/hands/fist.png")
                        .build())
                .server(Map.of(
                        "action", "attack",
                        "effects", "physical.damage:" + physDmg + ",physical.accuracy:" + physAcc
                ))
                .build();
    }

    /**
     * Create synthetic block item with defense stats derived from character skills.
     * Base physical defense from combat.defense skill level.
     */
    public WItem createSyntheticBlockItem(AdventureData data) {
        var skills = data.getCachedSkills();
        int defense = skills != null ? AdventureSkills.COMBAT_DEFENSE.getValue(skills) : 0;
        double physDef = 1.0 + defense * 0.1;
        double physEvasion = 0.1 + defense * 0.005;

        return WItem.builder()
                .itemId(BasicGameplay.BLOCK_ITEM_ID)
                .publicData(Item.builder()
                        .name(BasicGameplay.BLOCK_ITEM_ID)
                        .title("Block")
                        .texture("n:textures/hands/block.png")
                        .build())
                .server(Map.of(
                        "action", "block",
                        "effects", "physical.defense:" + physDef + ",physical.evasion:" + physEvasion
                ))
                .build();
    }

    /**
     * Get the wearing item ID for a given slot from cached backpack data.
     */
    public String getWearingItemId(AdventureData data, WEARABLE_SLOT slot) {
        var backpack = data.getCachedBackpack();
        if (backpack == null || backpack.getWearingItemIds() == null) return null;
        return backpack.getWearingItemIds().get(slot);
    }
}
