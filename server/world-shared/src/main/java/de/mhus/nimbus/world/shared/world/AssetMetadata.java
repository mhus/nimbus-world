package de.mhus.nimbus.world.shared.world;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Asset metadata from .info files in test_server.
 * Contains AI-generated descriptions and visual properties.
 * <p>
 * This is an open key-value structure with predefined common fields.
 * Additional custom fields can be added via the properties map.
 * <p>
 * Example from textures/items/armor_boots_crusty.png.info:
 * {
 *   "description": "...",
 *   "width": 16,
 *   "height": 16,
 *   "color": "#222021",
 *   "prompt": "original generation prompt"  // custom field
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetMetadata {

    /**
     * AI-generated description of the asset.
     */
    private String description;

    /**
     * Image width in pixels (for images).
     */
    private Integer width;

    /**
     * Image height in pixels (for images).
     */
    private Integer height;

    /**
     * Primary/dominant color (hex format, e.g., "#222021").
     */
    private String color;

    /**
     * MIME type (e.g., "image/png", "audio/wav").
     */
    private String mimeType;

    /**
     * Asset category (e.g., "textures", "models", "audio").
     */
    private String category;

    /**
     * File extension (e.g., ".png", ".wav").
     */
    private String extension;

    /**
     * Source/Origin of the asset.
     */
    private String source;

    /**
     * Author/Creator of the asset.
     */
    private String author;

    /**
     * License information for the asset.
     */
    private String license;

    /**
     * If true, license fields (source, author, license) are read-only in editors.
     */
    private Boolean licenseFixed;

    /**
     * Custom properties for additional metadata fields.
     * This allows for open key-value pairs beyond the predefined fields.
     * Use setProperty() and getProperty() for type-safe access.
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Jackson annotation for serializing custom properties.
     * Flattens the properties map into the JSON root.
     */
    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Jackson annotation for deserializing custom properties.
     * Captures any JSON field not mapped to a known field.
     */
    @JsonAnySetter
    public void setProperty(String key, Object value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }

    /**
     * Get a custom property value.
     *
     * @param key Property key
     * @return Property value or null if not found
     */
    public Object getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }

    /**
     * Get a custom property value as String.
     *
     * @param key Property key
     * @return Property value as String or null
     */
    public String getPropertyAsString(String key) {
        Object value = getProperty(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Check if a custom property exists.
     *
     * @param key Property key
     * @return true if property exists
     */
    public boolean hasProperty(String key) {
        return properties != null && properties.containsKey(key);
    }
}
