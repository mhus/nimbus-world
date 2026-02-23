package de.mhus.nimbus.world.generator.modelbuilder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds root blocks at/below the cursor position.
 *
 * Parameters:
 * - blockType (String, required): block type to use
 * - depth (int, default 1): number of root blocks downward
 */
@Component
@Slf4j
public class RootModelPartBuilder implements ModelPartBuilder {

    @Override
    public String name() {
        return "root";
    }

    @Override
    public void buildPart(ModelBuilderContext context, ResolvedStep step) throws ModelBuilderException {
        String blockType = step.getString("blockType");
        if (blockType == null) {
            throw new ModelBuilderException("root: missing required parameter 'blockType'");
        }
        int depth = step.getInt("depth", 1);

        context.setBlockType(blockType);

        int x = context.getPosition().getX();
        int y = context.getPosition().getY();
        int z = context.getPosition().getZ();

        for (int d = 0; d < depth; d++) {
            context.paintAt(x, y - d, z);
        }

        log.debug("root: painted {} blocks at ({},{},{}) depth {}", depth, x, y, z, depth);
    }
}
