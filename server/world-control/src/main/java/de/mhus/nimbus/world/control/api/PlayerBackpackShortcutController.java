package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.generated.types.ItemType;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WItemType;
import de.mhus.nimbus.world.shared.world.WItemTypeService;
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
 * REST Controller for player backpack shortcut operations.
 * Allows players to assign backpack items to shortcut slots.
 * Accessible by players under /control/player/backpack-shortcut.
 *
 * worldId, userId and characterId are extracted from the session cookie via ControlAccessFilter.
 */
@RestController
@RequestMapping("/control/player/backpack-shortcut")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Backpack Shortcuts", description = "Assign backpack items to shortcut slots")
public class PlayerBackpackShortcutController extends BaseEditorController {

    private final RCharacterService characterService;
    private final WItemService wItemService;
    private final WItemTypeService wItemTypeService;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;

    /**
     * Get backpack items enriched with texture and name information.
     */
    @GetMapping("/backpack")
    @Operation(summary = "Get backpack items with texture and name info")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backpack items returned"),
            @ApiResponse(responseCode = "400", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> getBackpack(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("GET backpack: worldId={}, userId={}, characterId={}", worldId, userId, characterId);

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

        List<Map<String, Object>> items = new ArrayList<>();
        if (itemIds != null) {
            for (var entry : itemIds.entrySet()) {
                String itemId = entry.getKey();
                int count = entry.getValue();

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
                        if (publicData.getModifier() != null) {
                            texture = publicData.getModifier().getTexture();
                        }
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

                // Fallback to ItemType for texture, name, description and wearableSlots
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
                            if (wearableSlots == null && typeData.getParameters() != null) {
                                Object slots = typeData.getParameters().get("wearableSlots");
                                if (slots instanceof List<?>) {
                                    wearableSlots = (List<String>) slots;
                                }
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
                items.add(info);
            }
        }

        return ResponseEntity.ok(Map.of(
                "worldId", worldId,
                "items", items
        ));
    }

    /**
     * Get player shortcuts (not editor shortcuts).
     */
    @GetMapping
    @Operation(summary = "Get player shortcuts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shortcuts found"),
            @ApiResponse(responseCode = "400", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> getShortcuts(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("GET player shortcuts: worldId={}, userId={}, characterId={}", worldId, userId, characterId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerInfo playerInfo = character.getPublicData();
        Map<String, ShortcutDefinition> shortcuts = playerInfo != null ? playerInfo.getShortcuts() : null;

        return ResponseEntity.ok(Map.of(
                "shortcuts", shortcuts != null ? shortcuts : Map.of()
        ));
    }

    /**
     * Assign a backpack item to a shortcut slot. Validates the item exists in the backpack
     * and builds the ShortcutDefinition server-side.
     */
    @PostMapping("/assign")
    @Operation(summary = "Assign backpack item to shortcut slot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shortcut assigned"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Character or item not found")
    })
    public ResponseEntity<?> assignShortcut(
            @RequestBody AssignShortcutRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("POST assign shortcut: worldId={}, userId={}, characterId={}, slotKey={}, itemId={}",
                worldId, userId, characterId, body != null ? body.slotKey() : null, body != null ? body.itemId() : null);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || Strings.isBlank(body.slotKey()) || Strings.isBlank(body.itemId())) {
            return bad("slotKey and itemId required");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        // Validate item exists in backpack
        PlayerBackpack backpack = character.getBackpack();
        Map<String, Integer> itemIds = backpack != null ? backpack.getItemIds() : null;
        if (itemIds == null || !itemIds.containsKey(body.itemId())) {
            return bad("Item not in backpack");
        }

        // Enrich item to build ShortcutDefinition server-side
        String texture = null;
        String name = body.itemId();
        String itemType = null;

        Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, body.itemId());
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
            }
        }

        if (!Strings.isBlank(itemType)) {
            Optional<WItemType> typeOpt = wItemTypeService.findByItemType(parsedWorldId, itemType);
            if (typeOpt.isPresent()) {
                ItemType typeData = typeOpt.get().getPublicData();
                if (typeData != null) {
                    if (texture == null && typeData.getModifier() != null) {
                        texture = typeData.getModifier().getTexture();
                    }
                    if (body.itemId().equals(name) && !Strings.isBlank(typeData.getTitle())) {
                        name = typeData.getTitle();
                    }
                }
            }
        }

        // Build ShortcutDefinition
        ShortcutDefinition shortcut = new ShortcutDefinition();
        shortcut.setType("use");
        shortcut.setItemId(body.itemId());
        shortcut.setName(name);
        shortcut.setIconPath(texture);
        shortcut.setWait(0);

        // Save
        PlayerInfo playerInfo = character.getPublicData();
        if (playerInfo == null) {
            playerInfo = new PlayerInfo();
        }
        Map<String, ShortcutDefinition> shortcuts = playerInfo.getShortcuts();
        if (shortcuts == null) {
            shortcuts = new LinkedHashMap<>();
        }
        shortcuts.put(body.slotKey(), shortcut);
        playerInfo.setShortcuts(shortcuts);
        character.setPublicData(playerInfo);
        characterService.updateCharater(character);

        log.info("Assigned shortcut: userId={}, characterId={}, slot={}, itemId={}", userId, characterId, body.slotKey(), body.itemId());
        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Clear a shortcut slot.
     */
    @PostMapping("/clear")
    @Operation(summary = "Clear a shortcut slot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shortcut cleared"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> clearShortcut(
            @RequestBody ClearShortcutRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("POST clear shortcut: worldId={}, userId={}, characterId={}, slotKey={}",
                worldId, userId, characterId, body != null ? body.slotKey() : null);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || Strings.isBlank(body.slotKey())) {
            return bad("slotKey required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerInfo playerInfo = character.getPublicData();
        if (playerInfo != null && playerInfo.getShortcuts() != null) {
            playerInfo.getShortcuts().remove(body.slotKey());
            character.setPublicData(playerInfo);
            characterService.updateCharater(character);
        }

        log.info("Cleared shortcut: userId={}, characterId={}, slot={}", userId, characterId, body.slotKey());
        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void notifyPlayer(String worldId, HttpServletRequest request) {
        String sessionId = (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);
        if (Strings.isBlank(sessionId)) {
            log.warn("No sessionId available, cannot notify player of shortcut change");
            return;
        }
        var wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot notify player of shortcut change", sessionId);
            return;
        }
        worldClientService.sendPlayerCommand(worldId, sessionId, wSession.get().getPlayerUrl(), "ShortcutModified", List.of(), null);
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

    /**
     * Assign a special action (not a backpack item) to a shortcut slot.
     */
    @PostMapping("/assign-action")
    @Operation(summary = "Assign a special action to a shortcut slot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Action shortcut assigned"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> assignActionShortcut(
            @RequestBody AssignActionRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("POST assign action shortcut: worldId={}, userId={}, characterId={}, slotKey={}, type={}",
                worldId, userId, characterId, body != null ? body.slotKey() : null, body != null ? body.type() : null);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || Strings.isBlank(body.slotKey()) || Strings.isBlank(body.type())) {
            return bad("slotKey and type required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        ShortcutDefinition shortcut = new ShortcutDefinition();
        shortcut.setType(body.type());
        shortcut.setName(body.name());
        shortcut.setIconPath(body.iconPath());
        shortcut.setWait(0);

        PlayerInfo playerInfo = character.getPublicData();
        if (playerInfo == null) {
            playerInfo = new PlayerInfo();
        }
        Map<String, ShortcutDefinition> shortcuts = playerInfo.getShortcuts();
        if (shortcuts == null) {
            shortcuts = new LinkedHashMap<>();
        }
        shortcuts.put(body.slotKey(), shortcut);
        playerInfo.setShortcuts(shortcuts);
        character.setPublicData(playerInfo);
        characterService.updateCharater(character);

        log.info("Assigned action shortcut: userId={}, characterId={}, slot={}, type={}", userId, characterId, body.slotKey(), body.type());
        notifyPlayer(worldId, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    record AssignShortcutRequest(String slotKey, String itemId) {}
    record AssignActionRequest(String slotKey, String type, String name, String iconPath) {}
    record ClearShortcutRequest(String slotKey) {}
}
