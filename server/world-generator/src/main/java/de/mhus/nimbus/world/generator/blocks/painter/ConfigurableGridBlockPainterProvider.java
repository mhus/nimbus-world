package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import org.springframework.stereotype.Component;

/**
 * Configurable grid block painter provider - paints only on grid lines.
 * Paints blocks only when x % grid == 0 AND y % grid == 0 AND z % grid == 0.
 *
 * Parameters:
 * - grid: int (default 2) - Grid spacing
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
 *     "painter": "grid",
 *     "grid": 5
 *   }
 * }
 * </pre>
 */
@Component
public class ConfigurableGridBlockPainterProvider implements BlockPainterProvider {

    @Override
    public String getName() {
        return "grid";
    }

    @Override
    public String getTitle() {
        return "Configurable Grid Pattern";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Read grid parameter from context (default: 2)
        Integer grid = context.getIntParameter("grid");
        if (grid == null || grid <= 0) {
            grid = 2; // Default grid size
        }

        final int finalGrid = grid;

        // Create painter with custom grid logic
        return new EditCachePainter.BlockPainter() {
            @Override
            public void paint(EditCachePainter painter, int x, int y, int z) {
                // Only paint if x, y, and z are all on grid lines
                if (x % finalGrid == 0 && y % finalGrid == 0 && z % finalGrid == 0) {
                    EditCachePainter.DEFAULT_PAINTER.paint(painter, x, y, z);
                }
            }
        };
    }
}
