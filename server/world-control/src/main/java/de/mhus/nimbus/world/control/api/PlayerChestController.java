package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.*;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WChest;
import de.mhus.nimbus.world.shared.world.WChestService;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for player's own chests (bank and transfer).
 * No PIN or access checks needed - these are the player's personal chests.
 * Accessible by players under /control/player/chest.
 */
@RestController
@RequestMapping("/control/player/chest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Chest", description = "Player's bank and transfer chests")
public class PlayerChestController extends BaseEditorController {

    private final WChestService chestService;
    private final RCharacterService characterService;
    private final WItemService wItemService;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;

    /**
     * Get player's bank and transfer chests with backpack items.
     */
    @GetMapping
    @Operation(summary = "Get player's bank and transfer chests")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chests and backpack data returned"),
            @ApiResponse(responseCode = "400", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> getChests(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("GET player chests: worldId={}, userId={}, characterId={}", worldId, userId, characterId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        var playerId = PlayerId.of(userId, characterId).orElse(null);
        if (playerId == null) {
            return bad("Invalid playerId");
        }

        // Get or create bank and transfer chests
        WChest bankChest = chestService.getOrCreateUserBankChest(worldId, playerId);
        WChest transferChest = chestService.getOrCreateUserTransferChest(worldId, playerId);

        // Load character and backpack
        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerBackpack backpack = character.getBackpack();
        Map<String, Integer> itemIds = backpack != null ? backpack.getItemIds() : null;

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("worldId", worldId);
        result.put("bank", buildChestData(parsedWorldId, bankChest));
        result.put("transfer", buildChestData(parsedWorldId, transferChest));
        result.put("backpack", Map.of("items", enrichBackpackItems(parsedWorldId, itemIds)));
        result.put("shortcutItemIds", getShortcutItemIds(character));

        return ResponseEntity.ok(result);
    }

    /**
     * Transfer item from a player chest to backpack.
     */
    @PostMapping("/to-backpack")
    @Operation(summary = "Move item from player chest to backpack")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item transferred"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Chest or character not found")
    })
    public ResponseEntity<?> toBackpack(
            @RequestBody TransferRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || Strings.isBlank(body.chestType()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("chestType, itemId and amount (> 0) required");
        }

        WChest chest = resolvePlayerChest(worldId, userId, characterId, body.chestType());
        if (chest == null) {
            return bad("Invalid chestType: " + body.chestType());
        }

        // Find item in chest
        ItemRef chestItem = null;
        for (ItemRef item : chest.getItems()) {
            if (item.getName().equals(body.itemId())) {
                chestItem = item;
                break;
            }
        }
        if (chestItem == null) {
            return bad("Item not found in chest");
        }

        int transferAmount = Math.min(body.amount(), chestItem.getAmount());

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        // Atomic chest update: reduce or remove item
        boolean chestUpdated;
        if (chestItem.getAmount() <= transferAmount) {
            chestUpdated = chestService.removeItemAtomic(chest.getId(), body.itemId());
        } else {
            chestUpdated = chestService.updateItemAmountAtomic(chest.getId(), body.itemId(), chestItem.getAmount() - transferAmount);
        }
        if (!chestUpdated) {
            return bad("Failed to update chest (concurrent modification)");
        }

        // Atomic backpack update: add or increase item
        boolean backpackUpdated = characterService.addBackpackItem(character.getId(), body.itemId(), transferAmount);
        if (!backpackUpdated) {
            // Compensate: the chest was already reduced, so return the amount to
            // the chest to avoid losing the item.
            boolean restored;
            if (chestItem.getAmount() <= transferAmount) {
                restored = chestService.addItemAtomic(chest.getId(), ItemRef.builder()
                        .itemId(body.itemId())
                        .name(chestItem.getName())
                        .texture(chestItem.getTexture())
                        .amount(transferAmount)
                        .build());
            } else {
                restored = chestService.incItemAmountAtomic(chest.getId(), body.itemId(), transferAmount);
            }
            log.error("Backpack update failed after chest was modified; chest restore={}. chestId={}, itemId={}, amount={}",
                    restored, chest.getId(), body.itemId(), transferAmount);
            return bad("Failed to update backpack");
        }

        log.info("Item transferred chest->backpack: worldId={}, userId={}, chestType={}, itemId={}, amount={}",
                worldId, userId, body.chestType(), body.itemId(), transferAmount);

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("transferred", transferAmount));
    }

    /**
     * Transfer item from backpack to a player chest.
     */
    @PostMapping("/to-chest")
    @Operation(summary = "Move item from backpack to player chest")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item transferred"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Chest or character not found")
    })
    public ResponseEntity<?> toChest(
            @RequestBody TransferRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || Strings.isBlank(body.chestType()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("chestType, itemId and amount (> 0) required");
        }

        WChest chest = resolvePlayerChest(worldId, userId, characterId, body.chestType());
        if (chest == null) {
            return bad("Invalid chestType: " + body.chestType());
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        // Check if item is referenced by a shortcut
        if (getShortcutItemIds(character).contains(body.itemId())) {
            return bad("Item is assigned to a shortcut and cannot be transferred");
        }

        PlayerBackpack backpack = character.getBackpack();
        Map<String, Integer> itemIds = backpack != null ? backpack.getItemIds() : null;
        if (itemIds == null || !itemIds.containsKey(body.itemId())) {
            return bad("Item not found in backpack");
        }

        int backpackCount = itemIds.get(body.itemId());
        int transferAmount = Math.min(body.amount(), backpackCount);

        // Check capacity
        if (chest.getCapacity() > 0 && chest.getItems().size() >= chest.getCapacity()) {
            boolean existsInChest = chest.getItems().stream()
                    .anyMatch(i -> i.getItemId().equals(body.itemId()));
            if (!existsInChest) {
                return bad("Chest is full");
            }
        }

        // Atomic backpack update: reduce or remove item
        boolean backpackUpdated = characterService.removeBackpackItem(character.getId(), body.itemId(), transferAmount);
        if (!backpackUpdated) {
            return bad("Failed to update backpack (concurrent modification or insufficient quantity)");
        }

        // Atomic chest update: add or increase item
        ItemRef existingChestItem = null;
        for (ItemRef item : chest.getItems()) {
            if (item.getName().equals(body.itemId())) {
                existingChestItem = item;
                break;
            }
        }

        boolean chestUpdated;
        if (existingChestItem != null) {
            chestUpdated = chestService.incItemAmountAtomic(chest.getId(), body.itemId(), transferAmount);
        } else {
            ItemRef newRef = ItemRef.builder()
                    .itemId(body.itemId())
                    .amount(transferAmount)
                    .build();
            chestUpdated = chestService.addItemAtomic(chest.getId(), newRef);
        }
        if (!chestUpdated) {
            // Compensate: the backpack was already reduced, so return the amount
            // to the backpack to avoid losing the item.
            boolean restored = characterService.addBackpackItem(character.getId(), body.itemId(), transferAmount);
            log.error("Chest update failed after backpack was modified; backpack restore={}. chestId={}, itemId={}, amount={}",
                    restored, chest.getId(), body.itemId(), transferAmount);
            return bad("Failed to update chest");
        }

        log.info("Item transferred backpack->chest: worldId={}, userId={}, chestType={}, itemId={}, amount={}",
                worldId, userId, body.chestType(), body.itemId(), transferAmount);

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("transferred", transferAmount));
    }

    // --- Helper methods ---

    private WChest resolvePlayerChest(String worldId, String userId, String characterId, String chestType) {
        var playerId = PlayerId.of(userId, characterId).orElse(null);
        if (playerId == null) return null;

        return switch (chestType.toLowerCase()) {
            case "bank" -> chestService.getOrCreateUserBankChest(worldId, playerId);
            case "transfer" -> chestService.getOrCreateUserTransferChest(worldId, playerId);
            default -> null;
        };
    }

    private Map<String, Object> buildChestData(WorldId parsedWorldId, WChest chest) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", chest.getName());
        data.put("title", chest.getTitle());
        data.put("capacity", chest.getCapacity());
        data.put("items", enrichChestItems(parsedWorldId, chest.getItems()));
        return data;
    }

    private Set<String> getShortcutItemIds(RCharacter character) {
        Set<String> itemIds = new HashSet<>();
        PlayerInfo playerInfo = character.getPublicData();
        if (playerInfo == null || playerInfo.getShortcuts() == null) return itemIds;
        for (ShortcutDefinition shortcut : playerInfo.getShortcuts().values()) {
            if (shortcut != null && !Strings.isBlank(shortcut.getItemId())) {
                itemIds.add(shortcut.getItemId());
            }
        }
        return itemIds;
    }

    private void notifyPlayer(String worldId, HttpServletRequest request) {
        String sessionId = (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);
        if (Strings.isBlank(sessionId)) {
            log.warn("No sessionId available, cannot notify player of backpack change");
            return;
        }
        var wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot notify player of backpack change", sessionId);
            return;
        }
        worldClientService.sendPlayerCommand(worldId, sessionId, wSession.get().getPlayerUrl(), "BackpackModified", List.of(), null);
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return null;
        }
        String regionId = parsedWorldId.getRegionId();
        return characterService.getCharacter(userId, regionId, characterId).orElse(null);
    }

    private List<Map<String, Object>> enrichChestItems(WorldId parsedWorldId, List<ItemRef> items) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        if (items == null) return enriched;

        for (ItemRef ref : items) {
            String texture = ref.getTexture();
            String name = ref.getName();
            String itemType = null;
            String description = null;

            Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, ref.getItemId());
            if (itemOpt.isPresent()) {
                Item publicData = itemOpt.get().getPublicData();
                if (publicData != null) {
                    itemType = publicData.getItemType();
                    if (texture == null && !Strings.isBlank(publicData.getTexture())) {
                        texture = publicData.getTexture();
                    }
                    if (Strings.isBlank(name) && !Strings.isBlank(publicData.getName())) {
                        name = publicData.getName();
                    }
                    if (!Strings.isBlank(publicData.getDescription())) {
                        description = publicData.getDescription();
                    }
                }
            }

            if (Strings.isBlank(name)) {
                name = ref.getItemId();
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("itemId", ref.getItemId());
            info.put("name", name);
            info.put("itemType", itemType);
            info.put("texture", texture);
            info.put("description", description);
            info.put("amount", ref.getAmount());
            enriched.add(info);
        }
        return enriched;
    }

    private List<Map<String, Object>> enrichBackpackItems(WorldId parsedWorldId, Map<String, Integer> itemIds) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (itemIds == null) return items;

        for (var entry : itemIds.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue();

            String texture = null;
            String name = itemId;
            String itemType = null;
            String description = null;

            Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, itemId);
            if (itemOpt.isPresent()) {
                Item publicData = itemOpt.get().getPublicData();
                if (publicData != null) {
                    itemType = publicData.getItemType();
                    texture = publicData.getTexture();
                    if (!Strings.isBlank(publicData.getName())) {
                        name = publicData.getName();
                    }
                    if (!Strings.isBlank(publicData.getDescription())) {
                        description = publicData.getDescription();
                    }
                }
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("itemId", itemId);
            info.put("name", name);
            info.put("itemType", itemType);
            info.put("texture", texture);
            info.put("description", description);
            info.put("count", count);
            items.add(info);
        }
        return items;
    }

    // --- Request DTOs ---

    record TransferRequest(String chestType, String itemId, int amount) {}
}
