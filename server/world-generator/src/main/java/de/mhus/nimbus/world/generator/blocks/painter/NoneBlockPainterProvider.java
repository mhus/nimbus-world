package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * None block painter provider - does not paint any blocks.
 *
 * This painter can be used for operations that only need to calculate geometry
 * without actually placing blocks in the world.
 *
 * Example:
 * <pre>
 * {
 *   "cube": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 10,
 *     "height": 10,
 *     "depth": 10,
 *     "painter": "none"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class NoneBlockPainterProvider implements BlockPainterProvider {

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public String getTitle() {
        return "None Painter";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Create painter that does nothing
        return new EditCachePainter.BlockPainter() {
            @Override
            public void paint(EditCachePainter painter, int x, int y, int z) {
                // None painter - intentionally does not paint any blocks

                // Still add to ModelSelector if context is available
                // This allows visualization of what would be painted
                if (painter.getContext() != null && painter.getContext().getModelSelector() != null) {
                    String color = painter.getContext().getModelSelector().getDefaultColor();
                    if (color == null) {
                        color = "#808080"; // Gray color for "none" painted blocks
                    }
                    painter.getContext().getModelSelector().addBlock(x, y, z, color);
                }
            }
        };
    }
}
