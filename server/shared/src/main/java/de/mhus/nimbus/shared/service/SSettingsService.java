package de.mhus.nimbus.shared.service;

import de.mhus.nimbus.shared.persistence.SSettings;
import de.mhus.nimbus.shared.persistence.SSettingsRepository;
import de.mhus.nimbus.shared.settings.SettingBoolean;
import de.mhus.nimbus.shared.settings.SettingDouble;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.shared.settings.SettingOptions;
import de.mhus.nimbus.shared.settings.SettingPassword;
import de.mhus.nimbus.shared.settings.SettingString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class SSettingsService {

    private final SSettingsRepository repository;
    private final AesEncryptionService encryptionService;

    /**
     * Get a setting by key
     */
    public Optional<SSettings> getSetting(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is blank");
        }
        return repository.findByKey(key).stream().findFirst();
    }

    /**
     * Get all settings
     */
    public List<SSettings> getAllSettings() {
        return repository.findAll();
    }

    /**
     * Get all settings of a specific type
     */
    public List<SSettings> getSettingsByType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is blank");
        }
        return repository.findByType(type);
    }

    /**
     * Save or update a setting
     */
    public SSettings saveSetting(SSettings setting) {
        if (setting == null) {
            throw new IllegalArgumentException("setting is null");
        }
        if (setting.getKey() == null || setting.getKey().isBlank()) {
            throw new IllegalArgumentException("setting key is blank");
        }
        return repository.save(setting);
    }

    /**
     * Create or update a setting with key, value, and type
     */
    public SSettings setSetting(String key, String value, String type) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is blank");
        }

        List<SSettings> existingOpt = repository.findByKey(key);
        SSettings setting;

        if (!existingOpt.isEmpty()) {
            setting = existingOpt.getFirst();
            setting.setValue(value);
            if (type != null) {
                setting.setType(type);
            }
        } else {
            setting = new SSettings(key, value, type != null ? type : "string");
        }
        try {
            return repository.save(setting);
        } catch (Exception e) {
            log.info("Failed to set setting '{}': {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Create or update a setting with full details
     */
    public SSettings setSetting(String key, String value, String type, String defaultValue, String description, Map<String, String> options) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is blank");
        }

        List<SSettings> existingOpt = repository.findByKey(key);
        SSettings setting;

        if (!existingOpt.isEmpty()) {
            setting = existingOpt.getFirst();
            setting.setValue(value);
            if (type != null) {
                setting.setType(type);
            }
            if (defaultValue != null) {
                setting.setDefaultValue(defaultValue);
            }
            if (description != null) {
                setting.setDescription(description);
            }
            if (options != null) {
                setting.setOptions(options);
            }
        } else {
            setting = new SSettings(key, value, type != null ? type : "string", defaultValue, description);
            if (options != null) {
                setting.setOptions(options);
            }
        }

        return repository.save(setting);
    }

    /**
     * Delete a setting by key
     */
    public void deleteSetting(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is blank");
        }
        repository.deleteByKey(key);
    }

    /**
     * Check if a setting exists
     */
    public boolean existsSetting(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return repository.existsByKey(key);
    }

    // ==================== Typed Getter Methods ====================

    /**
     * Get a string value by key.
     * Returns the value if found, otherwise returns the defaultValue if set, otherwise returns null.
     */
    public String getStringValue(String key) {
        Optional<SSettings> settingOpt = getSetting(key);
        if (settingOpt.isEmpty()) {
            return null;
        }
        SSettings setting = settingOpt.get();
        return setting.getValue() != null ? setting.getValue() : setting.getDefaultValue();
    }

    /**
     * Get a string value by key with a fallback default.
     */
    public String getStringValue(String key, String defaultValue) {
        String value = getStringValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a boolean value by key.
     * Recognizes: "true", "1", "yes", "on" as true (case-insensitive).
     * Returns false if the setting doesn't exist or cannot be parsed.
     */
    public boolean getBooleanValue(String key) {
        return getBooleanValue(key, false);
    }

    /**
     * Get a boolean value by key with a fallback default.
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        String value = getStringValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        value = value.trim().toLowerCase();
        return "true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value);
    }

    /**
     * Get an integer value by key.
     * Returns 0 if the setting doesn't exist or cannot be parsed.
     */
    public int getIntValue(String key) {
        return getIntValue(key, 0);
    }

    /**
     * Get an integer value by key with a fallback default.
     */
    public int getIntValue(String key, int defaultValue) {
        String value = getStringValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer value for key '{}': {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Get a long value by key.
     * Returns 0 if the setting doesn't exist or cannot be parsed.
     */
    public long getLongValue(String key) {
        return getLongValue(key, 0L);
    }

    /**
     * Get a long value by key with a fallback default.
     */
    public long getLongValue(String key, long defaultValue) {
        String value = getStringValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long value for key '{}': {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Get a double value by key.
     * Returns 0.0 if the setting doesn't exist or cannot be parsed.
     */
    public double getDoubleValue(String key) {
        return getDoubleValue(key, 0.0);
    }

    /**
     * Get a double value by key with a fallback default.
     */
    public double getDoubleValue(String key, double defaultValue) {
        String value = getStringValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double value for key '{}': {}", key, value);
            return defaultValue;
        }
    }

    // ==================== Typed Setter Methods ====================

    /**
     * Set a string setting
     */
    public SSettings setStringValue(String key, String value) {
        return setSetting(key, value, "string");
    }

    /**
     * Set a boolean setting
     */
    public SSettings setBooleanValue(String key, boolean value) {
        return setSetting(key, String.valueOf(value), "boolean");
    }

    /**
     * Set an integer setting
     */
    public SSettings setIntValue(String key, int value) {
        return setSetting(key, String.valueOf(value), "int");
    }

    /**
     * Set a long setting
     */
    public SSettings setLongValue(String key, long value) {
        return setSetting(key, String.valueOf(value), "long");
    }

    /**
     * Set a double setting
     */
    public SSettings setDoubleValue(String key, double value) {
        return setSetting(key, String.valueOf(value), "double");
    }

    /**
     * Set a secret setting (will be marked as sensitive)
     */
    public SSettings setSecretValue(String key, String value) {
        return setSetting(key, value, "secret");
    }

    public SettingBoolean getBoolean(String key, boolean defaultValue) {
        return new SettingBoolean(key, this, defaultValue);
    }

    public SettingInteger getInteger(String key, int defaultValue) {
        return new SettingInteger(key, this, defaultValue);
    }

    public SettingString getString(String key, String defaultValue) {
        return new SettingString(key, this, defaultValue, null);
    }

    public SettingString getString(String key, String defaultValue, String overwriteValue) {
        return new SettingString(key, this, defaultValue, overwriteValue);
    }

    public SettingPassword getPassword(String key) {
        return new SettingPassword(key, this, "");
    }

    public SettingOptions getOptions(String key, String defaultValue, String... options) {
        return new SettingOptions(key, this, defaultValue, options);
    }

    public SettingDouble getDouble(String key, double defaultValue) {
        return new SettingDouble(key, this, defaultValue);
    }

    // ==================== Password/Encryption Methods ====================

    /**
     * Set an encrypted password setting.
     * Uses AES-256-GCM encryption with password-based key derivation.
     */
    public SSettings setEncryptedPassword(String key, String plainPassword) {
        if (plainPassword == null) {
            return setSetting(key, null, "password");
        }
        try {
            String encrypted = encryptionService.encrypt(plainPassword);
            return setSetting(key, encrypted, "password");
        } catch (Exception e) {
            log.error("Failed to encrypt password for key '{}': {}", key, e.getMessage());
            throw new RuntimeException("Failed to encrypt password", e);
        }
    }

    /**
     * Get a decrypted password setting.
     * Uses AES-256-GCM decryption with password-based key derivation.
     */
    public String getDecryptedPassword(String key) {
        String encrypted = getStringValue(key);
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            return encryptionService.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("Failed to decrypt password for key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    public boolean getOrCreateBooleanValue(String key, boolean defaultValue) {
        Optional<SSettings> settingOpt = getSetting(key);
        if (settingOpt.isPresent()) {
            return getBooleanValue(key, defaultValue);
        } else {
            setBooleanValue(key, defaultValue);
            return defaultValue;
        }
    }

    public double getOrCreateDoubleValue(String key, double defaultValue) {
        Optional<SSettings> settingOpt = getSetting(key);
        if (settingOpt.isPresent()) {
            return getDoubleValue(key, defaultValue);
        } else {
            setDoubleValue(key, defaultValue);
            return defaultValue;
        }
    }

    public int getOrCreateIntValue(String key, int defaultValue) {
        Optional<SSettings> settingOpt = getSetting(key);
        if (settingOpt.isPresent()) {
            return getIntValue(key, defaultValue);
        } else {
            setIntValue(key, defaultValue);
            return defaultValue;
        }
    }

    public String getOrCreateStringValue(String key, String defaultValue) {
        Optional<SSettings> settingOpt = getSetting(key);
        if (settingOpt.isPresent()) {
            return getStringValue(key, defaultValue);
        } else {
            setStringValue(key, defaultValue);
            return defaultValue;
        }
    }
}
