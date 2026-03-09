package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Action: drop.item
 *
 * Drops an item from the player's shortcut/backpack onto a block position.
 *
 * ServerInfo parameters:
 * - location: top/bottom/left/right/front/back (default: top) - relative placement to the block
 * - category: if set, only items with matching 'category' in their parameters can be dropped here
 */
@Slf4j
public class DropItemAction implements GameplayAction {

    private final AdventureGameplay adventure;

    public DropItemAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId,
                                     String blockAction, JsonNode params, String userAction, String shortcutKey,
                                     Map<String, String> serverInfo) {
        if (shortcutKey == null) {
            log.debug("drop.item requires a shortcut key");
            return false;
        }

        // Resolve item behind shortcut (backpack mode passes itemId in params)
        String itemId = adventure.resolveShortcutItemId(session, shortcutKey, params);
        if (itemId == null) {
            log.debug("No item resolved for shortcut {}", shortcutKey);
            adventure.getClientService().sendNotification(session, 0, "", "No item selected", null);
            return false;
        }

        // Load WItem from DB for category check and display data
        var wItemOpt = adventure.getItemService().findByItemId(session.getWorldId(), itemId);
        if (wItemOpt.isEmpty()) {
            log.warn("Item {} not found in DB", itemId);
            adventure.getClientService().sendNotification(session, 0, "", "Item not found", null);
            return false;
        }
        WItem wItem = wItemOpt.get();

        // Category check
        String requiredCategory = serverInfo != null ? serverInfo.get("category") : null;
        if (requiredCategory != null && !requiredCategory.isBlank()) {
            String itemCategory = null;
            if (wItem.getPublicData() != null && wItem.getPublicData().getParameters() != null) {
                itemCategory = wItem.getPublicData().getParameters().get("category");
            }
            if (itemCategory == null || !requiredCategory.equals(itemCategory)) {
                log.debug("Item {} category '{}' does not match required '{}'", itemId, itemCategory, requiredCategory);
                adventure.getClientService().sendNotification(session, 0, "", "Wrong item type", null);
                return false;
            }
        }

        // Calculate target position based on location
        String location = serverInfo != null ? serverInfo.get("location") : null;
        if (location == null || location.isBlank()) {
            location = "top";
        }

        int targetX = x;
        int targetY = y;
        int targetZ = z;
        switch (location.toLowerCase()) {
            case "top" -> targetY = y + 1;
            case "bottom" -> targetY = y - 1;
            case "left" -> targetX = x - 1;
            case "right" -> targetX = x + 1;
            case "front" -> targetZ = z + 1;
            case "back" -> targetZ = z - 1;
            default -> {
                log.warn("Unknown location '{}', defaulting to top", location);
                targetY = y + 1;
            }
        }

        // Check if target position is free (no existing item)
        var existingItem = adventure.getItemPositionService().getItemAt(session.getWorldId(), targetX, targetY, targetZ);
        if (existingItem.isPresent()) {
            log.debug("Position ({},{},{}) already occupied by item {}", targetX, targetY, targetZ, existingItem.get().getItemId());
            adventure.getClientService().sendNotification(session, 0, "", "Position occupied", null);
            return false;
        }

        // Remove item from backpack (also cleans up shortcuts if quantity reaches 0)
        boolean reduced = adventure.getGameplayService().reduceItem(session, itemId, 1);
        if (!reduced) {
            log.debug("Could not reduce item {} from backpack", itemId);
            adventure.getClientService().sendNotification(session, 0, "", "Item not available", null);
            return false;
        }

        // Build ItemBlockRef for placement
        var itemData = wItem.getPublicData();
        ItemBlockRef itemBlockRef = ItemBlockRef.builder()
                .name(itemId)
                .position(Vector3.builder().x(targetX).y(targetY).z(targetZ).build())
                .texture(itemData != null ? itemData.getTexture() : null)
                .scaleX(itemData != null ? itemData.getScaleX() : null)
                .scaleY(itemData != null ? itemData.getScaleY() : null)
                .offset(itemData != null ? itemData.getOffset() : null)
                .title(itemData != null ? itemData.getTitle() : null)
                .amount(1)
                .build();

        // Place item in world
        adventure.getItemPositionService().saveItemPosition(session.getWorldId(), itemBlockRef);

        // Broadcast placement to all clients
        adventure.getItemBlockUpdatePublisher().publishItemAdded(session.getWorldId(), itemBlockRef);

        String title = itemData != null && itemData.getTitle() != null ? itemData.getTitle() : itemId;
        String texture = itemData != null ? itemData.getTexture() : null;
        adventure.getClientService().sendNotification(session, 3, "", "Dropped " + title, texture);
        log.info("Player {} dropped item {} at ({},{},{})", session.getEntityId(), itemId, targetX, targetY, targetZ);

        return true;
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        return false;
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        return false;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        return false;
    }
}
