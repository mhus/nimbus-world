package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import org.springframework.stereotype.Component;

/**
 * Grid-2 block painter provider - paints only on 2-block grid lines.
 * This is a convenience wrapper around ConfigurableGridBlockPainterProvider with grid=2.
 */
@Component
public class Grid2BlockPainterProvider implements BlockPainterProvider {

    @Override
    public String getName() {
        return "grid-2";
    }

    @Override
    public String getTitle() {
        return "Grid Pattern (2x2x2)";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Return GRID_2_PAINTER from EditCachePainter
        return EditCachePainter.GRID_2_PAINTER;
    }
}
