package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.Settings;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

    record UpdatePropertiesRequest(Map<String, String> properties) {}
    record UpdateTitleRequest(String title) {}
}
