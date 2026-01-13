package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import org.springframework.stereotype.Component;

/**
 * Default block painter provider - paints every block.
 */
@Component
public class DefaultBlockPainterProvider implements BlockPainterProvider {

    @Override
    public String getName() {
        return "default";
    }

    @Override
    public String getTitle() {
        return "Default Painter";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        return EditCachePainter.DEFAULT_PAINTER;
    }
}
