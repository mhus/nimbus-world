package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import org.springframework.stereotype.Component;

/**
 * Configurable raster block painter provider - paints in a configurable grid pattern.
 * Paints blocks only when (x + y + z) % raster == 0.
 *
 * Parameters:
 * - raster: int (default 2) - Grid spacing
 *
 * Example:
 * <pre>
 * {
 *   "cube": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 10,
 *     "height": 10,
 *     "depth": 10,
 *     "blockType": "n:s",
 *     "painter": "raster",
 *     "raster": 3
 *   }
 * }
 * </pre>
 */
@Component
public class ConfigurableRasterBlockPainterProvider implements BlockPainterProvider {

    @Override
    public String getName() {
        return "raster";
    }

    @Override
    public String getTitle() {
        return "Configurable Raster Pattern";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Read raster parameter from context (default: 2)
        Integer raster = context.getIntParameter("raster");
        if (raster == null || raster <= 0) {
            raster = 2; // Default raster size
        }

        final int finalRaster = raster;

        // Create painter with custom raster logic
        return new EditCachePainter.BlockPainter() {
            @Override
            public void paint(EditCachePainter painter, int x, int y, int z) {
                // Only paint if (x + y + z) % raster == 0
                if ((x + y + z) % finalRaster == 0) {
                    EditCachePainter.DEFAULT_PAINTER.paint(painter, x, y, z);
                }
            }
        };
    }
}
