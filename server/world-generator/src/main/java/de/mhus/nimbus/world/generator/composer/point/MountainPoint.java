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
 * MountainPoint represents a single mountain positioned at a specific point.
 * Creates a single mountain peak using the MountainManipulator.
 *
 * During composition:
 * 1. PointComposer positions the MountainPoint on a HexGrid
 * 2. MountainPoint creates g_mountain configuration for that grid
 * 3. SingleMountainBuilder reads g_mountain and builds the mountain on the grid
 *
 * Example: A volcanic peak, a sacred mountain, or a landmark summit
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class MountainPoint extends Point {

    /**
     * Radius of the mountain base in blocks.
     * Determines how wide the mountain will be at ground level.
     * Default: 150 (medium-sized mountain)
     */
    @lombok.Builder.Default
    private int radius = 150;

    /**
     * Height of the mountain peak above base level.
     * This is the total height from ground to summit.
     * Default: 100 (medium height)
     */
    @lombok.Builder.Default
    private int peakHeight = 100;

    /**
     * Base height level where the mountain starts.
     * Typically matches the terrain level (around 64-95).
     * Default: 64
     */
    @lombok.Builder.Default
    private int baseHeight = 64;

    /**
     * Random seed for mountain generation.
     * Different seeds create different mountain shapes.
     * If null, uses system time.
     */
    private Long seed;

    /**
     * Material type for the mountain surface.
     * Examples: "stone", "snow", "volcanic"
     * If null, uses default material from terrain.
     */
    private String material;

    /**
     * Roughness factor for mountain surface (0.0 - 1.0).
     * 0.0 = smooth cone, 1.0 = very jagged/rough.
     * Default: 0.5 (natural roughness)
     */
    @lombok.Builder.Default
    private double roughness = 0.5;

    /**
     * Whether to create a volcanic crater at the peak.
     * If true, creates a depression at the summit.
     * Default: false
     */
    @lombok.Builder.Default
    private boolean crater = false;

    /**
     * Custom parameters for the mountain.
     */
    private Map<String, String> parameters;

    /**
     * Configures the HexGrid at the given coordinate with mountain configuration.
     * Called by PointComposer after the point has been positioned.
     *
     * Creates a g_mountain parameter with the mountain design that will be used
     * by SingleMountainBuilder to build the actual mountain on the grid.
     *
     * @param gridCoordinate The coordinate of the grid where this point is placed
     * @param hexGridSize Size of the hex grid
     * @param context The composition context
     */
    public void configureHexGrid(HexVector2 gridCoordinate, int hexGridSize, ComposeContext context) {
        log.debug("Configuring HexGrid for MountainPoint '{}' at [{},{}] with hexGridSize: {}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), hexGridSize);

        // Create mountain configuration
        MountainConfig config = MountainConfig.builder()
            .mountainName(getName())
            .mountainTitle(getTitle())
            .radius(radius)
            .peakHeight(peakHeight)
            .baseHeight(baseHeight)
            .seed(seed != null ? seed : System.currentTimeMillis())
            .material(material)
            .roughness(roughness)
            .crater(crater)
            .build();

        // Serialize to JSON
        String configJson = serializeToJson(config);

        // Get FeatureHexGrid from central registry (Points are aspects, not grid owners)
        FeatureHexGrid grid = getFeatureHexGridFromRegistry(gridCoordinate, context);

        if (grid == null) {
            log.error("MountainPoint '{}' cannot configure grid [{},{}] - registry access failed",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        // Add g_mountain parameter as aspect (with collision check)
        String existingMountain = grid.getParameters().get("g_mountain");
        if (existingMountain != null && !existingMountain.isBlank()) {
            log.warn("MountainPoint '{}' - grid [{},{}] already has g_mountain parameter! " +
                "Another aspect already defined a mountain here. Skipping this MountainPoint.",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        grid.addParameter("g_mountain", configJson);

        // Add basic structure parameters
        grid.addParameter("structure", "mountain");
        grid.addParameter("structureName", getName());
        grid.addParameter("mountainPointId", getFeatureId());

        // Copy custom parameters to grid
        if (parameters != null) {
            grid.getParameters().putAll(parameters);
        }

        log.debug("MountainPoint '{}' configured on grid [{},{}] with radius={}, height={}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), radius, peakHeight);
    }

    /**
     * Serializes config to JSON string
     */
    private String serializeToJson(MountainConfig config) {
        try {
            tools.jackson.databind.ObjectMapper mapper =
                new tools.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize MountainConfig to JSON", e);
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
            log.error("MountainPoint '{}' has no composition context - cannot access grid registry",
                getName());
            return null;
        }

        // Get grid from central registry (will be created if not exists)
        FeatureHexGrid grid = context.getComposition().getOrCreateFeatureHexGrid(gridCoordinate);

        log.debug("MountainPoint '{}' accessing FeatureHexGrid at [{},{}] from central registry",
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
     * Configuration for a single mountain.
     */
    @Data
    @lombok.Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MountainConfig {
        private String mountainName;
        private String mountainTitle;
        private int radius;
        private int peakHeight;
        private int baseHeight;
        private long seed;
        private String material;
        private double roughness;
        private boolean crater;
    }
}
