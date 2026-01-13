package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Roughen manipulator.
 * Adds random variation to terrain heights, creating a rougher, more natural appearance.
 * Uses FlatPainter's fillRectangle() method with RANDOM_ADDITIVE painter.
 * <p>
 * Parameters:
 * - level: Maximum random height variation to add (default: 5)
 */
@Component
@Slf4j
public class RoughenManipulator implements FlatManipulator {

    public static final String NAME = "roughen";
    public static final String PARAM_LEVEL = "level";

    private static final int DEFAULT_LEVEL = 5;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ, Map<String, String> parameters) {
        log.debug("Roughening terrain: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int level = parseIntParameter(parameters, PARAM_LEVEL, DEFAULT_LEVEL);

        // Clamp level to reasonable range (1-50)
        level = Math.max(1, Math.min(50, level));

        // Create FlatPainter with RANDOM_ADDITIVE painter
        FlatPainter painter = new FlatPainter(flat);
        painter.setPainter(FlatPainter.RANDOM_ADDITIVE);

        int x2 = x + sizeX - 1;
        int z2 = z + sizeZ - 1;

        // Apply random additive effect using fillRectangle
        painter.fillRectangle(x, z, x2, z2, level);

        log.info("Terrain roughened: region=({},{},{},{}), level={}",
                x, z, sizeX, sizeZ, level);
    }

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
