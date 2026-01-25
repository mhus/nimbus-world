package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for snapping points to specific locations relative to biomes/features.
 * Used by Point features to define precise placement rules.
 *
 * Example use cases:
 * - Place a city inside a plains biome, away from rivers
 * - Place a lighthouse on the coast, preferring locations near ocean
 * - Place Mount Doom inside Mordor, avoiding the surrounding mountains
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnapConfig {

    /**
     * Snap mode defining how the point should be positioned.
     * INSIDE: Place within the target feature's boundaries
     * EDGE: Place at the edge/border of the target feature
     * OUTSIDE_NEAR: Place outside but near the target feature
     */
    private SnapMode mode;

    /**
     * Target feature name to snap to.
     * The point will be positioned relative to this feature according to the snap mode.
     */
    private String target;

    /**
     * List of feature names to avoid.
     * The point will not be placed on or near these features.
     * Example: Avoid rivers, avoid mountains, avoid other cities
     */
    private List<String> avoid;

    /**
     * List of feature names to prefer proximity to.
     * If multiple valid positions exist, prefer locations closer to these features.
     * Example: Prefer locations near roads, prefer locations near water
     */
    private List<String> preferNear;

    /**
     * Minimum distance from avoided features (in hexagons).
     * Default: 1 hexagon
     */
    private Integer minDistanceFromAvoid;

    /**
     * Maximum distance from preferNear features (in hexagons).
     * Points beyond this distance from preferNear features are less preferred.
     * Default: 5 hexagons
     */
    private Integer maxDistanceFromPrefer;

    public int getEffectiveMinDistanceFromAvoid() {
        return minDistanceFromAvoid != null ? minDistanceFromAvoid : 1;
    }

    public int getEffectiveMaxDistanceFromPrefer() {
        return maxDistanceFromPrefer != null ? maxDistanceFromPrefer : 5;
    }
}
