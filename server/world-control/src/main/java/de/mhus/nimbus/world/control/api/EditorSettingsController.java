package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.EditSettingsService;
import de.mhus.nimbus.world.control.service.WWorldEditSettings;
import de.mhus.nimbus.world.control.service.PaletteBlockDefinition;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for managing editor settings per world and user.
 * Stores user-specific editor configuration like block palettes.
 */
@RestController
@RequestMapping("/control/editor/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "EditorSettings", description = "Editor settings per world and user")
public class EditorSettingsController extends BaseEditorController {

    private final EditSettingsService editSettingsService;
    private final WSessionService wSessionService;

    /**
     * GET /control/editor/settings/worlds/{worldId}/editsettings
     * Load settings for world and current user.
     *
     * @param worldId World identifier
     * @param sessionId Session ID to get user from
     * @return Editor settings including palette
     */
    @GetMapping("/worlds/{worldId}/editsettings")
    @Operation(summary = "Load editor settings", description = "Load editor settings for a world and the current user")
    public ResponseEntity<?> getEditSettings(
            @PathVariable String worldId,
            @RequestParam String sessionId) {

        // Validate worldId
        Optional<WorldId> worldIdOpt = WorldId.of(worldId);
        if (worldIdOpt.isEmpty()) {
            return bad("Invalid worldId: " + worldId);
        }

        ResponseEntity<?> validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        // Get session to extract userId
        Optional<WSession> sessionOpt = wSessionService.get(sessionId);
        if (sessionOpt.isEmpty()) {
            return notFound("Session not found: " + sessionId);
        }

        WSession session = sessionOpt.get();
        String userId = session.getPlayerId(); // Note: playerId is used as userId according to WSession

        // Get or create settings
        WWorldEditSettings settings = editSettingsService.getOrCreateSettings(worldId, userId);

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("worldId", settings.getWorldId());
        response.put("userId", settings.getUserId());
        response.put("palette", settings.getPalette());
        response.put("createdAt", settings.getCreatedAt());
        response.put("updatedAt", settings.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    /**
     * POST /control/editor/settings/worlds/{worldId}/editsettings/palette
     * Set palette for world and current user.
     * Always replaces the entire palette.
     *
     * @param worldId World identifier
     * @param sessionId Session ID to get user from
     * @param palette List of palette block definitions
     * @return Updated settings
     */
    @PostMapping("/worlds/{worldId}/editsettings/palette")
    @Operation(summary = "Set palette", description = "Set the palette for a world and the current user (replaces entire palette)")
    public ResponseEntity<?> setPalette(
            @PathVariable String worldId,
            @RequestParam String sessionId,
            @RequestBody List<PaletteBlockDefinition> palette) {

        // Validate worldId
        Optional<WorldId> worldIdOpt = WorldId.of(worldId);
        if (worldIdOpt.isEmpty()) {
            return bad("Invalid worldId: " + worldId);
        }

        ResponseEntity<?> validation = validateId(sessionId, "sessionId");
        if (validation != null) return validation;

        // Get session to extract userId
        Optional<WSession> sessionOpt = wSessionService.get(sessionId);
        if (sessionOpt.isEmpty()) {
            return notFound("Session not found: " + sessionId);
        }

        WSession session = sessionOpt.get();
        String userId = session.getPlayerId(); // Note: playerId is used as userId according to WSession

        // Validate palette
        if (palette == null) {
            return bad("Palette is required");
        }

        // Update palette
        WWorldEditSettings settings = editSettingsService.updatePalette(worldId, userId, palette);

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("worldId", settings.getWorldId());
        response.put("userId", settings.getUserId());
        response.put("palette", settings.getPalette());
        response.put("paletteSize", settings.getPalette().size());
        response.put("updatedAt", settings.getUpdatedAt());

        log.info("Palette updated: worldId={}, userId={}, size={}", worldId, userId, palette.size());

        return ResponseEntity.ok(response);
    }
}
