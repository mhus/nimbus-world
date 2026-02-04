package de.mhus.nimbus.world.generator.composer.flow;

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
public class Wall extends Flow {
    // Note: waypointIds, startPointId, and endPointId are inherited from Flow

    private Integer height;
    private Integer level;
    private String material;

    // Note: Wall uses Flow.Composed (no Wall-specific calculated fields yet)

    /**
     * Applies wall-specific default configuration from FlowType.WALL
     */
    @Override
    protected void applyFlowDefaults(Map<String, String> defaults) {
        if (height == null && defaults.containsKey("default_height")) {
            height = Integer.parseInt(defaults.get("default_height"));
        }
        if (level == null && defaults.containsKey("default_level")) {
            level = Integer.parseInt(defaults.get("default_level"));
        }
        if (material == null && defaults.containsKey("default_material")) {
            material = defaults.get("default_material");
        }
        if (getWidthBlocks() == null && defaults.containsKey("default_width")) {
            setWidthBlocks(Integer.parseInt(defaults.get("default_width")));
        }
    }

    /**
     * Configures HexGrids with wall-specific parameters.
     * Wall-specific parameters are added by HexGridRoadConfigurator
     * after all flows have been composed.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Call parent to create basic FeatureHexGrids
        super.configureHexGrids(coordinates);
    }
}
