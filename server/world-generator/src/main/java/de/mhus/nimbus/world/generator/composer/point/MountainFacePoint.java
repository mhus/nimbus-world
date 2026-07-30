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
 * MountainFacePoint represents a steep mountain face with branching ridges.
 * Creates a spider-pattern cliff face using the SpiderPatternManipulator.
 *
 * During composition:
 * 1. PointComposer positions the MountainFacePoint on a HexGrid
 * 2. MountainFacePoint creates g_mountain_face configuration for that grid
 * 3. SingleMountainFaceBuilder reads g_mountain_face and builds the cliff face on the grid
 *
 * Example: Cliff faces, canyon walls, steep mountain ridges, escarpments
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class MountainFacePoint extends Point {

    /**
     * Dimension of the mountain face - affects size and complexity
     */
    public enum Dimension {
        SMALL,   // Small cliff: 3-4 branches, 30-40 length, 1 sub-branch
        MEDIUM,  // Medium cliff: 5-6 branches, 50-60 length, 2-3 sub-branches
        LARGE    // Large cliff: 7-9 branches, 70-90 length, 3-4 sub-branches
    }

    /**
     * Dimension/size of the mountain face.
     * Controls the number of branches, length, and complexity.
     * Default: MEDIUM
     */
    @lombok.Builder.Default
    private Dimension dimension = Dimension.MEDIUM;

    /**
     * Base height level where the mountain face starts.
     * This is the ground level at the base of the cliff.
     * Default: 64
     */
    @lombok.Builder.Default
    private int baseHeight = 64;

    /**
     * Height of the cliff face in blocks.
     * How much the face rises above the base height.
     * Default: 40
     */
    @lombok.Builder.Default
    private int faceHeight = 40;

    /**
     * Number of main branches/ridges radiating from center.
     * If not set, determined by dimension.
     * Range: 3-12
     */
    private Integer branches;

    /**
     * Length of main branches in blocks.
     * If not set, determined by dimension.
     * Range: 20-100
     */
    private Integer branchLength;

    /**
     * Number of sub-branches per main branch.
     * If not set, determined by dimension.
     * Range: 0-5
     */
    private Integer subBranches;

    /**
     * Recursion depth for sub-branching.
     * Higher values create more complex patterns.
     * Default: 2, Range: 1-4
     */
    @lombok.Builder.Default
    private int recursionDepth = 2;

    /**
     * Random seed for face generation.
     * Different seeds create different ridge patterns.
     * If null, uses system time.
     */
    private Long seed;

    /**
     * Material type for the mountain face.
     * Examples: "stone", "sandstone", "granite", "basalt"
     * If null, uses default stone material.
     */
    private String material;

    /**
     * Custom parameters for the mountain face.
     */
    private Map<String, String> parameters;

    /**
     * Configures the HexGrid at the given coordinate with mountain face configuration.
     * Called by PointComposer after the point has been positioned.
     *
     * Creates a g_mountain_face parameter with the cliff design that will be used
     * by SingleMountainFaceBuilder to build the actual mountain face on the grid.
     *
     * @param gridCoordinate The coordinate of the grid where this point is placed
     * @param hexGridSize Size of the hex grid
     * @param context The composition context
     */
    public void configureHexGrid(HexVector2 gridCoordinate, int hexGridSize, ComposeContext context) {
        log.debug("Configuring HexGrid for MountainFacePoint '{}' at [{},{}] with hexGridSize: {}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), hexGridSize);

        // Apply dimension defaults if specific values not set
        applyDimensionDefaults();

        // Create mountain face configuration
        MountainFaceConfig config = MountainFaceConfig.builder()
            .faceName(getName())
            .faceTitle(getTitle())
            .dimension(dimension)
            .baseHeight(baseHeight)
            .faceHeight(faceHeight)
            .branches(branches)
            .branchLength(branchLength)
            .subBranches(subBranches)
            .recursionDepth(recursionDepth)
            .seed(seed != null ? seed : System.currentTimeMillis())
            .material(material)
            .build();

        // Serialize to JSON
        String configJson = serializeToJson(config);

        // Get FeatureHexGrid from central registry (Points are aspects, not grid owners)
        FeatureHexGrid grid = getFeatureHexGridFromRegistry(gridCoordinate, context);

        if (grid == null) {
            log.error("MountainFacePoint '{}' cannot configure grid [{},{}] - registry access failed",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        // Add g_mountain_face parameter as aspect (with collision check)
        String existingFace = grid.getParameters().get("g_mountain_face");
        if (existingFace != null && !existingFace.isBlank()) {
            log.warn("MountainFacePoint '{}' - grid [{},{}] already has g_mountain_face parameter! " +
                "Another aspect already defined a mountain face here. Skipping this MountainFacePoint.",
                getName(), gridCoordinate.getQ(), gridCoordinate.getR());
            return;
        }

        grid.addParameter("g_mountain_face", configJson);

        // Add basic structure parameters
        grid.addParameter("structure", "mountain-face");
        grid.addParameter("structureName", getName());
        grid.addParameter("mountainFacePointId", getFeatureId());

        // Copy custom parameters to grid
        if (parameters != null) {
            grid.getParameters().putAll(parameters);
        }

        log.debug("MountainFacePoint '{}' configured on grid [{},{}] with dimension={}, height={}",
            getName(), gridCoordinate.getQ(), gridCoordinate.getR(), dimension, faceHeight);
    }

    /**
     * Apply dimension-based defaults for branches, length, and sub-branches
     */
    private void applyDimensionDefaults() {
        // Derive randomness from the seed so identical seeds produce identical
        // dimensions (reproducible worlds); Math.random() would make the
        // serialized config non-deterministic despite the seed.
        java.util.Random rng = new java.util.Random(seed != null ? seed : System.currentTimeMillis());

        if (branches == null) {
            branches = switch (dimension) {
                case SMALL -> 3 + rng.nextInt(2);   // 3-4
                case MEDIUM -> 5 + rng.nextInt(2);  // 5-6
                case LARGE -> 7 + rng.nextInt(3);   // 7-9
            };
        }

        if (branchLength == null) {
            branchLength = switch (dimension) {
                case SMALL -> 30 + rng.nextInt(11);  // 30-40
                case MEDIUM -> 50 + rng.nextInt(11); // 50-60
                case LARGE -> 70 + rng.nextInt(21);  // 70-90
            };
        }

        if (subBranches == null) {
            subBranches = switch (dimension) {
                case SMALL -> 1;                     // 1
                case MEDIUM -> 2 + rng.nextInt(2);   // 2-3
                case LARGE -> 3 + rng.nextInt(2);    // 3-4
            };
        }
    }

    /**
     * Serializes config to JSON string
     */
    private String serializeToJson(MountainFaceConfig config) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize MountainFaceConfig to JSON", e);
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
            log.error("MountainFacePoint '{}' has no composition context - cannot access grid registry",
                getName());
            return null;
        }

        // Get grid from central registry (will be created if not exists)
        FeatureHexGrid grid = context.getComposition().getOrCreateFeatureHexGrid(gridCoordinate);

        log.debug("MountainFacePoint '{}' accessing FeatureHexGrid at [{},{}] from central registry",
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
     * Configuration for a mountain face.
     */
    @Data
    @lombok.Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MountainFaceConfig {
        private String faceName;
        private String faceTitle;
        private Dimension dimension;
        private int baseHeight;
        private int faceHeight;
        private int branches;
        private int branchLength;
        private int subBranches;
        private int recursionDepth;
        private long seed;
        private String material;
    }
}
