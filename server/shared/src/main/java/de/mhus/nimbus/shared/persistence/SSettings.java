package de.mhus.nimbus.shared.persistence;

import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity for storing global settings.
 * Settings are stored as key-value pairs with type information.
 */
@Document(collection = "s_settings")
@ActualSchemaVersion("1.0.0")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "settings_key_idx", def = "{ 'key': 1 }", unique = true)
public class SSettings implements Identifiable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;

    private String value;

    /**
     * Type of the setting value.
     * Supported types: 'string', 'secret', 'boolean', 'int', 'double', 'long', 'json'
     */
    private String type;

    /**
     * Options for the setting (e.g., for dropdown menus).
     * Map of value -> title
     */
    private Map<String, String> options;

    private String defaultValue;

    private String description;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public SSettings(String key, String value, String type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }

    public SSettings(String key, String value, String type, String defaultValue, String description) {
        this.key = key;
        this.value = value;
        this.type = type;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    public Map<String, String> getOptions() {
        if (options == null) {
            options = new HashMap<>();
        }
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = (options == null || options.isEmpty()) ? null : new HashMap<>(options);
    }

    public void addOption(String value, String title) {
        if (options == null) {
            options = new HashMap<>();
        }
        options.put(value, title);
    }

    public void removeOption(String value) {
        if (options != null) {
            options.remove(value);
            if (options.isEmpty()) {
                options = null;
            }
        }
    }

    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }

    public Map<String, String> getOptionsReadOnly() {
        return options == null ? Collections.emptyMap() : Collections.unmodifiableMap(options);
    }
}
