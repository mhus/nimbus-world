package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.FlatPainter;
import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Plains scenario builder.
 * Creates flat grassland with minor variations.
 */
@Component
@Slf4j
public class PlainsBuilder implements CompositionBuilder {

    @Override
    public String getType() {
        return "plains";
    }

    @Override
    public void build(BuilderContext context) {
        WFlat flat = context.getFlat();
        Map<String, String> parameters = context.getParameters();

        log.info("Building plains scenario for flat: {}, neighbors: {}",
                flat.getFlatId(), context.getNeighborTypes());

        int groundLevel = parseIntParameter(parameters, "gf.groundLevel", 64);
        double variation = parseDoubleParameter(parameters, "gf.variation", 0.1);

        FlatPainter painter = new FlatPainter(flat);

        // Create flat base
        painter.fillRectangle(0, 0, flat.getSizeX() - 1, flat.getSizeZ() - 1, groundLevel, FlatPainter.DEFAULT_PAINTER);

        // Add minor roughness
        painter.pixelFlip(0, 0, flat.getSizeX() - 1, flat.getSizeZ() - 1, variation);

        // Set grass material
        for (int z = 0; z < flat.getSizeZ(); z++) {
            for (int x = 0; x < flat.getSizeX(); x++) {
                flat.setColumn(x, z, FlatMaterialService.GRASS);
            }
        }

        log.info("Plains scenario completed: groundLevel={}, variation={}", groundLevel, variation);
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
