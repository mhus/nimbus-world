package de.mhus.nimbus.world.generator.flat.hexgrid;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.composer.point.SpikesPoint;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.manipulator.SpikesManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

/**
 * SingleSpikesBuilder builds a field of spikes from SpikesPoint configuration.
 * Creates multiple spike formations distributed across the hex grid.
 *
 * This builder reads the g_spikes parameter and uses SpikesManipulator
 * to create the spike field with appropriate density and distribution.
 */
@Slf4j
public class SingleSpikesBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        // Get actual hexGridSize from world context
        int hexGridSize = context.getHexGridSize();

        log.debug("Building spike field for flat: {} with hexGridSize: {}", flat.getFlatId(), hexGridSize);

        // Get spikes parameter from hex grid
        String spikesParam = hexGrid.getParameters() != null ?
            hexGrid.getParameters().get("g_spikes") : null;

        if (spikesParam == null || spikesParam.isBlank()) {
            log.debug("No spikes parameter found, skipping");
            return;
        }

        try {
            // Parse spikes configuration
            SpikesPoint.SpikesConfig config = objectMapper.readValue(
                spikesParam, SpikesPoint.SpikesConfig.class);

            log.debug("Parsed spikes config for '{}': density={}, amount={}, radius={}",
                config.getSpikesName(), config.getDensity(), config.getAmount(),
                config.getDistributionRadius());

            // Build the spike field
            buildSpikes(flat, config, hexGridSize);

            log.debug("Spike field '{}' completed", config.getSpikesName());

        } catch (Exception e) {
            log.error("Failed to build spikes for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Build a field of spikes using SpikesManipulator
     */
    private void buildSpikes(WFlat flat, SpikesPoint.SpikesConfig config, int hexGridSize) {
        // Get material
        int material = getMaterialForType(config.getMaterial());

        // Calculate the region to manipulate (centered on the flat)
        int centerX = flat.getSizeX() / 2;
        int centerZ = flat.getSizeZ() / 2;

        // Use distribution radius, but clamp to flat size
        int effectiveRadius = Math.min(config.getDistributionRadius(), Math.min(flat.getSizeX(), flat.getSizeZ()) / 2);
        int regionStartX = Math.max(0, centerX - effectiveRadius);
        int regionStartZ = Math.max(0, centerZ - effectiveRadius);
        int regionSizeX = Math.min(flat.getSizeX(), effectiveRadius * 2);
        int regionSizeZ = Math.min(flat.getSizeZ(), effectiveRadius * 2);

        log.debug("Building spikes at center ({}, {}) with effectiveRadius={}, region=({},{},{},{})",
            centerX, centerZ, effectiveRadius, regionStartX, regionStartZ, regionSizeX, regionSizeZ);

        // Create parameters map for SpikesManipulator
        Map<String, String> manipulatorParams = new HashMap<>();
        manipulatorParams.put("density", config.getDensity().name());
        manipulatorParams.put("amount", config.getAmount().name());
        manipulatorParams.put("minHeight", String.valueOf(config.getMinHeight()));
        manipulatorParams.put("maxHeight", String.valueOf(config.getMaxHeight()));
        manipulatorParams.put("minWidth", String.valueOf(config.getMinWidth()));
        manipulatorParams.put("maxWidth", String.valueOf(config.getMaxWidth()));
        manipulatorParams.put("baseHeight", String.valueOf(config.getBaseHeight()));
        manipulatorParams.put("seed", String.valueOf(config.getSeed()));
        manipulatorParams.put("taperFactor", String.valueOf(config.getTaperFactor()));
        manipulatorParams.put("material", String.valueOf(material));

        // Create SpikesManipulator and apply
        SpikesManipulator manipulator = new SpikesManipulator();
        manipulator.manipulate(flat, regionStartX, regionStartZ, regionSizeX, regionSizeZ, manipulatorParams);

        log.info("Spike field built: name='{}', density={}, amount={}, material={}",
            config.getSpikesName(), config.getDensity(), config.getAmount(),
            config.getMaterial());
    }

    /**
     * Get material ID for spike type
     */
    private int getMaterialForType(String materialType) {
        if (materialType == null || materialType.isBlank()) {
            return FlatMaterialService.STONE;
        }

        return switch (materialType.toLowerCase()) {
            case "stone" -> FlatMaterialService.STONE;
            case "ice", "snow" -> FlatMaterialService.SNOW;
            case "crystal" -> FlatMaterialService.STONE; // Could be special crystal material if available
            case "obsidian", "volcanic" -> FlatMaterialService.STONE;
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
