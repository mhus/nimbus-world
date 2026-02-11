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

    // Note: configureHexGrids() was removed - rivers write directly to central registry

    /**
     * Calculates river level with downhill constraint and minimum level 0.
     * Overrides Flow.calculateSegmentLevel() to ensure rivers never flow uphill.
     * In ADJUST mode: level2 = min(level2, level1) to enforce downhill flow.
     * River level must never be below 0 (sea level minimum).
     */
    @Override
    public int calculateSegmentLevel(Integer gridALandLevel, Integer gridALandOffset,
                                      Integer gridBLandLevel, Integer gridBLandOffset,
                                      Integer previousLevel, Integer fixedLevel) {
        // Use parent calculation
        int level = super.calculateSegmentLevel(gridALandLevel, gridALandOffset,
            gridBLandLevel, gridBLandOffset, previousLevel, fixedLevel);

        // For ADJUST mode with previousLevel: enforce downhill (level2 <= level1)
        LevelMode mode = getLevelMode() != null ? getLevelMode() : LevelMode.FIXED;
        if (previousLevel != null) {
            level = Math.min(level, previousLevel);
        }

        // River level must never go below 0 (absolute minimum)
        level = Math.max(0, level);

        return level;
    }
}
