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
import de.mhus.nimbus.world.shared.world.*;
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
 * REST Controller for player chest operations.
 * Allows players to transfer items between chests and their backpack.
 * Accessible by players under /control/player/chest.
 *
 * worldId, userId and characterId are extracted from the session cookie via ControlAccessFilter.
 */
@RestController
@RequestMapping("/control/player/chest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Chest", description = "Transfer items between chest and backpack")
public class PlayerChestController extends BaseEditorController {

    private final WChestService chestService;
    private final WProgressService progressService;
    private final RCharacterService characterService;
    private final WItemService wItemService;
    private final WItemTypeService wItemTypeService;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;

    /**
     * Get chest and backpack items with access check.
     */
    @GetMapping
    @Operation(summary = "Get chest and backpack items")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chest and backpack data returned"),
            @ApiResponse(responseCode = "400", description = "Not authenticated or invalid request"),
            @ApiResponse(responseCode = "404", description = "Chest or character not found")
    })
    public ResponseEntity<?> getChest(
            @RequestParam(required = false) String chestId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("GET chest: worldId={}, userId={}, characterId={}, chestId={}", worldId, userId, characterId, chestId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        // Load chest
        WChest chest;
        if (!Strings.isBlank(chestId)) {
            var chestOpt = chestService.getByWorldIdAndName(worldId, chestId);
            if (chestOpt.isEmpty()) {
                return notFound("Chest not found");
            }
            chest = chestOpt.get();
        } else {
            // Get or create user bank chest
            var playerId = PlayerId.of(userId, characterId).orElse(null);
            if (playerId == null) {
                return bad("Invalid playerId");
            }
            chest = chestService.getOrCreateUserBankChest(worldId, playerId);
        }

        // Access check (PIN-based)
        boolean requiresPin = !Strings.isBlank(chest.getPin());
        boolean accessGranted = !requiresPin;

        if (requiresPin) {
            var progressOpt = progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(
                    worldId, userId, "CHEST_ACCESS", chest.getName());
            accessGranted = progressOpt.isPresent();
        }

        // Load character and backpack
        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("worldId", worldId);

        Map<String, Object> chestData = new LinkedHashMap<>();
        chestData.put("name", chest.getName());
        chestData.put("title", chest.getTitle());
        chestData.put("capacity", chest.getCapacity());
        if (accessGranted) {
            chestData.put("items", enrichChestItems(parsedWorldId, chest.getItems()));
        }
        result.put("chest", chestData);

        if (accessGranted) {
            PlayerBackpack backpack = character.getBackpack();
            Map<String, Integer> itemIds = backpack != null ? backpack.getItemIds() : null;
            result.put("backpack", Map.of("items", enrichBackpackItems(parsedWorldId, itemIds)));
            result.put("shortcutItemIds", getShortcutItemIds(character));
        }

        result.put("accessGranted", accessGranted);
        result.put("requiresPin", requiresPin);

        return ResponseEntity.ok(result);
    }

    /**
     * Validate PIN and grant access via WProgress.
     */
    @PostMapping("/pin")
    @Operation(summary = "Validate chest PIN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PIN validated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Chest not found")
    })
    public ResponseEntity<?> validatePin(
            @RequestBody PinRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        if (body == null || Strings.isBlank(body.chestId()) || Strings.isBlank(body.pin())) {
            return bad("chestId and pin required");
        }

        Optional<WChest> chestOpt = chestService.getByWorldIdAndName(worldId, body.chestId());
        if (chestOpt.isEmpty()) {
            return notFound("Chest not found");
        }
        WChest chest = chestOpt.get();

        if (!body.pin().equals(chest.getPin())) {
            return bad("Invalid PIN");
        }

        // Grant access via WProgress
        progressService.save(worldId, userId, "CHEST_ACCESS", chest.getName(), Map.of("granted", true));

        log.info("Chest PIN validated: worldId={}, userId={}, chestName={}", worldId, userId, chest.getName());
        return ResponseEntity.ok(Map.of("accessGranted", true));
    }

    /**
     * Transfer item from chest to backpack.
     */
    @PostMapping("/to-backpack")
    @Operation(summary = "Move item from chest to backpack")
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

        if (body == null || Strings.isBlank(body.chestId()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("chestId, itemId and amount (> 0) required");
        }

        // Access check
        var accessCheck = checkChestAccess(worldId, userId, body.chestId());
        if (accessCheck != null) return accessCheck;

        Optional<WChest> chestOpt = chestService.getByWorldIdAndName(worldId, body.chestId());
        if (chestOpt.isEmpty()) {
            return notFound("Chest not found");
        }
        WChest chest = chestOpt.get();

        // Find item in chest
        ItemRef chestItem = null;
        for (ItemRef item : chest.getItems()) {
            if (item.getItemId().equals(body.itemId())) {
                chestItem = item;
                break;
            }
        }
        if (chestItem == null) {
            return bad("Item not found in chest");
        }

        int transferAmount = Math.min(body.amount(), chestItem.getAmount());

        // Update chest: reduce or remove item
        if (chestItem.getAmount() <= transferAmount) {
            chestService.removeItem(chest.getId(), body.itemId());
        } else {
            chestService.updateItemAmount(chest.getId(), body.itemId(), chestItem.getAmount() - transferAmount);
        }

        // Update backpack: add or increase item
        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerBackpack backpack = character.getBackpack();
        if (backpack == null) {
            backpack = new PlayerBackpack();
            character.setBackpack(backpack);
        }
        if (backpack.getItemIds() == null) {
            backpack.setItemIds(new HashMap<>());
        }

        int currentCount = backpack.getItemIds().getOrDefault(body.itemId(), 0);
        backpack.getItemIds().put(body.itemId(), currentCount + transferAmount);
        characterService.updateCharater(character);

        log.info("Item transferred chest->backpack: worldId={}, userId={}, itemId={}, amount={}",
                worldId, userId, body.itemId(), transferAmount);

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("transferred", transferAmount));
    }

    /**
     * Transfer item from backpack to chest.
     */
    @PostMapping("/to-chest")
    @Operation(summary = "Move item from backpack to chest")
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

        if (body == null || Strings.isBlank(body.chestId()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("chestId, itemId and amount (> 0) required");
        }

        // Access check
        var accessCheck = checkChestAccess(worldId, userId, body.chestId());
        if (accessCheck != null) return accessCheck;

        // Load character and backpack
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

        Optional<WChest> chestOpt = chestService.getByWorldIdAndName(worldId, body.chestId());
        if (chestOpt.isEmpty()) {
            return notFound("Chest not found");
        }
        WChest chest = chestOpt.get();

        // Check capacity
        if (chest.getCapacity() > 0 && chest.getItems().size() >= chest.getCapacity()) {
            // Check if item already exists in chest (can still increase amount)
            boolean existsInChest = chest.getItems().stream()
                    .anyMatch(i -> i.getItemId().equals(body.itemId()));
            if (!existsInChest) {
                return bad("Chest is full");
            }
        }

        // Update backpack: reduce or remove item
        if (backpackCount <= transferAmount) {
            itemIds.remove(body.itemId());
        } else {
            itemIds.put(body.itemId(), backpackCount - transferAmount);
        }
        characterService.updateCharater(character);

        // Update chest: add or increase item
        ItemRef existingChestItem = null;
        for (ItemRef item : chest.getItems()) {
            if (item.getItemId().equals(body.itemId())) {
                existingChestItem = item;
                break;
            }
        }

        if (existingChestItem != null) {
            chestService.updateItemAmount(chest.getId(), body.itemId(), existingChestItem.getAmount() + transferAmount);
        } else {
            ItemRef newRef = ItemRef.builder()
                    .itemId(body.itemId())
                    .amount(transferAmount)
                    .build();
            chestService.addItem(chest.getId(), newRef);
        }

        log.info("Item transferred backpack->chest: worldId={}, userId={}, itemId={}, amount={}",
                worldId, userId, body.itemId(), transferAmount);

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("transferred", transferAmount));
    }

    // --- Helper methods ---

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

    private ResponseEntity<?> checkChestAccess(String worldId, String userId, String chestId) {
        Optional<WChest> chestOpt = chestService.getByWorldIdAndName(worldId, chestId);
        if (chestOpt.isEmpty()) {
            return notFound("Chest not found");
        }
        WChest chest = chestOpt.get();

        if (!Strings.isBlank(chest.getPin())) {
            var progressOpt = progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(
                    worldId, userId, "CHEST_ACCESS", chest.getName());
            if (progressOpt.isEmpty()) {
                return unauthorized("Access denied - PIN required");
            }
        }
        return null;
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
                    if (publicData.getModifier() != null && texture == null) {
                        texture = publicData.getModifier().getTexture();
                    }
                    if (Strings.isBlank(name) && !Strings.isBlank(publicData.getName())) {
                        name = publicData.getName();
                    }
                    if (!Strings.isBlank(publicData.getDescription())) {
                        description = publicData.getDescription();
                    }
                }
            }

            // Fallback to ItemType
            if (!Strings.isBlank(itemType)) {
                Optional<WItemType> typeOpt = wItemTypeService.findByItemType(parsedWorldId, itemType);
                if (typeOpt.isPresent()) {
                    ItemType typeData = typeOpt.get().getPublicData();
                    if (typeData != null) {
                        if (texture == null && typeData.getModifier() != null) {
                            texture = typeData.getModifier().getTexture();
                        }
                        if (Strings.isBlank(name) && !Strings.isBlank(typeData.getTitle())) {
                            name = typeData.getTitle();
                        }
                        if (description == null && !Strings.isBlank(typeData.getDescription())) {
                            description = typeData.getDescription();
                        }
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
                    if (publicData.getModifier() != null) {
                        texture = publicData.getModifier().getTexture();
                    }
                    if (!Strings.isBlank(publicData.getName())) {
                        name = publicData.getName();
                    }
                    if (!Strings.isBlank(publicData.getDescription())) {
                        description = publicData.getDescription();
                    }
                }
            }

            // Fallback to ItemType
            if (!Strings.isBlank(itemType)) {
                Optional<WItemType> typeOpt = wItemTypeService.findByItemType(parsedWorldId, itemType);
                if (typeOpt.isPresent()) {
                    ItemType typeData = typeOpt.get().getPublicData();
                    if (typeData != null) {
                        if (texture == null && typeData.getModifier() != null) {
                            texture = typeData.getModifier().getTexture();
                        }
                        if (itemId.equals(name) && !Strings.isBlank(typeData.getTitle())) {
                            name = typeData.getTitle();
                        }
                        if (description == null && !Strings.isBlank(typeData.getDescription())) {
                            description = typeData.getDescription();
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
            items.add(info);
        }
        return items;
    }

    // --- Request DTOs ---

    record PinRequest(String chestId, String pin) {}
    record TransferRequest(String chestId, String itemId, int amount) {}
}
