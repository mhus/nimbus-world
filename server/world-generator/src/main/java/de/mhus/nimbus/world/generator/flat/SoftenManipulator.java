package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Soften manipulator.
 * Smoothes terrain by averaging neighboring heights, reducing sharp changes.
 * Uses FlatPainter's soften() method.
 * <p>
 * Parameters:
 * - factor: Softening strength from 0.0 (no effect) to 1.0 (full averaging) (default: 0.5)
 */
@Component
@Slf4j
public class SoftenManipulator implements FlatManipulator {

    public static final String NAME = "soften";
    public static final String PARAM_FACTOR = "factor";

    private static final double DEFAULT_FACTOR = 0.5;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ, Map<String, String> parameters) {
        log.debug("Softening terrain: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        double factor = parseDoubleParameter(parameters, PARAM_FACTOR, DEFAULT_FACTOR);

        // Clamp factor to valid range
        factor = Math.max(0.0, Math.min(1.0, factor));

        // Create FlatPainter and apply soften operation
        FlatPainter painter = new FlatPainter(flat);

        int x2 = x + sizeX - 1;
        int z2 = z + sizeZ - 1;

        painter.soften(x, z, x2, z2, factor);

        log.info("Terrain softened: region=({},{},{},{}), factor={}",
                x, z, sizeX, sizeZ, factor);
    }

    private double parseDoubleParameter(Map<String, String> parameters, String name, double defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid double parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
