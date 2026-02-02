package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    }

    public int getEffectiveWidthBlocks() {
        return widthBlocks != null ? widthBlocks : (width != null ? width.getFrom() : 2);
    }

    /**
     * Gets the effective radius for closed loops.
     * Priority: sizeFrom/sizeTo > size enum > default (3)
     */
    public int getEffectiveSizeFrom() {
        return sizeFrom != null ? sizeFrom : (size != null ? size.getFrom() : 3);
    }

    /**
     * Gets the effective maximum radius for closed loops.
     * Priority: sizeFrom/sizeTo > size enum > default (3)
     */
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
    public void configureHexGrids(List<HexVector2> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return;
        }

        // Initialize flowComposed if needed
        if (flowComposed == null) {
            flowComposed = new FlowComposed();
        }

        // Store route
        flowComposed.setRoute(coordinates);

        // Clear existing configurations
        if (getHexGrids() != null) {
            getHexGrids().clear();
        }

        // Create FeatureHexGrid for each coordinate
        for (HexVector2 coord : coordinates) {
            FeatureHexGrid featureHexGrid = FeatureHexGrid.builder()
                .coordinate(coord)
                .name(getName() + " [" + coord.getQ() + "," + coord.getR() + "]")
                .description("Flow segment for " + getName())
                .build();

            // Add to this feature
            addHexGrid(featureHexGrid);
        }
    }

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
}
