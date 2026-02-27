package de.mhus.nimbus.world.generator.modelbuilder;

import de.mhus.nimbus.world.generator.blocks.generator.EditBlockPainter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a cone of blocks upward from the cursor position.
 * Wide base at the cursor, narrowing to a tip at the top.
 * Ideal for conifer tree crowns (spruce, fir, pine).
 * Cursor remains unchanged after execution.
 *
 * Parameters:
 * - blockTypes (List<String>): list of block types (random selection per block)
 * - blockType (String): single block type (alternative to blockTypes)
 * - height (int, default 5): total height of the cone
 * - baseRadius (int, default 3): radius at the bottom (widest point)
 * - tipRadius (int, default 0): radius at the top (0 = pointed tip)
 * - density (double, default 0.85): probability of placing each block (0.0 - 1.0)
 */
@Component
@Slf4j
public class ConeModelPartBuilder implements ModelPartBuilder {

    @Override
    public String name() {
        return "cone";
    }

    @Override
    public void buildPart(ModelBuilderContext context, ResolvedStep step) throws ModelBuilderException {
        int height = step.getInt("height", 5);
        int baseRadius = step.getInt("baseRadius", 3);
        int tipRadius = step.getInt("tipRadius", 0);
        double density = step.getDouble("density", 0.85);

        if (height < 1) {
            throw new ModelBuilderException("cone: height must be >= 1, got: " + height);
        }

        // Collect block types
        List<String> blockTypes = step.getStringList("blockTypes");
        if (blockTypes.isEmpty()) {
            String singleType = step.getString("blockType");
            if (singleType == null) {
                throw new ModelBuilderException("cone: missing required parameter 'blockTypes' or 'blockType'");
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
        int painted = 0;

        for (int dy = 0; dy < height; dy++) {
            // Linear interpolation from baseRadius (bottom) to tipRadius (top)
            double progress = (height > 1) ? (double) dy / (height - 1) : 1.0;
            double currentRadius = baseRadius + (tipRadius - baseRadius) * progress;
            double r2 = currentRadius * currentRadius;
            int intRadius = (int) Math.ceil(currentRadius);

            for (int dx = -intRadius; dx <= intRadius; dx++) {
                for (int dz = -intRadius; dz <= intRadius; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 <= r2 && context.getRandom().nextDouble() < density) {
                        EditBlockPainter p = painters.get(context.getRandom().nextInt(painters.size()));
                        context.paintAt(p, cx + dx, cy + dy, cz + dz);
                        painted++;
                    }
                }
            }
        }

        log.debug("cone: painted {} blocks, height={}, baseRadius={}, tipRadius={} at ({},{},{})",
                painted, height, baseRadius, tipRadius, cx, cy, cz);
    }
}
