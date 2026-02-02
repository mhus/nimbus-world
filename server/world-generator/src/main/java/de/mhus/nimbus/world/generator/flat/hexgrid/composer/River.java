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
public class River extends Flow {
    // Note: waypointIds, startPointId, and endPointId are inherited from Flow
    // For rivers, endPointId can be a merge point where rivers join

    private Integer depth;
    private Integer level;

    /**
     * Force flag: Controls error handling when river cannot reach destination.
     * - true: Throw error if river gets stuck or cannot reach goal
     * - false: Silently stop routing, use partial route (default)
     */
    private Boolean force;

    // Note: River uses Flow.Composed (no River-specific calculated fields yet)

    /**
     * Applies river-specific default configuration from FlowType.RIVER
     */
    @Override
    protected void applyFlowDefaults(Map<String, String> defaults) {
        if (depth == null && defaults.containsKey("default_depth")) {
            depth = Integer.parseInt(defaults.get("default_depth"));
        }
        if (level == null && defaults.containsKey("default_level")) {
            level = Integer.parseInt(defaults.get("default_level"));
        }
        if (getWidthBlocks() == null && defaults.containsKey("default_width")) {
            setWidthBlocks(Integer.parseInt(defaults.get("default_width")));
        }
    }

    /**
     * Configures HexGrids with river-specific parameters.
     * River-specific parameters are added by HexGridRoadConfigurator
     * after all flows have been composed.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Call parent to create basic FeatureHexGrids
        super.configureHexGrids(coordinates);
    }
}
