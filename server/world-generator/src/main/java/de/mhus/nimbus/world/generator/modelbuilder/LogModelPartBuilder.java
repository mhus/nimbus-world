package de.mhus.nimbus.world.generator.modelbuilder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds a vertical trunk/log upward from the cursor position.
 * Cursor moves up to the top of the log after execution.
 *
 * Parameters:
 * - blockType (String, required): block type to use
 * - heightFrom (int, default 3): minimum height
 * - heightTo (int, default 6): maximum height
 */
@Component
@Slf4j
public class LogModelPartBuilder implements ModelPartBuilder {

    @Override
    public String name() {
        return "log";
    }

    @Override
    public void buildPart(ModelBuilderContext context, ResolvedStep step) throws ModelBuilderException {
        String blockType = step.getString("blockType");
        if (blockType == null) {
            throw new ModelBuilderException("log: missing required parameter 'blockType'");
        }
        int heightFrom = step.getInt("heightFrom", 3);
        int heightTo = step.getInt("heightTo", 6);

        context.setBlockType(blockType);

        int height = context.randomInt(heightFrom, heightTo);

        for (int i = 0; i < height; i++) {
            context.paintAtCursor();
            context.incrementY();
        }

        log.debug("log: painted {} blocks upward from cursor", height);
    }
}
