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
public class Road extends Flow {
    // Note: waypointIds, startPointId, and endPointId are inherited from Flow

    private String roadType;
    private Integer level;

    // Note: Road uses Flow.Composed (no Road-specific calculated fields yet)

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

    /**
     * Overrides Flow.calculateSegmentLevel() to ensure roads never go below level 1.
     * Road level must never be below 1 (minimum for roads/paths).
     */
    @Override
    public int calculateSegmentLevel(Integer gridALandLevel, Integer gridALandOffset,
                                      Integer gridBLandLevel, Integer gridBLandOffset,
                                      Integer previousLevel, Integer fixedLevel) {
        // Use parent calculation
        int level = super.calculateSegmentLevel(gridALandLevel, gridALandOffset,
            gridBLandLevel, gridBLandOffset, previousLevel, fixedLevel);

        // Road level must never go below 1 (absolute minimum)
        level = Math.max(1, level);

        return level;
    }
}
