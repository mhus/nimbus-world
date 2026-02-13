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
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

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
     * Force flag: Previously controlled error handling when river cannot reach destination.
     * No longer has any effect — rivers now always use best-effort routing.
     * Kept for JSON backward compatibility.
     */
    @Deprecated
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
     * River routing: prefer downhill neighbors, fallback to all neighbors if stuck.
     * No ocean-level minimum — rivers CAN flow into coast/ocean terrain.
     * Ocean/Coast/Island biomes have lower priority unless they are the goal
     * or genuinely closer to the goal than the best high-priority alternative.
     */
    @Override
    public HexVector2 selectNextStep(HexVector2 current, HexVector2 goal,
            List<HexVector2> neighbors,
            ToIntFunction<HexVector2> terrainLevelAt,
            Predicate<HexVector2> isLowPriorityBiome) {
        if (neighbors == null || neighbors.isEmpty()) return null;
        int currentLevel = terrainLevelAt.applyAsInt(current);

        // 1. Prefer downhill (level <= current)
        List<HexVector2> downhill = new ArrayList<>();
        for (HexVector2 n : neighbors) {
            if (terrainLevelAt.applyAsInt(n) <= currentLevel) downhill.add(n);
        }

        // 2. Fallback: ALL neighbors if no downhill available
        List<HexVector2> candidates = downhill.isEmpty() ? neighbors : downhill;

        // 3. Separate into high-priority (land) and low-priority (Ocean/Coast/Island)
        List<HexVector2> highPriority = new ArrayList<>();
        List<HexVector2> lowPriority = new ArrayList<>();
        for (HexVector2 n : candidates) {
            if (n.equals(goal)) {
                // Goal always has highest priority — return immediately
                return n;
            }
            if (isLowPriorityBiome.test(n)) {
                lowPriority.add(n);
            } else {
                highPriority.add(n);
            }
        }

        // 4. Find best from each group
        HexVector2 bestHigh = closestToGoal(highPriority, goal);
        HexVector2 bestLow = closestToGoal(lowPriority, goal);

        // 5. If no high-priority candidates, use low-priority
        if (bestHigh == null) return bestLow;
        // If no low-priority candidates, use high-priority
        if (bestLow == null) return bestHigh;

        // 6. Use low-priority only if it is genuinely closer to goal
        int highDist = hexDistance(bestHigh, goal);
        int lowDist = hexDistance(bestLow, goal);
        return (lowDist < highDist) ? bestLow : bestHigh;
    }

    private static HexVector2 closestToGoal(List<HexVector2> candidates, HexVector2 goal) {
        HexVector2 best = null;
        int bestDist = Integer.MAX_VALUE;
        for (HexVector2 n : candidates) {
            int dist = hexDistance(n, goal);
            if (dist < bestDist) { bestDist = dist; best = n; }
        }
        return best;
    }

    /**
     * Pre-calculates levels for the entire river route using a two-pass approach.
     * Pass 1: Calculate raw level at each coordinate (terrain-based via levelMode).
     * Pass 2: Enforce monotonically decreasing (river flows downhill).
     * Pass 3: Enforce minimum = sea level.
     *
     * This ensures continuity: endLevel of segment N = startLevel of segment N+1.
     * All levels are absolute (not relative to biome).
     */
    @Override
    public List<Integer> calculateRouteLevels(List<HexVector2> route,
            ToIntFunction<HexVector2> rawLevelAt, int seaLevel) {
        List<Integer> levels = new ArrayList<>(route.size());

        // Pass 1: Get raw level at each coordinate
        for (HexVector2 coord : route) {
            levels.add(rawLevelAt.applyAsInt(coord));
        }

        // Pass 2: Enforce monotonically decreasing (river only flows downhill)
        for (int i = 1; i < levels.size(); i++) {
            if (levels.get(i) > levels.get(i - 1)) {
                levels.set(i, levels.get(i - 1));
            }
        }

        // Pass 3: Enforce minimum = sea level
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i) < seaLevel) {
                levels.set(i, seaLevel);
            }
        }

        return levels;
    }

    /**
     * Calculates river level with downhill constraint and minimum level 0.
     * Used as fallback for per-segment calculation (e.g., when called from rawLevelAt function).
     * The main river level calculation now uses calculateRouteLevels() for two-pass approach.
     */
    @Override
    public int calculateSegmentLevel(Integer gridALandLevel, Integer gridALandOffset,
                                      Integer gridBLandLevel, Integer gridBLandOffset,
                                      Integer previousLevel, Integer fixedLevel) {
        // Use parent calculation (raw level without river-specific constraints)
        return super.calculateSegmentLevel(gridALandLevel, gridALandOffset,
            gridBLandLevel, gridBLandOffset, previousLevel, fixedLevel);
    }
}
