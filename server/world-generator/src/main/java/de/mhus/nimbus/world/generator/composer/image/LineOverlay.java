package de.mhus.nimbus.world.generator.composer.image;

import de.mhus.nimbus.world.generator.composer.build.HexGridCompositeImageCreator;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.awt.*;

/**
 * Draws a line from p1 to p2 in world coordinates.
 */
@Data
@AllArgsConstructor
public class LineOverlay implements ImageOverlay {

    private final double x1;
    private final double z1;
    private final double x2;
    private final double z2;
    private final Color color;
    private final float strokeWidth;

    /**
     * Constructor with default stroke width.
     */
    public LineOverlay(double x1, double z1, double x2, double z2, Color color) {
        this(x1, z1, x2, z2, color, 2.0f);
    }

    @Override
    public void paint(Graphics2D g, HexGridCompositeImageCreator.CartesianBounds bounds) {
        // Transform world coordinates to image coordinates (Z flipped: North at top)
        int imageX1 = (int) Math.round(x1 - bounds.getMinX());
        int imageZ1 = (int) Math.round(bounds.getMaxZ() - z1);
        int imageX2 = (int) Math.round(x2 - bounds.getMinX());
        int imageZ2 = (int) Math.round(bounds.getMaxZ() - z2);

        g.setColor(color);
        g.setStroke(new BasicStroke(strokeWidth));
        g.drawLine(imageX1, imageZ1, imageX2, imageZ2);
    }
}
