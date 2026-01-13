package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import org.springframework.stereotype.Component;

/**
 * Grid-5 block painter provider - paints only on 5-block grid lines.
 * This is a convenience wrapper around ConfigurableGridBlockPainterProvider with grid=5.
 */
@Component
public class Grid5BlockPainterProvider implements BlockPainterProvider {

    @Override
    public String getName() {
        return "grid-5";
    }

    @Override
    public String getTitle() {
        return "Grid Pattern (5x5x5)";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Return GRID_5_PAINTER from EditCachePainter
        return EditCachePainter.GRID_5_PAINTER;
    }
}
