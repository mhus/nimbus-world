package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.awt.*;

/**
 * Draws a cross marker at a point in world coordinates.
 */
@Data
@AllArgsConstructor
public class CrossOverlay implements ImageOverlay {

    private final double x;
    private final double z;
    private final int size;
    private final Color color;
    private final float strokeWidth;

    /**
     * Constructor with default size and stroke width.
     */
    public CrossOverlay(double x, double z, Color color) {
        this(x, z, 10, color, 2.0f);
    }

    @Override
    public void paint(Graphics2D g, HexGridCompositeImageCreator.CartesianBounds bounds) {
        // Transform world coordinates to image coordinates
        int imageX = (int) Math.round(x - bounds.getMinX());
        int imageZ = (int) Math.round(z - bounds.getMinZ());

        g.setColor(color);
        g.setStroke(new BasicStroke(strokeWidth));

        // Draw cross: horizontal and vertical lines
        int halfSize = size / 2;
        g.drawLine(imageX - halfSize, imageZ, imageX + halfSize, imageZ);
        g.drawLine(imageX, imageZ - halfSize, imageX, imageZ + halfSize);
    }
}
