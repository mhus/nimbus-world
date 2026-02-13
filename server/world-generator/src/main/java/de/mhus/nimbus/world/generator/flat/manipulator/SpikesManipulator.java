package de.mhus.nimbus.world.generator.flat.manipulator;

import de.mhus.nimbus.world.generator.composer.point.SpikesPoint;
import de.mhus.nimbus.world.generator.flat.FlatManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * SpikesManipulator creates fields of spike formations.
 * Generates multiple spike structures with varying heights and widths,
 * distributed randomly across the area.
 *
 * Parameters:
 * - density: Spacing between spikes (LOW/MEDIUM/HIGH)
 * - amount: Total number of spikes (FEW/NORMAL/MANY)
 * - minHeight: Minimum spike height (default: 10)
 * - maxHeight: Maximum spike height (default: 50)
 * - minWidth: Minimum spike base width (default: 1)
 * - maxWidth: Maximum spike base width (default: 3)
 * - baseHeight: Base terrain level (default: 64)
 * - seed: Random seed for reproducibility
 * - taperFactor: How quickly spikes taper (0.0-1.0, default: 0.5)
 */
@Component
@Slf4j
public class SpikesManipulator implements FlatManipulator {

    public static final String NAME = "spikes";

    private static final int DEFAULT_MIN_HEIGHT = 10;
    private static final int DEFAULT_MAX_HEIGHT = 50;
    private static final int DEFAULT_MIN_WIDTH = 1;
    private static final int DEFAULT_MAX_WIDTH = 3;
    private static final int DEFAULT_BASE_HEIGHT = 64;
    private static final double DEFAULT_TAPER_FACTOR = 0.5;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting spikes manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        SpikesPoint.Density density = parseDensity(parameters.get("density"));
        SpikesPoint.Amount amount = parseAmount(parameters.get("amount"));
        int minHeight = parseIntParameter(parameters, "minHeight", DEFAULT_MIN_HEIGHT);
        int maxHeight = parseIntParameter(parameters, "maxHeight", DEFAULT_MAX_HEIGHT);
        int minWidth = parseIntParameter(parameters, "minWidth", DEFAULT_MIN_WIDTH);
        int maxWidth = parseIntParameter(parameters, "maxWidth", DEFAULT_MAX_WIDTH);
        int baseHeight = parseIntParameter(parameters, "baseHeight", DEFAULT_BASE_HEIGHT);
        long seed = parseLongParameter(parameters, "seed", System.currentTimeMillis());
        double taperFactor = parseDoubleParameter(parameters, "taperFactor", DEFAULT_TAPER_FACTOR);

        // Initialize random generator
        Random random = new Random(seed);

        // Calculate center point
        int centerX = x + sizeX / 2;
        int centerZ = z + sizeZ / 2;

        // Determine number of spikes based on amount
        int spikeCount = getSpikeCount(amount, random);

        // Determine spacing based on density
        int minSpacing = getMinSpacing(density);
        int maxSpacing = getMaxSpacing(density);

        log.debug("Generating {} spikes with density={}, spacing={}-{}",
            spikeCount, density, minSpacing, maxSpacing);

        // Generate spike positions with minimum spacing
        List<SpikePosition> positions = generateSpikePositions(
            centerX, centerZ, spikeCount, minSpacing, maxSpacing,
            Math.min(sizeX, sizeZ) / 2, random);

        log.debug("Generated {} spike positions", positions.size());

        // Create each spike
        for (SpikePosition pos : positions) {
            // Random dimensions for this spike
            int spikeHeight = minHeight + random.nextInt(maxHeight - minHeight + 1);
            int spikeWidth = minWidth + random.nextInt(maxWidth - minWidth + 1);

            // Create the spike
            createSpike(flat, pos.x, pos.z, baseHeight, spikeHeight, spikeWidth, taperFactor, random);
        }

        log.info("Spikes manipulation completed: {} spikes created, density={}, amount={}",
            positions.size(), density, amount);
    }

    /**
     * Generate spike positions with minimum spacing
     */
    private List<SpikePosition> generateSpikePositions(int centerX, int centerZ,
                                                       int count, int minSpacing, int maxSpacing,
                                                       int maxRadius, Random random) {
        List<SpikePosition> positions = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = count * 10; // Prevent infinite loop

        while (positions.size() < count && attempts < maxAttempts) {
            attempts++;

            // Random position within distribution radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * maxRadius;

            int x = centerX + (int) (Math.cos(angle) * distance);
            int z = centerZ + (int) (Math.sin(angle) * distance);

            // Check minimum spacing to existing spikes
            boolean tooClose = false;
            int requiredSpacing = minSpacing + random.nextInt(maxSpacing - minSpacing + 1);

            for (SpikePosition existing : positions) {
                double dist = Math.sqrt(
                    Math.pow(x - existing.x, 2) +
                    Math.pow(z - existing.z, 2)
                );
                if (dist < requiredSpacing) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                positions.add(new SpikePosition(x, z));
            }
        }

        if (positions.size() < count) {
            log.debug("Could only place {} of {} requested spikes due to spacing constraints",
                positions.size(), count);
        }

        return positions;
    }

    /**
     * Create a single spike at the specified position
     */
    private void createSpike(WFlat flat, int centerX, int centerZ,
                            int baseHeight, int height, int baseWidth,
                            double taperFactor, Random random) {
        // Build spike layer by layer from bottom to top
        for (int layer = 0; layer < height; layer++) {
            // Calculate width at this height using taper factor
            // taperFactor 0.0 = cylinder (same width all the way up)
            // taperFactor 1.0 = perfect cone (linear taper to point)
            double heightRatio = (double) layer / height;
            double widthFactor = 1.0 - (heightRatio * taperFactor);

            // Add some randomness to make spikes more organic
            widthFactor += (random.nextDouble() - 0.5) * 0.1;
            widthFactor = Math.max(0.1, Math.min(1.0, widthFactor));

            int currentWidth = Math.max(1, (int) (baseWidth * widthFactor));
            int currentHeight = baseHeight + layer;

            // Draw the layer as a circle/square
            drawSpikeLayer(flat, centerX, centerZ, currentWidth, currentHeight);
        }

        log.debug("Created spike at ({},{}) with height={}, baseWidth={}",
            centerX, centerZ, height, baseWidth);
    }

    /**
     * Draw a single layer of a spike
     */
    private void drawSpikeLayer(WFlat flat, int centerX, int centerZ, int radius, int height) {
        // Use diamond/circular pattern
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Calculate distance from center
                double distance = Math.sqrt(dx * dx + dz * dz);

                // Only draw if within radius (creates circular cross-section)
                if (distance <= radius) {
                    int x = centerX + dx;
                    int z = centerZ + dz;

                    // Check bounds
                    if (x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ()) {
                        // Only set if higher than current level (spikes grow upward)
                        int currentLevel = flat.getLevel(x, z);
                        if (height > currentLevel) {
                            flat.setLevel(x, z, height);
                            // Material is set by SingleSpikesBuilder
                        }
                    }
                }
            }
        }
    }

    /**
     * Get spike count based on amount setting
     */
    private int getSpikeCount(SpikesPoint.Amount amount, Random random) {
        return switch (amount) {
            case FEW -> 3 + random.nextInt(8);      // 3-10
            case NORMAL -> 10 + random.nextInt(21); // 10-30
            case MANY -> 30 + random.nextInt(21);   // 30-50
        };
    }

    /**
     * Get minimum spacing based on density
     */
    private int getMinSpacing(SpikesPoint.Density density) {
        return switch (density) {
            case LOW -> 15;
            case MEDIUM -> 10;
            case HIGH -> 5;
        };
    }

    /**
     * Get maximum spacing based on density
     */
    private int getMaxSpacing(SpikesPoint.Density density) {
        return switch (density) {
            case LOW -> 20;
            case MEDIUM -> 15;
            case HIGH -> 10;
        };
    }

    /**
     * Parse density from string
     */
    private SpikesPoint.Density parseDensity(String value) {
        if (value == null) return SpikesPoint.Density.MEDIUM;
        try {
            return SpikesPoint.Density.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid density value '{}', using MEDIUM", value);
            return SpikesPoint.Density.MEDIUM;
        }
    }

    /**
     * Parse amount from string
     */
    private SpikesPoint.Amount parseAmount(String value) {
        if (value == null) return SpikesPoint.Amount.NORMAL;
        try {
            return SpikesPoint.Amount.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid amount value '{}', using NORMAL", value);
            return SpikesPoint.Amount.NORMAL;
        }
    }

    // Parameter parsing helper methods

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}",
                    name, parameters.get(name), defaultValue);
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
            log.warn("Invalid double parameter '{}': {}, using default: {}",
                    name, parameters.get(name), defaultValue);
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
            log.warn("Invalid long parameter '{}': {}, using default: {}",
                    name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Internal class for spike positions
     */
    private static class SpikePosition {
        final int x;
        final int z;

        SpikePosition(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }
}
