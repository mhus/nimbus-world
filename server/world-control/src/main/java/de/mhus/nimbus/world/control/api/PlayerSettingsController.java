package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.Settings;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.session.WSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for player settings management.
 * Allows players to view and update their own settings and title.
 * Accessible by players under /control/player/settings.
 */
@RestController
@RequestMapping("/control/player/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Settings", description = "Player settings management")
public class PlayerSettingsController extends BaseEditorController {

    private final RUserService rUserService;
    private final WorldClientService worldClientService;
    private final WSessionService wSessionService;

    /**
     * Get the player's settings for a given client type, including the user title.
     */
    @GetMapping
    @Operation(summary = "Get player settings for client type")
    public ResponseEntity<?> getSettings(
            @RequestParam(name = "client", defaultValue = "web") String clientType,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        if (Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        log.debug("GET player settings: userId={}, clientType={}", userId, clientType);

        RUser user = rUserService.getByUsername(userId).orElse(null);
        if (user == null) {
            return notFound("User not found");
        }

        Settings settings = user.getSettingsForClientType(clientType);
        if (settings == null) {
            settings = new Settings();
            settings.setProperties(new HashMap<>());
        }
        if (settings.getProperties() == null) {
            settings.setProperties(new HashMap<>());
        }

        String title = user.getPublicData() != null ? user.getPublicData().getTitle() : null;

        return ResponseEntity.ok(Map.of(
                "title", title != null ? title : "",
                "settings", settings
        ));
    }

    /**
     * Update the player's settings properties for a given client type.
     */
    @PutMapping
    @Operation(summary = "Update player settings properties")
    public ResponseEntity<?> updateSettings(
            @RequestParam(name = "client", defaultValue = "web") String clientType,
            @RequestBody UpdatePropertiesRequest body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        if (Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.properties() == null) {
            return bad("properties required");
        }

        log.debug("PUT player settings: userId={}, clientType={}, properties={}", userId, clientType, body.properties());

        RUser user = rUserService.getByUsername(userId).orElse(null);
        if (user == null) {
            return notFound("User not found");
        }

        Settings settings = user.getSettingsForClientType(clientType);
        if (settings == null) {
            settings = new Settings();
        }
        settings.setProperties(new HashMap<>(body.properties()));
        rUserService.setSettingsForClientType(userId, clientType, settings);

        log.info("Updated settings properties: userId={}, clientType={}", userId, clientType);

        notifyPlayer(request);

        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Update the player's display title.
     */
    @PutMapping("/title")
    @Operation(summary = "Update player display title")
    public ResponseEntity<?> updateTitle(
            @RequestBody UpdateTitleRequest body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        if (Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.title() == null) {
            return bad("title required");
        }

        log.debug("PUT player title: userId={}, title={}", userId, body.title());

        RUser user = rUserService.getByUsername(userId).orElse(null);
        if (user == null) {
            return notFound("User not found");
        }

        if (user.getPublicData() == null) {
            user.setPublicData(new de.mhus.nimbus.shared.types.PlayerUser());
        }
        user.getPublicData().setTitle(body.title());
        user.touchUpdate();
        rUserService.save(user);

        log.info("Updated title: userId={}, title={}", userId, body.title());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Notify the player's engine client that settings have changed.
     * Sends a "SettingsModified" command via WebSocket.
     */
    private void notifyPlayer(HttpServletRequest request) {
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String sessionId = (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);
        if (Strings.isBlank(sessionId) || Strings.isBlank(worldId)) {
            log.warn("No sessionId or worldId available, cannot notify player of settings change");
            return;
        }
        var wSession = wSessionService.getWithPlayerUrl(sessionId);
        if (wSession.isEmpty() || Strings.isBlank(wSession.get().getPlayerUrl())) {
            log.warn("No player URL available for session {}, cannot notify player of settings change", sessionId);
            return;
        }
        worldClientService.sendPlayerCommand(worldId, sessionId, wSession.get().getPlayerUrl(), "SettingsModified", List.of(), null);
    }

    record UpdatePropertiesRequest(Map<String, String> properties) {}
    record UpdateTitleRequest(String title) {}
}
