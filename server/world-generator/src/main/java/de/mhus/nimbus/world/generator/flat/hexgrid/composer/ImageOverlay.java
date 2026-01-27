package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import java.awt.*;

/**
 * Interface for drawing overlays on composite images.
 * Overlays are drawn after all hex grids have been rendered.
 */
public interface ImageOverlay {

    /**
     * Paint this overlay onto the graphics context.
     *
     * @param g Graphics2D context to draw on
     * @param bounds Cartesian bounds of the composite image (for coordinate transformation)
     */
    void paint(Graphics2D g, HexGridCompositeImageCreator.CartesianBounds bounds);
}
