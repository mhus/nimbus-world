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

    // Calculated values (runtime, set during composition)
    private Integer calculatedWidthBlocks;  // Resolved from width enum
    private HexVector2 startPoint;          // Resolved coordinate
    private HexVector2 endPoint;            // Resolved coordinate (Road/Wall) or merge point (River)
    private List<HexVector2> waypoints;     // Resolved waypoints
    private List<HexVector2> route;         // Calculated route from start to end

    public int getEffectiveWidthBlocks() {
        return widthBlocks != null ? widthBlocks : (width != null ? width.getFrom() : 2);
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
        // Calculate width from enum
        calculatedWidthBlocks = getEffectiveWidthBlocks();
    }

    /**
     * Configures HexGrids for this flow at the given coordinates (route).
     * Called by FlowComposer after routing to let the flow configure its own grids.
     * Override in subclasses for type-specific configuration.
     *
     * @param coordinates Ordered list of coordinates for the flow route
     */
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Store route
        this.route = coordinates;

        // Default implementation - override in subclasses
        // Base flows do nothing special
    }
}
