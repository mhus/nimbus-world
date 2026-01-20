package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.FlatPainter;
import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Island scenario builder.
 * Creates island(s) surrounded by ocean.
 */
@Slf4j
public class IslandBuilder extends HexGridBuilder {

    @Override
    public void build(BuilderContext context) {
        WFlat flat = context.getFlat();

        log.info("Building island scenario for flat: {}, neighbors: {}",
                flat.getFlatId(), context.getNeighborTypes());

        int oceanLevel = flat.getOceanLevel();
        int islandHeight = parseIntParameter(parameters, "g_islandHeight", 15);
        int islandRadius = parseIntParameter(parameters, "g_islandRadius", 40);
        int smallIslands = parseIntParameter(parameters, "g_smallIslands", 3);
        long seed = parseLongParameter(parameters, "g_seed", System.currentTimeMillis());

        Random random = new Random(seed);
        FlatPainter painter = new FlatPainter(flat);

        // Start with ocean floor
        int oceanFloor = oceanLevel - 20;
        painter.fillRectangle(0, 0, flat.getSizeX() - 1, flat.getSizeZ() - 1, oceanFloor, FlatPainter.DEFAULT_PAINTER);

        // Create main island in center
        int centerX = flat.getSizeX() / 2;
        int centerZ = flat.getSizeZ() / 2;
        int targetLevel = oceanLevel + islandHeight;

        drawIsland(painter, flat, centerX, centerZ, islandRadius, targetLevel, oceanLevel);

        // Create small islands scattered around
        for (int i = 0; i < smallIslands; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = islandRadius + 20 + random.nextInt(30);
            int islandX = centerX + (int) (Math.cos(angle) * distance);
            int islandZ = centerZ + (int) (Math.sin(angle) * distance);
            int smallRadius = 10 + random.nextInt(15);
            int smallHeight = 5 + random.nextInt(10);

            drawIsland(painter, flat, islandX, islandZ, smallRadius, oceanLevel + smallHeight, oceanLevel);
        }

        // Set materials based on height
        for (int z = 0; z < flat.getSizeZ(); z++) {
            for (int x = 0; x < flat.getSizeX(); x++) {
                int level = flat.getLevel(x, z);
                if (level <= oceanLevel) {
                    flat.setColumn(x, z, FlatMaterialService.SAND);
                } else {
                    flat.setColumn(x, z, FlatMaterialService.GRASS);
                }
            }
        }

        // Soften for natural look
        painter.soften(0, 0, flat.getSizeX() - 1, flat.getSizeZ() - 1, 1, 0.4);

        log.info("Island scenario completed: mainIsland={}, smallIslands={}", islandRadius, smallIslands);
    }

    private void drawIsland(FlatPainter painter, WFlat flat, int centerX, int centerZ,
                           int radius, int peakHeight, int oceanLevel) {
        // Create island with radial falloff
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= radius) {
                    int x = centerX + dx;
                    int z = centerZ + dz;

                    // Smooth falloff from peak to ocean
                    double heightFactor = 1.0 - (distance / radius);
                    int height = oceanLevel + (int) (heightFactor * (peakHeight - oceanLevel));

                    painter.paint(x, z, height, FlatPainter.HIGHER);
                }
            }
        }
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

    private long parseLongParameter(Map<String, String> parameters, String name, long defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid long parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
