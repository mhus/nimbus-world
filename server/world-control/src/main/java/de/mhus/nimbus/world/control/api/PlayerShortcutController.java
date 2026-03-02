package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for player shortcut operations.
 * Provides access to editor shortcut templates and player shortcut management.
 * Accessible by players under /control/player/shortcut.
 *
 * worldId and playerId are extracted from the session cookie via ControlAccessFilter.
 */
@RestController
@RequestMapping("/control/player/shortcut")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Shortcuts", description = "Player editor shortcut management")
public class PlayerShortcutController extends BaseEditorController {

    private final WAnythingService anythingService;
    private final RCharacterService characterService;

    /**
     * Get available shortcut templates for the current player's world.
     * Templates are stored as WAnything entities in collection 'editorShortcuts' scoped to the region.
     */
    @GetMapping("/templates")
    @Operation(summary = "Get shortcut templates for current world")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Templates found"),
            @ApiResponse(responseCode = "400", description = "Not authenticated")
    })
    public ResponseEntity<?> getTemplates(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);

        log.debug("GET shortcut templates: worldId={}", worldId);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required - not authenticated");
        }

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return bad("Invalid worldId format");
        }

        // Build region collection worldId: @region:regionId
        String regionWorldId = WorldId.of(WorldId.COLLECTION_REGION, parsedWorldId.getRegionId()).orElseThrow().getId();

        List<WAnything> templates = anythingService.findByWorldIdAndCollectionAndEnabled(
                regionWorldId, "editorShortcuts", true);

        var result = templates.stream().map(entity -> Map.of(
                "name", entity.getName() != null ? entity.getName() : "",
                "title", entity.getTitle() != null ? entity.getTitle() : "",
                "description", entity.getDescription() != null ? entity.getDescription() : "",
                "type", entity.getType() != null ? entity.getType() : "",
                "data", entity.getData() != null ? entity.getData() : Map.of()
        )).toList();

        return ResponseEntity.ok(Map.of("templates", result));
    }

    /**
     * Get editor shortcuts for the current player.
     */
    @GetMapping
    @Operation(summary = "Get editor shortcuts for current player")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shortcuts found"),
            @ApiResponse(responseCode = "400", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Player not found")
    })
    public ResponseEntity<?> getShortcuts(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("GET shortcuts: worldId={}, userId={}, characterId={}", worldId, userId, characterId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerInfo playerInfo = character.getPublicData();
        Map<String, ShortcutDefinition> shortcuts = playerInfo != null ? playerInfo.getEditorShortcuts() : null;

        return ResponseEntity.ok(Map.of(
                "editorShortcuts", shortcuts != null ? shortcuts : Map.of()
        ));
    }

    /**
     * Update editor shortcuts for the current player.
     */
    @PutMapping
    @Operation(summary = "Update editor shortcuts for current player")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shortcuts updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Player not found")
    })
    public ResponseEntity<?> updateShortcuts(
            @RequestBody UpdateShortcutsRequest body,
            HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        log.debug("PUT shortcuts: worldId={}, userId={}, characterId={}", worldId, userId, characterId);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.editorShortcuts() == null) {
            return bad("editorShortcuts required");
        }

        var character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        PlayerInfo playerInfo = character.getPublicData();
        if (playerInfo == null) {
            playerInfo = new PlayerInfo();
        }

        playerInfo.setEditorShortcuts(body.editorShortcuts());
        character.setPublicData(playerInfo);
        characterService.updateCharater(character);

        log.info("Updated editor shortcuts: userId={}, characterId={}", userId, characterId);
        return ResponseEntity.ok(Map.of(
                "editorShortcuts", playerInfo.getEditorShortcuts() != null ? playerInfo.getEditorShortcuts() : Map.of()
        ));
    }

    /**
     * Find character by worldId, userId and characterId.
     */
    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) {
            return null;
        }
        String regionId = parsedWorldId.getRegionId();

        Optional<RCharacter> characterOpt = characterService.getCharacter(userId, regionId, characterId);
        return characterOpt.orElse(null);
    }

    record UpdateShortcutsRequest(Map<String, ShortcutDefinition> editorShortcuts) {}
}
