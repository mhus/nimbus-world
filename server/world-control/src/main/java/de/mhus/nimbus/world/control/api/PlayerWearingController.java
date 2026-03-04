package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.session.WSessionService;
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
 * REST Controller for player wearing operations.
 * Allows players to equip/unequip items from backpack to wearing slots.
 * Accessible by players under /control/player/wearing.
 */
@RestController
@RequestMapping("/control/player/wearing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Wearing", description = "Equip and unequip items to wearing slots")
public class PlayerWearingController extends BaseEditorController {

    private final RCharacterService characterService;
    private final WItemService wItemService;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;

    @GetMapping
    @Operation(summary = "Get backpack items and wearing items with enriched info")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wearing data returned"),
            @ApiResponse(responseCode = "400", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> getWearingData(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("GET wearing: worldId={}, userId={}, characterId={}", worldId, userId, characterId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerBackpack backpack = character.getBackpack();
        Map<String, Integer> itemIds = backpack != null ? backpack.getItemIds() : null;
        Map<WEARABLE_SLOT, String> wearingItemIds = backpack != null ? backpack.getWearingItemIds() : null;

        // Enrich backpack items
        List<Map<String, Object>> backpackItems = new ArrayList<>();
        if (itemIds != null) {
            for (var entry : itemIds.entrySet()) {
                backpackItems.add(enrichItem(parsedWorldId, entry.getKey(), entry.getValue()));
            }
        }

        // Enrich wearing items
        Map<String, Map<String, Object>> wearingItems = new LinkedHashMap<>();
        for (WEARABLE_SLOT slot : WEARABLE_SLOT.values()) {
            String itemId = wearingItemIds != null ? wearingItemIds.get(slot) : null;
            if (itemId != null) {
                wearingItems.put(slot.name(), enrichItem(parsedWorldId, itemId, 1));
            } else {
                wearingItems.put(slot.name(), null);
            }
        }

        return ResponseEntity.ok(Map.of(
                "worldId", worldId,
                "backpackItems", backpackItems,
                "wearingItems", wearingItems
        ));
    }

    @PostMapping("/equip")
    @Operation(summary = "Equip an item from backpack to a wearing slot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item equipped"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Character or item not found")
    })
    public ResponseEntity<?> equip(@RequestBody EquipRequest body, HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("POST equip: worldId={}, userId={}, characterId={}, itemId={}, slot={}",
                worldId, userId, characterId, body != null ? body.itemId() : null, body != null ? body.slot() : null);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || Strings.isBlank(body.itemId()) || body.slot() == null) {
            return bad("itemId and slot required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerBackpack backpack = character.getBackpack();
        if (backpack == null) {
            return bad("No backpack");
        }

        Map<String, Integer> itemIds = backpack.getItemIds();
        if (itemIds == null || !itemIds.containsKey(body.itemId())) {
            return bad("Item not in backpack");
        }

        WEARABLE_SLOT slot = body.slot();

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        // Validate that the item is allowed in this slot (wearableSlots contains group names)
        Map<String, Object> enriched = enrichItem(parsedWorldId, body.itemId(), 1);
        @SuppressWarnings("unchecked")
        List<String> wearableSlots = (List<String>) enriched.get("wearableSlots");
        if (wearableSlots != null && !wearableSlots.contains(slotToGroup(slot))) {
            return bad("Item cannot be equipped in slot " + slot.name());
        }

        // Initialize wearingItemIds if needed
        Map<WEARABLE_SLOT, String> wearingItemIds = backpack.getWearingItemIds();
        if (wearingItemIds == null) {
            wearingItemIds = new LinkedHashMap<>();
            backpack.setWearingItemIds(wearingItemIds);
        }

        // If slot is already occupied, move old item back to backpack
        String oldItemId = wearingItemIds.get(slot);
        if (oldItemId != null) {
            itemIds.merge(oldItemId, 1, Integer::sum);
        }

        // Remove item from backpack
        int count = itemIds.get(body.itemId());
        if (count <= 1) {
            itemIds.remove(body.itemId());
        } else {
            itemIds.put(body.itemId(), count - 1);
        }

        // Put item in slot
        wearingItemIds.put(slot, body.itemId());

        characterService.updateCharater(character);

        log.info("Equipped item: userId={}, characterId={}, itemId={}, slot={}", userId, characterId, body.itemId(), slot);
        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/unequip")
    @Operation(summary = "Unequip an item from a wearing slot back to backpack")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item unequipped"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> unequip(@RequestBody UnequipRequest body, HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("POST unequip: worldId={}, userId={}, characterId={}, slot={}",
                worldId, userId, characterId, body != null ? body.slot() : null);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.slot() == null) {
            return bad("slot required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerBackpack backpack = character.getBackpack();
        if (backpack == null) {
            return bad("No backpack");
        }

        Map<WEARABLE_SLOT, String> wearingItemIds = backpack.getWearingItemIds();
        if (wearingItemIds == null || !wearingItemIds.containsKey(body.slot())) {
            return bad("Slot is empty");
        }

        String itemId = wearingItemIds.remove(body.slot());

        // Add item back to backpack
        Map<String, Integer> itemIds = backpack.getItemIds();
        if (itemIds == null) {
            itemIds = new LinkedHashMap<>();
            backpack.setItemIds(itemIds);
        }
        itemIds.merge(itemId, 1, Integer::sum);

        characterService.updateCharater(character);

        log.info("Unequipped item: userId={}, characterId={}, itemId={}, slot={}", userId, characterId, itemId, body.slot());
        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void notifyPlayer(String worldId, HttpServletRequest request) {
        String sessionId = (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);
        if (Strings.isBlank(sessionId)) {
            log.warn("No sessionId available, cannot notify player of wearing change");
            return;
        }
        var wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot notify player of wearing change", sessionId);
            return;
        }
        worldClientService.sendPlayerCommand(worldId, sessionId, wSession.get().getPlayerUrl(), "WearingModified", List.of(), null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichItem(WorldId parsedWorldId, String itemId, int count) {
        String texture = null;
        String name = itemId;
        String itemType = null;
        String description = null;
        List<String> wearableSlots = null;

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
                if (publicData.getParameters() != null) {
                    Object slots = publicData.getParameters().get("wearableSlots");
                    if (slots instanceof List<?>) {
                        wearableSlots = (List<String>) slots;
                    }
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
        info.put("wearableSlots", wearableSlots);
        return info;
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return null;
        }
        String regionId = parsedWorldId.getRegionId();
        Optional<RCharacter> characterOpt = characterService.getCharacter(userId, regionId, characterId);
        return characterOpt.orElse(null);
    }

    private String slotToGroup(WEARABLE_SLOT slot) {
        return switch (slot) {
            case HEAD -> "HEAD";
            case BODY -> "BODY";
            case ARMS -> "ARMS";
            case LEGS -> "LEGS";
            case FEET -> "FEET";
            case NECK -> "NECK";
            case LEFT_RING, RIGHT_RING -> "RING";
            case LEFT_HAND_1, RIGHT_HAND_1, LEFT_HAND_2, RIGHT_HAND_2 -> "HAND";
            default -> slot.name();
        };
    }

    record EquipRequest(String itemId, WEARABLE_SLOT slot) {}
    record UnequipRequest(WEARABLE_SLOT slot) {}
}
