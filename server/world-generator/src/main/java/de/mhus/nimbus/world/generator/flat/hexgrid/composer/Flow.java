package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Flow extends Feature {
    private FlowType type;
    private String startPointId;
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

    // Calculated values (runtime, set during composition)
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

        // Calculate width from enum or explicit value
        calculatedWidthBlocks = getEffectiveWidthBlocks();
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

        // Store route
        this.route = coordinates;

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
}
