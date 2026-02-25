package de.mhus.nimbus.world.generator.composer.flow;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Road extends Flow {
    // Note: waypointIds, startPointId, and endPointId are inherited from Flow

    private RoadType roadType;
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
            roadType = RoadType.fromString(defaults.get("default_roadType"));
        }
        if (getWidthBlocks() == null && defaults.containsKey("default_width")) {
            setWidthBlocks(Integer.parseInt(defaults.get("default_width")));
        }
    }

    // Note: configureHexGrids() was removed - roads write directly to central registry

    /**
     * Pre-calculates levels for the entire road route.
     * - Start/End: terrain level of that grid
     * - Intermediate edges: average of two adjacent grid terrain levels
     * - Minimum: seaLevel + 1 (roads must be above water)
     * - No monotonic constraint — roads can go uphill and downhill
     */
    @Override
    public List<Integer> calculateRouteLevels(List<HexVector2> route,
            ToIntFunction<HexVector2> rawLevelAt, int seaLevel) {
        int minLevel = seaLevel + 1;
        List<Integer> levels = new ArrayList<>(route.size());

        for (int i = 0; i < route.size(); i++) {
            int lvl;
            if (i == 0 || i == route.size() - 1) {
                // Start / End: use this grid's terrain level
                lvl = rawLevelAt.applyAsInt(route.get(i));
            } else {
                // Intermediate: edge level = average of previous and current grid
                int prevTerrain = rawLevelAt.applyAsInt(route.get(i - 1));
                int currTerrain = rawLevelAt.applyAsInt(route.get(i));
                lvl = (prevTerrain + currTerrain) / 2;
            }
            levels.add(Math.max(minLevel, lvl));
        }

        return levels;
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
