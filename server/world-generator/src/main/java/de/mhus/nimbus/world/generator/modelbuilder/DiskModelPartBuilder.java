package de.mhus.nimbus.world.generator.modelbuilder;

import de.mhus.nimbus.world.generator.blocks.generator.EditBlockPainter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a flat disk of blocks at the cursor position.
 * Circular shape with configurable radius and thickness.
 * Ideal for palm tree crowns, umbrella trees, or flat canopies.
 * Cursor remains unchanged after execution.
 *
 * Parameters:
 * - blockTypes (List<String>): list of block types (random selection per block)
 * - blockType (String): single block type (alternative to blockTypes)
 * - radius (int, default 3): radius of the disk
 * - thickness (int, default 1): vertical thickness of the disk
 * - density (double, default 0.85): probability of placing each block (0.0 - 1.0)
 */
@Component
@Slf4j
public class DiskModelPartBuilder implements ModelPartBuilder {

    @Override
    public String name() {
        return "disk";
    }

    @Override
    public void buildPart(ModelBuilderContext context, ResolvedStep step) throws ModelBuilderException {
        int radius = step.getInt("radius", 3);
        int thickness = step.getInt("thickness", 1);
        double density = step.getDouble("density", 0.85);

        if (thickness < 1) {
            throw new ModelBuilderException("disk: thickness must be >= 1, got: " + thickness);
        }

        // Collect block types
        List<String> blockTypes = step.getStringList("blockTypes");
        if (blockTypes.isEmpty()) {
            String singleType = step.getString("blockType");
            if (singleType == null) {
                throw new ModelBuilderException("disk: missing required parameter 'blockTypes' or 'blockType'");
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
        int r2 = radius * radius;
        int painted = 0;

        for (int dy = 0; dy < thickness; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 <= r2 && context.getRandom().nextDouble() < density) {
                        EditBlockPainter p = painters.get(context.getRandom().nextInt(painters.size()));
                        context.paintAt(p, cx + dx, cy + dy, cz + dz);
                        painted++;
                    }
                }
            }
        }

        log.debug("disk: painted {} blocks, radius={}, thickness={} at ({},{},{})",
                painted, radius, thickness, cx, cy, cz);
    }
}
