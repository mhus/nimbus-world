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
 * LakesPoint represents a lake system positioned at a specific point.
 * Creates a main lake with multiple smaller lakes using the LakesManipulator.
 *
 * During composition:
 * 1. PointComposer positions the LakesPoint on a HexGrid
 * 2. LakesPoint creates g_lakes configuration for that grid
 * 3. LakesBuilder reads g_lakes and builds the lake system on the grid
 *
 * Example: Lake districts, crater lakes, oasis systems, pond clusters
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class LakesPoint extends Point {

    /**
     * Radius of the main lake in blocks.
     * Determines the size of the central lake.
     * Default: 35, Range: 10-100
     */
    @lombok.Builder.Default
    private int mainLakeRadius = 35;

    /**
     * Depth of the main lake below ocean/base level in blocks.
     * How deep the lake depression goes.
     * Default: 25, Range: 5-50
     */
    @lombok.Builder.Default
    private int mainLakeDepth = 25;

    /**
     * Number of small lakes scattered around the main lake.
     * Creates a lake system with multiple water bodies.
     * Default: 6, Range: 0-15
     */
    @lombok.Builder.Default
    private int smallLakes = 6;

    /**
     * Minimum radius of small lakes in blocks.
     * Default: 8, Range: 5-50
     */
    @lombok.Builder.Default
    private int smallLakeMinRadius = 8;

    /**
     * Maximum radius of small lakes in blocks.
     * Default: 15, Range: smallLakeMinRadius-50
     */
    @lombok.Builder.Default
    private int smallLakeMaxRadius = 15;

    /**
     * Scatter distance for small lakes in blocks.
     * How far from the main lake small lakes can be placed.
     * Default: 50, Range: mainLakeRadius-150
     */
    @lombok.Builder.Default
    private int scatterDistance = 50;

    /**
     * Random seed for lake generation.
     * Different seeds create different lake patterns.
     * If null, uses system time.
     */
    private Long seed;

    /**
     * Custom parameters for the lakes.
     */
    private Map<String, String> parameters;

    /**
     * Configures the HexGrid at the given coordinate with lakes configuration.
     * Called by PointComposer after the point has been positioned.
     *
     * Creates a g_lakes parameter with the lake design that will be used
     * by LakesBuilder to build the actual lake system on the grid.
     *
     * @param gridCoordinate The coordinate of the grid where this point is placed
     * @param hexGridSize Size of the hex grid
     * @param context The composition context
     */
    public void configureHexGrid(HexVector2 gridCoordinate, int hexGridSize, ComposeContext context) {
        log.debug("Configuring HexGrid for LakesPoint '{}' at [{},{}] with hexGridSize: {}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), hexGridSize);

        // Create lakes configuration
        LakesConfig config = LakesConfig.builder()
            .lakesName(getName())
            .lakesTitle(getTitle())
            .mainLakeRadius(mainLakeRadius)
            .mainLakeDepth(mainLakeDepth)
            .smallLakes(smallLakes)
            .smallLakeMinRadius(smallLakeMinRadius)
            .smallLakeMaxRadius(smallLakeMaxRadius)
            .scatterDistance(scatterDistance)
            .seed(seed != null ? seed : System.currentTimeMillis())
            .build();

        // Serialize to JSON
        String configJson = serializeToJson(config);

        // Get FeatureHexGrid from central registry (Points are aspects, not grid owners)
        FeatureHexGrid grid = getFeatureHexGridFromRegistry(gridCoordinate, context);

        if (grid == null) {
            log.error("LakesPoint '{}' cannot configure grid [{},{}] - registry access failed",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        // Add g_lakes parameter as aspect (with collision check)
        String existingLakes = grid.getParameters().get("g_lakes");
        if (existingLakes != null && !existingLakes.isBlank()) {
            log.warn("LakesPoint '{}' - grid [{},{}] already has g_lakes parameter! " +
                "Another aspect already defined lakes here. Skipping this LakesPoint.",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        grid.addParameter("g_lakes", configJson);

        // Add basic structure parameters
        grid.addParameter("structure", "lakes");
        grid.addParameter("structureName", getName());
        grid.addParameter("lakesPointId", getFeatureId());

        // Copy custom parameters to grid
        if (parameters != null) {
            grid.getParameters().putAll(parameters);
        }

        log.debug("LakesPoint '{}' configured on grid [{},{}] with mainRadius={}, smallLakes={}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), mainLakeRadius, smallLakes);
    }

    /**
     * Serializes config to JSON string
     */
    private String serializeToJson(LakesConfig config) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize LakesConfig to JSON", e);
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
            log.error("LakesPoint '{}' has no composition context - cannot access grid registry",
                getName());
            return null;
        }

        // Get grid from central registry (will be created if not exists)
        FeatureHexGrid grid = context.getComposition().getOrCreateFeatureHexGrid(gridCoordinate);

        log.debug("LakesPoint '{}' accessing FeatureHexGrid at [{},{}] from central registry",
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
     * Configuration for a lake system.
     */
    @Data
    @lombok.Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LakesConfig {
        private String lakesName;
        private String lakesTitle;
        private int mainLakeRadius;
        private int mainLakeDepth;
        private int smallLakes;
        private int smallLakeMinRadius;
        private int smallLakeMaxRadius;
        private int scatterDistance;
        private long seed;
    }
}
