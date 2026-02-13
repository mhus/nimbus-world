package de.mhus.nimbus.world.generator.composer.point;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.build.ComposeContext;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * SpikesPoint represents a field of spikes positioned at a specific point.
 * Creates multiple spike formations using the SpikesManipulator.
 *
 * During composition:
 * 1. PointComposer positions the SpikesPoint on a HexGrid
 * 2. SpikesPoint creates g_spikes configuration for that grid
 * 3. SingleSpikesBuilder reads g_spikes and builds the spike field on the grid
 *
 * Example: Crystal formations, ice spikes, volcanic vents, stalagmite fields
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class SpikesPoint extends Point {

    /**
     * Density of spikes - affects spacing and clustering
     */
    public enum Density {
        LOW,      // Sparse, wide spacing (15-20 blocks)
        MEDIUM,   // Normal spacing (10-15 blocks)
        HIGH      // Dense, close spacing (5-10 blocks)
    }

    /**
     * Amount of spikes - total number of spikes
     */
    public enum Amount {
        FEW,      // 3-10 spikes
        NORMAL,   // 10-30 spikes
        MANY      // 30-50 spikes
    }

    /**
     * Density of the spike field.
     * Controls spacing between individual spikes.
     * Default: MEDIUM
     */
    @lombok.Builder.Default
    private Density density = Density.MEDIUM;

    /**
     * Amount of spikes to generate.
     * Controls the total number of spikes.
     * Default: NORMAL
     */
    @lombok.Builder.Default
    private Amount amount = Amount.NORMAL;

    /**
     * Base height level where spikes start growing.
     * Typically matches the terrain level.
     * Default: 64
     */
    @lombok.Builder.Default
    private int baseHeight = 64;

    /**
     * Minimum spike height in blocks.
     * Default: 10
     */
    @lombok.Builder.Default
    private int minHeight = 10;

    /**
     * Maximum spike height in blocks.
     * Default: 50
     */
    @lombok.Builder.Default
    private int maxHeight = 50;

    /**
     * Minimum spike base width in blocks.
     * Default: 1
     */
    @lombok.Builder.Default
    private int minWidth = 1;

    /**
     * Maximum spike base width in blocks.
     * Default: 3
     */
    @lombok.Builder.Default
    private int maxWidth = 3;

    /**
     * Distribution radius for spikes in blocks.
     * Spikes will be randomly placed within this radius from the point center.
     * Default: 100 (covers most of a hex grid)
     */
    @lombok.Builder.Default
    private int distributionRadius = 100;

    /**
     * Random seed for spike generation.
     * Different seeds create different spike patterns.
     * If null, uses system time.
     */
    private Long seed;

    /**
     * Material type for the spikes.
     * Examples: "stone", "ice", "crystal", "obsidian"
     * If null, uses default stone material.
     */
    private String material;

    /**
     * Taper factor - how quickly spikes narrow from base to tip.
     * 0.0 = cylindrical (no taper)
     * 0.5 = moderate taper (default)
     * 1.0 = very sharp taper (needle-like)
     */
    @lombok.Builder.Default
    private double taperFactor = 0.5;

    /**
     * Custom parameters for the spikes.
     */
    private Map<String, String> parameters;

    /**
     * Configures the HexGrid at the given coordinate with spikes configuration.
     * Called by PointComposer after the point has been positioned.
     *
     * Creates a g_spikes parameter with the spike design that will be used
     * by SingleSpikesBuilder to build the actual spikes on the grid.
     *
     * @param gridCoordinate The coordinate of the grid where this point is placed
     * @param hexGridSize Size of the hex grid
     * @param context The composition context
     */
    public void configureHexGrid(HexVector2 gridCoordinate, int hexGridSize, ComposeContext context) {
        log.debug("Configuring HexGrid for SpikesPoint '{}' at [{},{}] with hexGridSize: {}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), hexGridSize);

        // Create spikes configuration
        SpikesConfig config = SpikesConfig.builder()
            .spikesName(getName())
            .spikesTitle(getTitle())
            .density(density)
            .amount(amount)
            .baseHeight(baseHeight)
            .minHeight(minHeight)
            .maxHeight(maxHeight)
            .minWidth(minWidth)
            .maxWidth(maxWidth)
            .distributionRadius(distributionRadius)
            .seed(seed != null ? seed : System.currentTimeMillis())
            .material(material)
            .taperFactor(taperFactor)
            .build();

        // Serialize to JSON
        String configJson = serializeToJson(config);

        // Get FeatureHexGrid from central registry (Points are aspects, not grid owners)
        FeatureHexGrid grid = getFeatureHexGridFromRegistry(gridCoordinate, context);

        if (grid == null) {
            log.error("SpikesPoint '{}' cannot configure grid [{},{}] - registry access failed",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        // Add g_spikes parameter as aspect (with collision check)
        String existingSpikes = grid.getParameters().get("g_spikes");
        if (existingSpikes != null && !existingSpikes.isBlank()) {
            log.warn("SpikesPoint '{}' - grid [{},{}] already has g_spikes parameter! " +
                "Another aspect already defined spikes here. Skipping this SpikesPoint.",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        grid.addParameter("g_spikes", configJson);

        // Add basic structure parameters
        grid.addParameter("structure", "spikes");
        grid.addParameter("structureName", getName());
        grid.addParameter("spikesPointId", getFeatureId());

        // Copy custom parameters to grid
        if (parameters != null) {
            grid.getParameters().putAll(parameters);
        }

        log.debug("SpikesPoint '{}' configured on grid [{},{}] with density={}, amount={}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), density, amount);
    }

    /**
     * Serializes config to JSON string
     */
    private String serializeToJson(SpikesConfig config) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize SpikesConfig to JSON", e);
            return "{}";
        }
    }

    /**
     * Gets the FeatureHexGrid for this point's coordinate from the central registry.
     * Points are ASPEKTE - they don't create their own HexGrids, but add parameters
     * to existing grids from the central composition registry.
     *
     * @param gridCoordinate The grid coordinate
     * @param context The composition context with central registry
     * @return The FeatureHexGrid from the central registry
     */
    private FeatureHexGrid getFeatureHexGridFromRegistry(HexVector2 gridCoordinate, ComposeContext context) {
        if (context == null || context.getComposition() == null) {
            log.error("SpikesPoint '{}' has no composition context - cannot access grid registry",
                getName());
            return null;
        }

        // Get grid from central registry (will be created if not exists)
        FeatureHexGrid grid = context.getComposition().getOrCreateFeatureHexGrid(gridCoordinate);

        log.debug("SpikesPoint '{}' accessing FeatureHexGrid at [{},{}] from central registry",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR());

        return grid;
    }

    @Override
    public void applyDefaults() {
        super.applyDefaults();

        if (parameters == null) {
            parameters = new HashMap<>();
        }
    }

    /**
     * Configuration for a spike field.
     */
    @Data
    @lombok.Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpikesConfig {
        private String spikesName;
        private String spikesTitle;
        private Density density;
        private Amount amount;
        private int baseHeight;
        private int minHeight;
        private int maxHeight;
        private int minWidth;
        private int maxWidth;
        private int distributionRadius;
        private long seed;
        private String material;
        private double taperFactor;
    }
}
