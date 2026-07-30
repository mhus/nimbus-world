package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST Controller for player-related operations in world-control.
 * Provides access to player information for editing purposes.
 */
@RestController
@RequestMapping("/control/player")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player", description = "Player information management")
public class PlayerController extends BaseEditorController {

    private final RCharacterService characterService;

    /**
     * Ensures the requested player identity matches the authenticated session.
     * Prevents a player from reading or modifying another player's data (IDOR):
     * worldId/userId/characterId must all equal the session attributes set by the
     * access filter, otherwise access is denied.
     *
     * @return a 403 response on mismatch, or {@code null} when ownership is confirmed
     */
    private ResponseEntity<?> verifyOwnership(HttpServletRequest request, String worldId, String userId, String characterId) {
        String sessionUserId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String sessionWorldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String sessionCharacterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (Strings.isBlank(sessionUserId) || Strings.isBlank(sessionWorldId) || Strings.isBlank(sessionCharacterId)) {
            return ResponseEntity.status(403).body("No authenticated player session");
        }
        if (!sessionUserId.equals(userId) || !sessionWorldId.equals(worldId) || !sessionCharacterId.equals(characterId)) {
            log.warn("Denied cross-player access: session={}:{}:{} requested={}:{}:{}",
                    sessionWorldId, sessionUserId, sessionCharacterId, worldId, userId, characterId);
            return ResponseEntity.status(403).body("Access denied");
        }
        return null;
    }

    /**
     * Get PlayerInfo for a specific player.
     * GET /control/player/playerinfo/{worldId}/{playerId}
     *
     * @param worldId The world ID (format: regionId:worldName)
     * @param playerId The player ID (format: userId:characterId)
     * @return PlayerInfo entity
     */
    @GetMapping("/playerinfo/{worldId}/{playerId}")
    @Operation(summary = "Get PlayerInfo by worldId and playerId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PlayerInfo found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "404", description = "Player not found")
    })
    public ResponseEntity<?> getPlayerInfo(
            @Parameter(description = "World ID in format regionId:worldName")
            @PathVariable String worldId,
            @Parameter(description = "Player ID in format userId:characterId")
            @PathVariable String playerId,
            HttpServletRequest request) {

        log.debug("GET playerinfo: worldId={}, playerId={}", worldId, playerId);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }

        if (Strings.isBlank(playerId)) {
            return bad("playerId required");
        }

        // Parse worldId to extract regionId (format: regionId:worldName)
        String[] worldParts = worldId.split(":");
        if (worldParts.length < 1) {
            return bad("Invalid worldId format. Expected: regionId:worldName");
        }
        String regionId = worldParts[0];

        // Parse playerId format: userId:characterId
        String[] playerParts = playerId.split(":");
        if (playerParts.length != 2) {
            return bad("Invalid playerId format. Expected: userId:characterId");
        }

        String userId = playerParts[0];
        String characterId = playerParts[1];

        log.debug("Parsed: userId={}, regionId={}, characterId={}", userId, regionId, characterId);

        var ownership = verifyOwnership(request, worldId, userId, characterId);
        if (ownership != null) return ownership;

        // Get character from database
        Optional<RCharacter> characterOpt = characterService.getCharacter(userId, regionId, characterId);
        if (characterOpt.isEmpty()) {
            log.warn("Character not found: userId={}, regionId={}, characterId={}", userId, regionId, characterId);
            return notFound("Character not found");
        }

        RCharacter character = characterOpt.get();

        // Return PlayerInfo from character's publicData
        PlayerInfo playerInfo = character.getPublicData();
        if (playerInfo == null) {
            log.warn("PlayerInfo is null for character: {}", characterId);
            return notFound("PlayerInfo not found");
        }

        log.debug("Returning PlayerInfo for playerId={}", playerId);
        return ResponseEntity.ok(playerInfo);
    }

    /**
     * Update PlayerInfo for a specific player.
     * PUT /control/player/playerinfo/{worldId}/{playerId}
     *
     * @param worldId The world ID (format: regionId:worldName)
     * @param playerId The player ID (format: userId:characterId)
     * @param playerInfo Updated PlayerInfo
     * @return Updated PlayerInfo
     */
    @PutMapping("/playerinfo/{worldId}/{playerId}")
    @Operation(summary = "Update PlayerInfo by worldId and playerId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PlayerInfo updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Player not found")
    })
    public ResponseEntity<?> updatePlayerInfo(
            @Parameter(description = "World ID in format regionId:worldName")
            @PathVariable String worldId,
            @Parameter(description = "Player ID in format userId:characterId")
            @PathVariable String playerId,
            @RequestBody PlayerInfo playerInfo,
            HttpServletRequest request) {

        log.debug("PUT playerinfo: worldId={}, playerId={}", worldId, playerId);

        if (Strings.isBlank(worldId)) {
            return bad("worldId required");
        }

        if (Strings.isBlank(playerId)) {
            return bad("playerId required");
        }

        if (playerInfo == null) {
            return bad("PlayerInfo required");
        }

        // Parse worldId to extract regionId (format: regionId:worldName)
        String[] worldParts = worldId.split(":");
        if (worldParts.length < 1) {
            return bad("Invalid worldId format. Expected: regionId:worldName");
        }
        String regionId = worldParts[0];

        // Parse playerId format: userId:characterId
        String[] playerParts = playerId.split(":");
        if (playerParts.length != 2) {
            return bad("Invalid playerId format. Expected: userId:characterId");
        }

        String userId = playerParts[0];
        String characterId = playerParts[1];

        log.debug("Parsed: userId={}, regionId={}, characterId={}", userId, regionId, characterId);

        var ownership = verifyOwnership(request, worldId, userId, characterId);
        if (ownership != null) return ownership;

        // Get character from database
        Optional<RCharacter> characterOpt = characterService.getCharacter(userId, regionId, characterId);
        if (characterOpt.isEmpty()) {
            log.warn("Character not found: userId={}, regionId={}, characterId={}", userId, regionId, characterId);
            return notFound("Character not found");
        }

        RCharacter character = characterOpt.get();

        // Update PlayerInfo
        character.setPublicData(playerInfo);
        characterService.updateCharater(character);

        log.info("Updated PlayerInfo for playerId={}", playerId);
        return ResponseEntity.ok(character.getPublicData());
    }
}
