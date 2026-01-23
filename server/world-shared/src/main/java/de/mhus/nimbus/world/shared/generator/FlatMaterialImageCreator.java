package de.mhus.nimbus.world.shared.generator;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Helper class for creating material (block type) images from WFlat data.
 * Generates PNG images showing terrain materials with color-coded block types.
 * <p>
 * Usage: new FlatMaterialImageCreator(flat).create(ignoreEmptyMaterial)
 */
@RequiredArgsConstructor
public class FlatMaterialImageCreator {

    private final WFlat flat;

    /**
     * Create material map image from flat data.
     * Each block type ID gets a unique color.
     *
     * @param ignoreEmptyMaterial If true, renders black pixels where material == 0 (NOT_SET)
     * @return PNG image as byte array
     * @throws IOException If image generation fails
     */
    public byte[] create(boolean ignoreEmptyMaterial) throws IOException {
        int width = flat.getSizeX();
        int height = flat.getSizeZ();
        byte[] columns = flat.getColumns();

        // Create image
        BufferedImage image = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_RGB);

        // Draw block map
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int blockTypeId = columns[index] & 0xFF; // Convert to unsigned

                int rgb;

                // Check if material is 0 (NOT_SET) and ignoreEmptyMaterial is enabled
                if (ignoreEmptyMaterial && blockTypeId == 0) {
                    // Render as black
                    rgb = 0x000000;
                } else {
                    rgb = getBlockColor(blockTypeId);
                }

                image.setRGB(x, z, rgb);
            }
        }

        // Convert to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Get RGB color for block type ID.
     */
    private int getBlockColor(int id) {
        if (id == 0) return 0x000000; // Black for air (NOT_SET)
        if (id == 255) return 0x808080; // Medium gray for NOT_SET_MUTABLE

        // Predefined colors for common block types
        int[] colors = {
                0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0xFFA500, 0x800080,
                0xA52A2A, 0xFFC0CB, 0xFFD700, 0xC0C0C0, 0x808080, 0x800000, 0x808000, 0x008000,
                0x008080, 0x000080, 0xFF6347, 0x4682B4, 0xD2691E, 0xCD5C5C, 0xF08080, 0xFA8072,
                0xE9967A, 0xFFA07A, 0xDC143C, 0xFF1493, 0xFF69B4, 0xFFB6C1, 0xFFC0CB, 0xDB7093
        };

        if (id <= colors.length) {
            return colors[id - 1];
        }

        // Generate color based on ID using HSL-like algorithm
        float hue = ((id * 137.5f) % 360) / 360f;
        float saturation = 0.7f + ((id % 30) / 100f);
        float lightness = 0.45f + ((id % 20) / 100f);

        return hslToRgb(hue, saturation, lightness);
    }

    /**
     * Convert HSL to RGB color.
     */
    private int hslToRgb(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = l - c / 2;

        float r, g, b;
        int hi = (int)(h * 6);
        switch (hi) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }

        int ri = (int)((r + m) * 255);
        int gi = (int)((g + m) * 255);
        int bi = (int)((b + m) * 255);

        return (ri << 16) | (gi << 8) | bi;
    }
}
