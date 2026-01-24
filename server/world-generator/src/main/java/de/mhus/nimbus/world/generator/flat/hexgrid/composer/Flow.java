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
     * Configures HexGrids for this flow at the given coordinates (route).
     * Called by FlowComposer after routing to let the flow configure its own grids.
     * Override in subclasses for type-specific configuration.
     *
     * @param coordinates Ordered list of coordinates for the flow route
     */
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Default implementation - override in subclasses
        // Base flows do nothing special
    }
}
