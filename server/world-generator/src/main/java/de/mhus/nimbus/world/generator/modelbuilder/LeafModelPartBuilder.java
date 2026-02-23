package de.mhus.nimbus.world.generator.modelbuilder;

import de.mhus.nimbus.world.generator.blocks.generator.EditBlockPainter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a sphere of leaf blocks around the cursor position.
 * Cursor remains unchanged after execution.
 *
 * Parameters:
 * - blockTypes (List&lt;String&gt;): list of block types (random selection per block)
 * - blockType (String): single block type (alternative to blockTypes)
 * - size (int, default 2): sphere radius
 * - density (double, default 0.85): probability of placing each block (0.0 - 1.0)
 */
@Component
@Slf4j
public class LeafModelPartBuilder implements ModelPartBuilder {

    @Override
    public String name() {
        return "leaf";
    }

    @Override
    public void buildPart(ModelBuilderContext context, ResolvedStep step) throws ModelBuilderException {
        int size = step.getInt("size", 2);
        double density = step.getDouble("density", 0.85);

        // Collect block types
        List<String> blockTypes = step.getStringList("blockTypes");
        if (blockTypes.isEmpty()) {
            String singleType = step.getString("blockType");
            if (singleType == null) {
                throw new ModelBuilderException("leaf: missing required parameter 'blockTypes' or 'blockType'");
            }
            blockTypes = List.of(singleType);
        }

        // Create painters for each block type
        List<EditBlockPainter> painters = new ArrayList<>(blockTypes.size());
        for (String bt : blockTypes) {
            painters.add(context.createPainter(bt));
        }

        int cx = context.getPosition().getX();
        int cy = context.getPosition().getY();
        int cz = context.getPosition().getZ();
        int r2 = size * size;
        int painted = 0;

        for (int dx = -size; dx <= size; dx++) {
            for (int dy = -size; dy <= size; dy++) {
                for (int dz = -size; dz <= size; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= r2 && context.getRandom().nextDouble() < density) {
                        EditBlockPainter p = painters.get(context.getRandom().nextInt(painters.size()));
                        context.paintAt(p, cx + dx, cy + dy, cz + dz);
                        painted++;
                    }
                }
            }
        }

        log.debug("leaf: painted {} blocks in sphere radius {} around ({},{},{})", painted, size, cx, cy, cz);
    }
}
