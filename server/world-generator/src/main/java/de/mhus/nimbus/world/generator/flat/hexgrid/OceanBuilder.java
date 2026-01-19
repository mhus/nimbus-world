package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.FlatPainter;
import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Ocean scenario builder.
 * Creates deep ocean with varying depth.
 */
@Component
@Slf4j
public class OceanBuilder implements CompositionBuilder {

    @Override
    public String getType() {
        return "ocean";
    }

    @Override
    public void build(BuilderContext context) {
        WFlat flat = context.getFlat();
        Map<String, String> parameters = context.getParameters();

        log.info("Building ocean scenario for flat: {}, neighbors: {}",
                flat.getFlatId(), context.getNeighborTypes());

        int oceanLevel = flat.getOceanLevel();
        int oceanDepth = parseIntParameter(parameters, "gf.oceanDepth", 30);

        FlatPainter painter = new FlatPainter(flat);

        // Create ocean floor at oceanLevel - oceanDepth
        int oceanFloor = oceanLevel - oceanDepth;
        painter.fillRectangle(0, 0, flat.getSizeX() - 1, flat.getSizeZ() - 1, oceanFloor, FlatPainter.DEFAULT_PAINTER);

        // Set all to water material
        for (int z = 0; z < flat.getSizeZ(); z++) {
            for (int x = 0; x < flat.getSizeX(); x++) {
                flat.setColumn(x, z, FlatMaterialService.SAND);
            }
        }

        // Add some variation with soften
        painter.soften(0, 0, flat.getSizeX() - 1, flat.getSizeZ() - 1, 1, 0.3);

        log.info("Ocean scenario completed: oceanLevel={}, oceanFloor={}", oceanLevel, oceanFloor);
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
