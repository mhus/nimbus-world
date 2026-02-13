package de.mhus.nimbus.world.generator.composer.flow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.area.AreaSize;
import de.mhus.nimbus.world.generator.composer.point.Point;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Flow extends Feature {
    private FlowType type;

    /**
     * ID of the starting Point feature.
     * All flows must start at a Point (not a Biome).
     */
    private String startPointId;

    /**
     * ID of the ending Point feature.
     * All flows must end at a Point (not a Biome).
     * For rivers, this can be a merge point where rivers join.
     */
    private String endPointId;

    /**
     * List of waypoint Point IDs that the flow should pass through.
     * All waypoints must be Points (not Biomes).
     */
    private List<String> waypointIds;

    private FlowWidth width;
    private Integer widthBlocks;
    private Map<String, String> parameters;

    /**
     * Level mode - determines how flow height is calculated.
     * FIXED: Use fixed 'level' parameter (default if not specified)
     * ADJUST_MEAN: Adapt to terrain with half offset: meanHeight + offset/2
     * ADJUST_MINIMUM: Adapt to terrain without offset: meanHeight
     * ADJUST_MAXIMUM: Adapt to terrain with full offset: meanHeight + offset
     * where meanHeight = landLevel + landOffset/2
     */
    private LevelMode levelMode;

    /**
     * Mean level offset - used when levelMode is ADJUST_MEAN, ADJUST_MINIMUM, or ADJUST_MAXIMUM.
     * For ADJUST_MEAN: meanHeight + offset/2
     * For ADJUST_MINIMUM: meanHeight (offset not used)
     * For ADJUST_MAXIMUM: meanHeight + offset
     * where meanHeight = landLevel + landOffset/2
     */
    private Integer meanLevelOffset;

    // Route deviation control (for curves)
    private DeviationTendency tendLeft;
    private DeviationTendency tendRight;

    // Closed loop configuration (when startPointId == endPointId)
    private Boolean closedLoop;      // If true, creates a closed ring/loop around the point
    private String shapeHint;        // Shape hint for closed loops: "RING", "CIRCLE", "SQUARE", etc.
    private AreaSize size;           // Size of the closed loop (radius) - uses same enum as Biome
    private Integer sizeFrom;        // Explicit radius min (overrides size enum)
    private Integer sizeTo;          // Explicit radius max (overrides size enum)

    /**
     * Composed data - calculated during composition phase at Flow level.
     * Separates input configuration from runtime computed values.
     */
    private FlowComposed flowComposed;

    /**
     * Inner class for flowComposed (calculated) data at Flow level.
     * Stores values computed during composition, separate from user input.
     *
     * Note: hexGrids is temporary storage during composition phase.
     * Flows configure their FeatureHexGrids here, then they are copied to
     * the central HexComposition.featureHexGridRegistry by FlowComposer.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowComposed {
        private Integer calculatedWidthBlocks;  // Resolved from width enum
        private HexVector2 startPoint;          // Resolved coordinate
        private HexVector2 endPoint;            // Resolved coordinate (Road/Wall) or merge point (River)
        private List<HexVector2> waypoints;     // Resolved waypoints
        private List<HexVector2> route;         // Calculated route from start to end

        /**
         * Reference to actual Point feature if startPointId refers to a Point (not a Biome).
         * Used to extract lx/lz coordinates for flow connection.
         */
        private Point startPointFeature;

        /**
         * Reference to actual Point feature if endPointId refers to a Point (not a Biome).
         * Used to extract lx/lz coordinates for flow connection.
         * Note: endPointId is defined in subclasses (Road, Wall) not in Flow base class.
         */
        private Point endPointFeature;

        // Note: Flow.hexGrids was removed - flows now write directly to central registry
        // via composition.getOrCreateFeatureHexGrid() during FlowComposer.createFlowSegments()
    }

    @JsonIgnore
    public int getEffectiveWidthBlocks() {
        return widthBlocks != null ? widthBlocks : (width != null ? width.getFrom() : 2);
    }

    /**
     * Gets the effective radius for closed loops.
     * Priority: sizeFrom/sizeTo > size enum > default (3)
     */
    @JsonIgnore
    public int getEffectiveSizeFrom() {
        return sizeFrom != null ? sizeFrom : (size != null ? size.getFrom() : 3);
    }

    /**
     * Gets the effective maximum radius for closed loops.
     * Priority: sizeFrom/sizeTo > size enum > default (3)
     */
    @JsonIgnore
    public int getEffectiveSizeTo() {
        return sizeTo != null ? sizeTo : (size != null ? size.getTo() : 3);
    }

    /**
     * Returns true if this flow is configured as a closed loop.
     * A closed loop is when startPointId == endPointId OR closedLoop == true.
     */
    public boolean isClosedLoop() {
        return closedLoop != null && closedLoop;
    }

    /**
     * Applies default configuration for this flow type.
     * Override in subclasses for type-specific defaults.
     */
    @Override
    public void applyDefaults() {
        if (type == null) {
            return;
        }

        // Apply defaults from FlowType enum
        // Subclasses can use these defaults or override them
        Map<String, String> defaults = type.getDefaultParameters();
        if (defaults != null) {
            applyFlowDefaults(defaults);
        }
    }

    /**
     * Hook for subclasses to apply flow-specific defaults.
     * Base implementation does nothing - override in subclasses.
     */
    protected void applyFlowDefaults(Map<String, String> defaults) {
        // Base implementation - subclasses override
    }

    /**
     * Prepares this flow for composition by calculating concrete values from enums.
     * Called by HexCompositionPreparer before FlowComposer routes the flow.
     */
    public void prepareForComposition() {
        // Apply defaults first (if not already applied)
        applyDefaults();

        // Initialize flowComposed if needed
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }

        // Calculate width from enum or explicit value
        flowComposed.setCalculatedWidthBlocks(getEffectiveWidthBlocks());
    }

    /**
     * Configures HexGrids for this flow at the given coordinates (route).
     * Called by FlowComposer after routing to let the flow configure its own grids.
     * Creates FeatureHexGrid objects with flow-specific parameters.
     * Override in subclasses for type-specific configuration.
     *
     * @param coordinates Ordered list of coordinates for the flow route
     */
    // Note: configureHexGrids() was removed - flows now write directly to central registry
    // No need to pre-configure hexGrids, they are created on-the-fly during createFlowSegments()

    // Helper methods for backward compatibility

    public Integer getCalculatedWidthBlocks() {
        return flowComposed != null ? flowComposed.getCalculatedWidthBlocks() : null;
    }

    public void setCalculatedWidthBlocks(Integer calculatedWidthBlocks) {
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }
        flowComposed.setCalculatedWidthBlocks(calculatedWidthBlocks);
    }

    public HexVector2 getStartPoint() {
        return flowComposed != null ? flowComposed.getStartPoint() : null;
    }

    public void setStartPoint(HexVector2 startPoint) {
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }
        flowComposed.setStartPoint(startPoint);
    }

    public HexVector2 getEndPoint() {
        return flowComposed != null ? flowComposed.getEndPoint() : null;
    }

    public void setEndPoint(HexVector2 endPoint) {
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }
        flowComposed.setEndPoint(endPoint);
    }

    public List<HexVector2> getWaypoints() {
        return flowComposed != null ? flowComposed.getWaypoints() : null;
    }

    public void setWaypoints(List<HexVector2> waypoints) {
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }
        flowComposed.setWaypoints(waypoints);
    }

    public List<HexVector2> getRoute() {
        return flowComposed != null ? flowComposed.getRoute() : null;
    }

    public void setRoute(List<HexVector2> route) {
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }
        flowComposed.setRoute(route);
    }

    public Point getStartPointFeature() {
        return flowComposed != null ? flowComposed.getStartPointFeature() : null;
    }

    public void setStartPointFeature(Point startPointFeature) {
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }
        flowComposed.setStartPointFeature(startPointFeature);
    }

    public Point getEndPointFeature() {
        return flowComposed != null ? flowComposed.getEndPointFeature() : null;
    }

    public void setEndPointFeature(Point endPointFeature) {
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }
        flowComposed.setEndPointFeature(endPointFeature);
    }

    // Note: getHexGrids(), setHexGrids(), addHexGrid() and findHexGrid() were removed
    // Flows now write directly to central registry via composition.getOrCreateFeatureHexGrid()

    /**
     * Selects the next step towards the goal from the given neighbors.
     * Default: greedy — pick neighbor closest to goal.
     * Subclasses override for custom routing (e.g., river downhill preference).
     *
     * @param current Current position
     * @param goal Target position
     * @param neighbors Available neighbor coordinates
     * @param terrainLevelAt Returns terrain level for a coordinate
     * @param isLowPriorityBiome Returns true for Ocean/Coast/Island biomes
     */
    public HexVector2 selectNextStep(HexVector2 current, HexVector2 goal,
            List<HexVector2> neighbors,
            ToIntFunction<HexVector2> terrainLevelAt,
            Predicate<HexVector2> isLowPriorityBiome) {
        if (neighbors == null || neighbors.isEmpty()) return null;
        HexVector2 best = null;
        int bestDist = Integer.MAX_VALUE;
        for (HexVector2 n : neighbors) {
            int dist = hexDistance(n, goal);
            if (dist < bestDist) { bestDist = dist; best = n; }
        }
        return best;
    }

    /** Maximum routing steps (segments) for this flow. */
    public int getMaxRoutingSteps() { return 50; }

    /**
     * Pre-calculates levels for the entire route (two-pass approach).
     * Returns null by default, meaning per-segment calculation is used instead.
     * Override in River for downhill-constrained, monotonically decreasing level calculation.
     *
     * @param route Ordered list of coordinates for the flow route
     * @param rawLevelAt Returns the raw (unconstrained) level at a coordinate
     * @param seaLevel Minimum allowed level (sea level floor)
     * @return List of levels (one per route coordinate), or null to use per-segment calculation
     */
    public List<Integer> calculateRouteLevels(List<HexVector2> route,
            ToIntFunction<HexVector2> rawLevelAt, int seaLevel) {
        return null; // Default: use per-segment calculation
    }

    /** Hex distance utility (cube coordinate formula). */
    public static int hexDistance(HexVector2 a, HexVector2 b) {
        int dq = Math.abs(a.getQ() - b.getQ());
        int dr = Math.abs(a.getR() - b.getR());
        int ds = Math.abs((a.getQ() + a.getR()) - (b.getQ() + b.getR()));
        return (dq + dr + ds) / 2;
    }

    /**
     * Calculates the level for a flow segment based on levelMode.
     *
     * @param gridALandLevel landLevel of current grid
     * @param gridALandOffset landOffset of current grid
     * @param gridBLandLevel landLevel of next grid (can be null for last segment)
     * @param gridBLandOffset landOffset of next grid (can be null for last segment)
     * @param previousLevel level from previous segment (for continuation)
     * @param fixedLevel fixed level value (used when levelMode=FIXED)
     * @return calculated level for this segment
     */
    public int calculateSegmentLevel(Integer gridALandLevel, Integer gridALandOffset,
                                      Integer gridBLandLevel, Integer gridBLandOffset,
                                      Integer previousLevel, Integer fixedLevel) {
        // Default to FIXED mode if not specified
        LevelMode mode = levelMode != null ? levelMode : LevelMode.FIXED;

        if (mode == LevelMode.FIXED) {
            // Use fixed level (default to 0 if not specified)
            return fixedLevel != null ? fixedLevel : 0;
        } else {
            // ADJUST mode - calculate based on biome mean heights
            int offset = meanLevelOffset != null ? meanLevelOffset : 0;

            // Calculate mean height of gridA
            int gridALand = gridALandLevel != null ? gridALandLevel : 0;
            int gridAOffset = gridALandOffset != null ? gridALandOffset : 0;
            int gridAMeanHeight = gridALand + gridAOffset / 2;

            // Calculate mean height (will be used for all ADJUST modes)
            int meanHeight;
            if (previousLevel != null) {
                // Calculate level2 for segment end
                if (gridBLandLevel != null) {
                    // We have gridB - calculate average of both grids
                    int gridBLand = gridBLandLevel;
                    int gridBOffset = gridBLandOffset != null ? gridBLandOffset : 0;
                    int gridBMeanHeight = gridBLand + gridBOffset / 2;

                    meanHeight = (gridAMeanHeight + gridBMeanHeight) / 2;
                } else {
                    // Last segment - just use gridA
                    meanHeight = gridAMeanHeight;
                }
            } else {
                // First segment - use gridA mean height
                meanHeight = gridAMeanHeight;
            }

            // Apply offset based on mode
            if (mode == LevelMode.ADJUST_MEAN) {
                // ADJUST_MEAN: offset/2 (ADJUST is deprecated, treated as ADJUST_MEAN)
                return meanHeight + offset / 2;
            } else if (mode == LevelMode.ADJUST_MINIMUM) {
                // ADJUST_MINIMUM: no offset
                return meanHeight;
            } else if (mode == LevelMode.ADJUST_MAXIMUM) {
                // ADJUST_MAXIMUM: full offset
                return meanHeight + offset;
            }

            // Fallback (should not happen)
            return meanHeight + offset / 2;
        }
    }
}
