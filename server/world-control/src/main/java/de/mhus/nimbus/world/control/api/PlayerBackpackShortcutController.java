package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.generated.types.ItemType;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
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

                // Fallback to ItemType for texture, name and description
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
     * Update player shortcuts.
     */
    @PutMapping
    @Operation(summary = "Update player shortcuts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shortcuts updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Character not found")
    })
    public ResponseEntity<?> updateShortcuts(
            @RequestBody UpdateShortcutsRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("PUT player shortcuts: worldId={}, userId={}, characterId={}", worldId, userId, characterId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.shortcuts() == null) {
            return bad("shortcuts required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerInfo playerInfo = character.getPublicData();
        if (playerInfo == null) {
            playerInfo = new PlayerInfo();
        }

        playerInfo.setShortcuts(body.shortcuts());
        character.setPublicData(playerInfo);
        characterService.updateCharater(character);

        log.info("Updated player shortcuts: userId={}, characterId={}", userId, characterId);
        return ResponseEntity.ok(Map.of(
                "shortcuts", playerInfo.getShortcuts() != null ? playerInfo.getShortcuts() : Map.of()
        ));
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

    record UpdateShortcutsRequest(Map<String, ShortcutDefinition> shortcuts) {}
}
