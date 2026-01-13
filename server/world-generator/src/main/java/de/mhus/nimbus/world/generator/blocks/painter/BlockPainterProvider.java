package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;

/**
 * Provider interface for block painters.
 * Implementations register different painting strategies (default, raster, grid, etc.)
 * that can be selected via the "painter" parameter in manipulator commands.
 *
 * Painters control how blocks are painted, e.g.:
 * - Default: Paint every block
 * - Raster: Paint in a checkerboard pattern
 * - Grid: Paint only on grid lines (grid-2, grid-5, etc.)
 */
public interface BlockPainterProvider {

    /**
     * Get the technical name of this painter.
     * This name is used in the "painter" parameter.
     *
     * @return technical name (e.g., "default", "raster", "grid-2")
     */
    String getName();

    /**
     * Get the display title of this painter.
     *
     * @return display title (e.g., "Default Painter", "Raster Pattern")
     */
    String getTitle();

    /**
     * Create a BlockPainter instance for the given context.
     * The returned painter is used by EditCachePainter to decide which blocks to paint.
     *
     * @param context manipulator context (currently unused, but available for future extensions)
     * @return BlockPainter instance
     */
    EditCachePainter.BlockPainter createPainter(ManipulatorContext context);
}
