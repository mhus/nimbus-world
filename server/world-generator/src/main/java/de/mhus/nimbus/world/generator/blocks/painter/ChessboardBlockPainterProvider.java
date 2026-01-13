package de.mhus.nimbus.world.generator.blocks.painter;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.world.generator.blocks.ManipulatorContext;
import de.mhus.nimbus.world.generator.blocks.generator.EditCachePainter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chessboard block painter provider - paints blocks in a chessboard pattern with multiple block types.
 *
 * Parameters:
 * - size: int (default 2) - Number of different block types/colors
 * - blockType0, blockType1, blockType2, ... - Block types for the chessboard pattern
 *
 * The pattern cycles through the provided block types based on (x + y + z) % size.
 *
 * Example (classic 2-color chessboard):
 * <pre>
 * {
 *   "cube": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 10,
 *     "height": 1,
 *     "depth": 10,
 *     "painter": "chessboard",
 *     "size": 2,
 *     "blockType0": "n:s",
 *     "blockType1": "n:t"
 *   }
 * }
 * </pre>
 *
 * Example (3-color pattern):
 * <pre>
 * {
 *   "plateau": {
 *     "position": {"x": 0, "y": 64, "z": 0},
 *     "width": 15,
 *     "depth": 15,
 *     "painter": "chessboard",
 *     "size": 3,
 *     "blockType0": "310",
 *     "blockType1": "311",
 *     "blockType2": "312"
 *   }
 * }
 * </pre>
 */
@Component
@Slf4j
public class ChessboardBlockPainterProvider implements BlockPainterProvider {

    @Override
    public String getName() {
        return "chessboard";
    }

    @Override
    public String getTitle() {
        return "Chessboard Pattern (Multi-Color)";
    }

    @Override
    public EditCachePainter.BlockPainter createPainter(ManipulatorContext context) {
        // Read size parameter (default: 2)
        Integer size = context.getIntParameter("size");
        if (size == null || size <= 0) {
            size = 2;
        }

        // Read blockType0, blockType1, ... parameters
        List<BlockDef> blockDefs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String paramName = "blockType" + i;
            String blockTypeStr = context.getParameter(paramName);

            if (blockTypeStr == null || blockTypeStr.isBlank()) {
                log.warn("Chessboard painter: missing parameter '{}', using first blockType as fallback", paramName);
                // Use first blockDef as fallback, or skip if none available
                if (!blockDefs.isEmpty()) {
                    blockDefs.add(blockDefs.get(0));
                }
                continue;
            }

            BlockDef blockDef = BlockDef.of(blockTypeStr).orElse(null);
            if (blockDef == null) {
                log.warn("Chessboard painter: invalid blockType '{}' for parameter '{}'", blockTypeStr, paramName);
                // Use first blockDef as fallback, or skip if none available
                if (!blockDefs.isEmpty()) {
                    blockDefs.add(blockDefs.get(0));
                }
                continue;
            }

            blockDefs.add(blockDef);
        }

        // Validate we have at least one blockDef
        if (blockDefs.isEmpty()) {
            log.error("Chessboard painter: no valid blockTypes provided, using default painter");
            return EditCachePainter.DEFAULT_PAINTER;
        }

        final int finalSize = blockDefs.size();
        final List<BlockDef> finalBlockDefs = blockDefs;

        // Create painter with chessboard logic
        return new EditCachePainter.BlockPainter() {
            @Override
            public void paint(EditCachePainter painter, int x, int y, int z) {
                // Calculate which blockDef to use based on position
                int index = Math.abs((x + y + z) % finalSize);
                BlockDef blockDef = finalBlockDefs.get(index);

                // Create and paint block with selected BlockDef
                Block block = Block.builder()
                        .position(
                                de.mhus.nimbus.generated.types.Vector3Int.builder()
                                        .x(x)
                                        .y(y)
                                        .z(z)
                                        .build()
                        ).build();

                blockDef.fillBlock(block);
                painter.getEditService().doSetAndSendBlock(
                        painter.getWorld(),
                        painter.getLayerDataId(),
                        painter.getModelName(),
                        block,
                        painter.getGroupId()
                );

                // Add block to ModelSelector if context is available
                if (painter.getContext() != null && painter.getContext().getModelSelector() != null) {
                    String color = painter.getContext().getModelSelector().getDefaultColor();
                    if (color == null) {
                        color = "#00ff00"; // Default green
                    }
                    painter.getContext().getModelSelector().addBlock(x, y, z, color);
                }
            }
        };
    }
}
