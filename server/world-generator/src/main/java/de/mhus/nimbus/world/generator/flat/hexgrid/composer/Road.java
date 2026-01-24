package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Road extends Flow {
    private List<String> waypointIds;
    private String endPointId;
    private String roadType;
    private Integer level;

    public static RoadBuilder builder() {
        return new RoadBuilder();
    }

    /**
     * Applies road-specific default configuration from FlowType.ROAD
     */
    @Override
    protected void applyFlowDefaults(Map<String, String> defaults) {
        if (level == null && defaults.containsKey("default_level")) {
            level = Integer.parseInt(defaults.get("default_level"));
        }
        if (roadType == null && defaults.containsKey("default_roadType")) {
            roadType = defaults.get("default_roadType");
        }
        if (getWidthBlocks() == null && defaults.containsKey("default_width")) {
            setWidthBlocks(Integer.parseInt(defaults.get("default_width")));
        }
    }

    /**
     * Configures HexGrids with road-specific parameters.
     * Road-specific parameters are added by HexGridRoadConfigurator
     * after all flows have been composed.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Call parent to create basic FeatureHexGrids
        super.configureHexGrids(coordinates);
    }
}
