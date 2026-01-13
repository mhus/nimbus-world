package de.mhus.nimbus.world.control.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mhus.nimbus.shared.persistence.SSettings;
import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for managing global SSettings.
 * Settings have no world selector - they are global across the application.
 */
@RestController
@RequestMapping("/control/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Settings", description = "Global application settings management")
public class SettingsController extends BaseEditorController {

    private final SSettingsService settingsService;

    /**
     * GET /control/settings
     * Get all settings.
     * Password and secret values are masked for security.
     */
    @GetMapping
    @Operation(summary = "Get all settings", description = "Returns all global settings")
    public ResponseEntity<List<SSettings>> getAllSettings() {
        List<SSettings> settings = settingsService.getAllSettings();
        // Mask password and secret values
        settings.forEach(this::maskSensitiveValue);
        return ResponseEntity.ok(settings);
    }

    /**
     * GET /control/settings/{key}
     * Get a single setting by key.
     * Password and secret values are masked for security.
     */
    @GetMapping("/{key}")
    @Operation(summary = "Get setting by key", description = "Returns a single setting by its key")
    public ResponseEntity<?> getSetting(@PathVariable String key) {
        ResponseEntity<?> validation = validateId(key, "key");
        if (validation != null) return validation;

        Optional<SSettings> setting = settingsService.getSetting(key);
        if (setting.isEmpty()) {
            return notFound("Setting not found: " + key);
        }

        SSettings maskedSetting = setting.get();
        maskSensitiveValue(maskedSetting);
        return ResponseEntity.ok(maskedSetting);
    }

    /**
     * POST /control/settings
     * Create a new setting.
     */
    @PostMapping
    @Operation(summary = "Create setting", description = "Creates a new global setting")
    public ResponseEntity<?> createSetting(@RequestBody CreateSettingRequest request) {
        if (request.key() == null || request.key().isBlank()) {
            return bad("key is required");
        }

        if (settingsService.existsSetting(request.key())) {
            return bad("Setting already exists: " + request.key());
        }

        SSettings setting = settingsService.setSetting(
                request.key(),
                request.value(),
                request.type(),
                request.defaultValue(),
                request.description(),
                request.options()
        );

        log.info("Setting created: key={}, type={}", request.key(), request.type());
        return ResponseEntity.ok(setting);
    }

    /**
     * PUT /control/settings/{key}
     * Update an existing setting.
     * For password/secret types: empty values are ignored (password not changed).
     */
    @PutMapping("/{key}")
    @Operation(summary = "Update setting", description = "Updates an existing global setting")
    public ResponseEntity<?> updateSetting(
            @PathVariable String key,
            @RequestBody UpdateSettingRequest request) {

        ResponseEntity<?> validation = validateId(key, "key");
        if (validation != null) return validation;

        if (!settingsService.existsSetting(key)) {
            return notFound("Setting not found: " + key);
        }

        // For password/secret types: ignore empty values (don't overwrite existing password)
        String valueToSet = request.value();
        if (("password".equals(request.type()) || "secret".equals(request.type()))) {
            if (valueToSet == null || valueToSet.isBlank()) {
                // Don't update the password if empty - keep existing value
                Optional<SSettings> existing = settingsService.getSetting(key);
                if (existing.isPresent()) {
                    valueToSet = existing.get().getValue();
                }
            }
        }

        SSettings setting = settingsService.setSetting(
                key,
                valueToSet,
                request.type(),
                request.defaultValue(),
                request.description(),
                request.options()
        );

        log.info("Setting updated: key={}, type={}", key, request.type());
        return ResponseEntity.ok(setting);
    }

    /**
     * DELETE /control/settings/{key}
     * Delete a setting.
     */
    @DeleteMapping("/{key}")
    @Operation(summary = "Delete setting", description = "Deletes a global setting")
    public ResponseEntity<?> deleteSetting(@PathVariable String key) {
        ResponseEntity<?> validation = validateId(key, "key");
        if (validation != null) return validation;

        if (!settingsService.existsSetting(key)) {
            return notFound("Setting not found: " + key);
        }

        settingsService.deleteSetting(key);
        log.info("Setting deleted: key={}", key);

        return ResponseEntity.ok(Map.of("message", "Setting deleted successfully"));
    }

    /**
     * Masks sensitive values (password, secret) before sending to client.
     * Sets value to empty string to prevent accidentally saving the masked value.
     */
    private void maskSensitiveValue(SSettings setting) {
        if (setting == null) {
            return;
        }
        String type = setting.getType();
        if ("password".equals(type) || "secret".equals(type)) {
            if (setting.getValue() != null && !setting.getValue().isBlank()) {
                setting.setValue("");
            }
        }
    }

    /**
     * Request for creating a new setting.
     */
    public record CreateSettingRequest(
            String key,
            String value,
            String type,
            String defaultValue,
            String description,
            Map<String, String> options
    ) {}

    /**
     * Request for updating an existing setting.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateSettingRequest(
            String value,
            String type,
            String defaultValue,
            String description,
            Map<String, String> options
    ) {}
}
