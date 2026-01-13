package de.mhus.nimbus.world.shared.world;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Asset metadata from .info files in test_server.
 * Contains AI-generated descriptions and visual properties.
 *
 * Example from textures/items/armor_boots_crusty.png.info:
 * {
 *   "description": "...",
 *   "width": 16,
 *   "height": 16,
 *   "color": "#222021"
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
}
