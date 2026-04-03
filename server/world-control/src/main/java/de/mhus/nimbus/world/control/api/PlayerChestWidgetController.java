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
import de.mhus.nimbus.world.shared.world.WLease;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import de.mhus.nimbus.world.shared.world.WProgressService;
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
 * REST Controller for chest widget operations.
 * Allows players to interact with world chests via a WLease reference.
 * The WLease (type "chest-access") contains a chestId as resourceId.
 * Validates that the WLease belongs to the requesting player and world.
 * Accessible by players under /control/player/chest-widget.
 */
@RestController
@RequestMapping("/control/player/chest-widget")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Chest Widget", description = "Interact with world chests via progress reference")
public class PlayerChestWidgetController extends BaseEditorController {

    private final WChestService chestService;
    private final WLeaseService leaseService;
    private final WProgressService progressService;
    private final RCharacterService characterService;
    private final WItemService wItemService;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;

    /**
     * Get chest and backpack items via WProgress reference.
     * Validates the progress belongs to the requesting player and world.
     */
    @GetMapping
    @Operation(summary = "Get chest and backpack items via progress reference")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chest and backpack data returned"),
            @ApiResponse(responseCode = "400", description = "Not authenticated or invalid request"),
            @ApiResponse(responseCode = "404", description = "Progress, chest, or character not found")
    })
    public ResponseEntity<?> getChest(
            @RequestParam String progressId,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("GET chest-widget: worldId={}, userId={}, characterId={}, progressId={}", worldId, userId, characterId, progressId);

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

        // Load and validate WProgress
        var resolveResult = resolveChestFromLease(progressId, worldId, userId);
        if (resolveResult.error != null) {
            return resolveResult.error;
        }
        WChest chest = resolveResult.chest;

        // Access check (PIN-based)
        boolean requiresPin = !Strings.isBlank(chest.getPin());
        boolean accessGranted = !requiresPin;

        if (requiresPin) {
            var pinProgressOpt = progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(
                    worldId, userId, "CHEST_ACCESS", chest.getName());
            accessGranted = pinProgressOpt.isPresent();
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
    @Operation(summary = "Validate chest PIN via progress reference")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PIN validated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Progress or chest not found")
    })
    public ResponseEntity<?> validatePin(
            @RequestBody PinRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }
        if (body == null || Strings.isBlank(body.progressId()) || Strings.isBlank(body.pin())) {
            return bad("progressId and pin required");
        }

        var resolveResult = resolveChestFromLease(body.progressId(), worldId, userId);
        if (resolveResult.error != null) {
            return resolveResult.error;
        }
        WChest chest = resolveResult.chest;

        if (!body.pin().equals(chest.getPin())) {
            return bad("Invalid PIN");
        }

        // Grant access via WProgress
        progressService.save(worldId, userId, "CHEST_ACCESS", chest.getName(), Map.of("granted", true));

        log.info("Chest widget PIN validated: worldId={}, userId={}, chestName={}", worldId, userId, chest.getName());
        return ResponseEntity.ok(Map.of("accessGranted", true));
    }

    /**
     * Transfer item from chest to backpack.
     */
    @PostMapping("/to-backpack")
    @Operation(summary = "Move item from world chest to backpack")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item transferred"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Progress, chest, or character not found")
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
        if (body == null || Strings.isBlank(body.progressId()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("progressId, itemId and amount (> 0) required");
        }

        var resolveResult = resolveChestFromLease(body.progressId(), worldId, userId);
        if (resolveResult.error != null) {
            return resolveResult.error;
        }
        WChest chest = resolveResult.chest;

        // PIN access check
        var accessCheck = checkChestPinAccess(worldId, userId, chest);
        if (accessCheck != null) return accessCheck;

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
            log.error("Backpack update failed after chest was already modified! chestId={}, itemId={}, amount={}",
                    chest.getId(), body.itemId(), transferAmount);
            return bad("Failed to update backpack");
        }

        log.info("Chest widget item transferred chest->backpack: worldId={}, userId={}, itemId={}, amount={}",
                worldId, userId, body.itemId(), transferAmount);

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("transferred", transferAmount));
    }

    /**
     * Transfer item from backpack to chest.
     */
    @PostMapping("/to-chest")
    @Operation(summary = "Move item from backpack to world chest")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item transferred"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Progress, chest, or character not found")
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
        if (body == null || Strings.isBlank(body.progressId()) || Strings.isBlank(body.itemId()) || body.amount() <= 0) {
            return bad("progressId, itemId and amount (> 0) required");
        }

        var resolveResult = resolveChestFromLease(body.progressId(), worldId, userId);
        if (resolveResult.error != null) {
            return resolveResult.error;
        }
        WChest chest = resolveResult.chest;

        // PIN access check
        var accessCheck = checkChestPinAccess(worldId, userId, chest);
        if (accessCheck != null) return accessCheck;

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
            log.error("Chest update failed after backpack was already modified! chestId={}, itemId={}, amount={}",
                    chest.getId(), body.itemId(), transferAmount);
            return bad("Failed to update chest");
        }

        log.info("Chest widget item transferred backpack->chest: worldId={}, userId={}, itemId={}, amount={}",
                worldId, userId, body.itemId(), transferAmount);

        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("transferred", transferAmount));
    }

    // --- Helper methods ---

    /**
     * Resolve chest from a WLease entry.
     * Validates that the lease belongs to the requesting player and world,
     * and is of type "chest-access".
     */
    private ChestResolveResult resolveChestFromLease(String leaseId, String worldId, String userId) {
        var leaseOpt = leaseService.validate(leaseId, worldId, userId, "chest-access");
        if (leaseOpt.isEmpty()) {
            return ChestResolveResult.ofError(notFound("Lease not found or access denied"));
        }
        WLease lease = leaseOpt.get();

        // resourceId contains the chest name
        String chestName = lease.getResourceId();
        if (Strings.isBlank(chestName)) {
            return ChestResolveResult.ofError(bad("Lease does not reference a chest"));
        }

        // Load chest by name (COW-aware: for instance worlds, resolves from base world too)
        Optional<WChest> chestOpt = chestService.getByWorldIdAndName(worldId, chestName);
        if (chestOpt.isEmpty()) {
            return ChestResolveResult.ofError(notFound("Chest not found"));
        }

        // Ensure COW copy exists for instance worlds before any write operation
        WChest chest = chestService.ensureCowCopy(worldId, chestOpt.get());
        return ChestResolveResult.ofChest(chest);
    }

    private ResponseEntity<?> checkChestPinAccess(String worldId, String userId, WChest chest) {
        if (!Strings.isBlank(chest.getPin())) {
            var pinProgressOpt = progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(
                    worldId, userId, "CHEST_ACCESS", chest.getName());
            if (pinProgressOpt.isEmpty()) {
                return unauthorized("Access denied - PIN required");
            }
        }
        return null;
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

    // --- DTOs ---

    record PinRequest(String progressId, String pin) {}
    record TransferRequest(String progressId, String itemId, int amount) {}

    private static class ChestResolveResult {
        final WChest chest;
        final ResponseEntity<?> error;

        private ChestResolveResult(WChest chest, ResponseEntity<?> error) {
            this.chest = chest;
            this.error = error;
        }

        static ChestResolveResult ofChest(WChest chest) {
            return new ChestResolveResult(chest, null);
        }

        static ChestResolveResult ofError(ResponseEntity<?> error) {
            return new ChestResolveResult(null, error);
        }
    }
}
