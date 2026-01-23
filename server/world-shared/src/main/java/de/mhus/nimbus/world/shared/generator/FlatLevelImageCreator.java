package de.mhus.nimbus.world.shared.generator;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Helper class for creating height map (level) images from WFlat data.
 * Generates PNG images showing terrain elevation with color-coded heights.
 * <p>
 * Usage: new FlatLevelImageCreator(flat).create(ignoreEmptyMaterial)
 */
@RequiredArgsConstructor
public class FlatLevelImageCreator {

    private final WFlat flat;

    /**
     * Create height map image from flat data.
     * Fixed palette based on oceanLevel:
     * - Below oceanLevel: dark blue to light blue
     * - Above oceanLevel: yellow -> green -> red
     *
     * @param ignoreEmptyMaterial If true, renders black pixels where material == 0 (NOT_SET)
     * @return PNG image as byte array
     * @throws IOException If image generation fails
     */
    public byte[] create(boolean ignoreEmptyMaterial) throws IOException {
        int width = flat.getSizeX();
        int height = flat.getSizeZ();
        byte[] levels = flat.getLevels();
        byte[] columns = flat.getColumns();
        int oceanLevel = flat.getOceanLevel();

        // Fixed range for above ocean levels (oceanLevel to oceanLevel + 100)
        // This ensures consistent coloring across different flats
        int maxHeightRange = 100;

        // Create image
        BufferedImage image = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_RGB);

        // Draw height map with fixed palette
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int level = levels[index] & 0xFF;
                int material = columns[index] & 0xFF;

                int r, g, b;

                // Check if material is 0 (NOT_SET) and ignoreEmptyMaterial is enabled
                if (ignoreEmptyMaterial && material == 0) {
                    // Render as black
                    r = 0;
                    g = 0;
                    b = 0;
                } else if (level < oceanLevel) {
                    // Below ocean: dark blue (deep) to light blue (near ocean)
                    // Fixed palette from 0 to oceanLevel
                    float t = oceanLevel > 0 ? (float) level / oceanLevel : 0;
                    // Dark blue (0, 0, 100) to light cyan (50, 150, 255)
                    r = (int)(t * 50);
                    g = (int)(t * 150);
                    b = (int)(100 + t * 155);
                } else {
                    // At or above ocean: yellow -> green -> red
                    // Fixed palette from oceanLevel to oceanLevel + maxHeightRange
                    int heightAboveOcean = level - oceanLevel;
                    float t = Math.min(1.0f, (float) heightAboveOcean / maxHeightRange);

                    if (t < 0.5f) {
                        // Yellow to green
                        float tt = t * 2;
                        r = (int)(255 - tt * 255);
                        g = 255;
                        b = 0;
                    } else {
                        // Green to red
                        float tt = (t - 0.5f) * 2;
                        r = (int)(tt * 255);
                        g = (int)((1 - tt) * 255);
                        b = 0;
                    }
                }

                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, z, rgb);
            }
        }

        // Convert to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
