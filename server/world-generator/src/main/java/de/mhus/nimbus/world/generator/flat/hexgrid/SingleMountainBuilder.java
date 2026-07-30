package de.mhus.nimbus.world.generator.flat.hexgrid;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.composer.point.MountainPoint;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.FlatPainter;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

/**
 * SingleMountainBuilder builds a single mountain from MountainPoint configuration.
 * Creates a conical or rough mountain peak at the center of the grid.
 *
 * This builder reads the g_mountain parameter and uses a simplified approach
 * compared to the complex branching MountainManipulator, creating a single
 * prominent peak suitable for landmarks and volcanic formations.
 */
@Slf4j
public class SingleMountainBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    private static final int MIN_HEIGHT = 5;
    private static final int MAX_RECURSION = 6;

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        // Get actual hexGridSize from world context
        int hexGridSize = context.getHexGridSize();

        log.debug("Building single mountain for flat: {} with hexGridSize: {}", flat.getFlatId(), hexGridSize);

        // Get mountain parameter from hex grid
        String mountainParam = hexGrid.getParameters() != null ?
            hexGrid.getParameters().get("g_mountain") : null;

        if (mountainParam == null || mountainParam.isBlank()) {
            log.debug("No mountain parameter found, skipping");
            return;
        }

        try {
            // Parse mountain configuration
            MountainPoint.MountainConfig config = objectMapper.readValue(
                mountainParam, MountainPoint.MountainConfig.class);

            log.debug("Parsed mountain config for '{}': radius={}, height={}, baseHeight={}",
                config.getMountainName(), config.getRadius(), config.getPeakHeight(), config.getBaseHeight());

            // Build the mountain
            buildMountain(flat, config, hexGridSize);

            log.debug("Mountain '{}' completed", config.getMountainName());

        } catch (Exception e) {
            log.error("Failed to build mountain for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Build a single mountain peak
     */
    private void buildMountain(WFlat flat, MountainPoint.MountainConfig config, int hexGridSize) {
        FlatPainter painter = new FlatPainter(flat);
        Random random = new Random(config.getSeed());

        // Calculate center of the flat
        int centerX = flat.getSizeX() / 2;
        int centerZ = flat.getSizeZ() / 2;

        // Get material
        int material = getMaterialForType(config.getMaterial());

        log.debug("Building mountain at center ({}, {}) with radius={}, peakHeight={}, material={}",
            centerX, centerZ, config.getRadius(), config.getPeakHeight(), config.getMaterial());

        // Create mountain using radial approach
        buildRadialMountain(painter, centerX, centerZ, config, material, random);

        // Apply smoothing to blend with existing terrain
        painter.soften(0, 0, flat.getSizeX() - 1, flat.getSizeZ() - 1, 1, 0.3);

        log.info("Single mountain built: name='{}', radius={}, peakHeight={}, baseHeight={}, roughness={}",
            config.getMountainName(), config.getRadius(), config.getPeakHeight(),
            config.getBaseHeight(), config.getRoughness());
    }

    /**
     * Build a radial mountain with optional roughness and crater
     */
    private void buildRadialMountain(FlatPainter painter, int centerX, int centerZ,
                                      MountainPoint.MountainConfig config, int material, Random random) {
        WFlat flat = painter.getFlat();
        int radius = config.getRadius();
        int peakHeight = config.getPeakHeight();
        int baseHeight = config.getBaseHeight();
        double roughness = config.getRoughness();
        boolean crater = config.isCrater();

        // Iterate through all points within the radius
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Calculate distance from center
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= radius) {
                    // Calculate height based on distance (cone shape)
                    double heightFactor = 1.0 - (distance / radius);

                    // Apply power curve for more natural mountain shape
                    // Power < 1 = gentle slopes, Power > 1 = steep slopes
                    heightFactor = Math.pow(heightFactor, 1.5);

                    // Calculate base height with roughness
                    int height = baseHeight + (int) (peakHeight * heightFactor);

                    // Add random roughness
                    if (roughness > 0.0 && distance > radius * 0.1) {
                        int roughnessAmount = (int) (random.nextGaussian() * peakHeight * roughness * 0.3);
                        height += roughnessAmount;
                    }

                    // Create crater at peak if requested
                    if (crater && distance < radius * 0.15) {
                        // Depression at the summit
                        double craterFactor = 1.0 - (distance / (radius * 0.15));
                        int craterDepth = (int) (peakHeight * 0.4 * craterFactor);
                        height -= craterDepth;
                    }

                    // Clamp height to valid range
                    height = Math.max(0, Math.min(255, height));

                    // Only set if higher than existing terrain
                    int currentLevel = flat.getLevel(x, z);
                    if (height > currentLevel) {
                        flat.setLevel(x, z, height);
                        flat.setColumn(x, z, material);
                    }
                }
            }
        }

        log.debug("Radial mountain completed: peak at ({},{}) with {} blocks radius",
            centerX, centerZ, radius);
    }

    /**
     * Get material ID for mountain type
     */
    private int getMaterialForType(String materialType) {
        if (materialType == null || materialType.isBlank()) {
            return FlatMaterialService.STONE;
        }

        return switch (materialType.toLowerCase()) {
            case "stone" -> FlatMaterialService.STONE;
            case "snow" -> FlatMaterialService.SNOW;
            case "volcanic", "lava" -> FlatMaterialService.STONE; // Could be lava material if available
            case "grass" -> FlatMaterialService.GRASS;
            case "sand" -> FlatMaterialService.SAND;
            default -> FlatMaterialService.STONE;
        };
    }

    @Override
    protected int getDefaultOffset() {
        return 0;
    }

    @Override
    protected int getDefaultAsl() {
        return 0;
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }
}
