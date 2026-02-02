package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Point that must be placed at the edge of a biome.
 * The composing logic ensures this point is positioned at a biome boundary.
 *
 * Example uses:
 * - Gate entrances at biome edges
 * - Border posts
 * - Entry/exit points for biomes
 * - Connection points between biomes
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EdgePoint extends Point {

    /**
     * Preferred edge direction (NORTH_EAST, EAST, SOUTH_EAST, SOUTH_WEST, WEST, NORTH_WEST).
     * If set, tries to find edge position in this direction from biome center.
     */
    private WHexGrid.EDGE preferredEdge;

    /**
     * Alternative: Preferred direction (N, NE, E, SE, S, SW, W, NW).
     * Converted to EDGE during composition if preferredEdge is not set.
     */
    private Direction preferredDirection;

    /**
     * ID of neighboring biome to connect to (optional).
     * If set, ensures the edge point borders this specific biome.
     */
    private String neighborBiomeId;

    /**
     * Composes the position for this edge point.
     * Finds a suitable position at the biome boundary.
     *
     * @param biome The biome this point belongs to
     * @param context Context for composing
     * @return Composed HexLocalEdgeVector (shared type) at biome edge
     */
    public de.mhus.nimbus.world.shared.world.HexLocalEdgeVector composePosition(Area biome, ComposeContext context) {
        // TODO: Implement full edge finding logic:
        // 1. Get biome's assigned coordinates
        // 2. For each coordinate, check neighbors
        // 3. Find coordinates that are at biome boundary
        // 4. If preferredEdge or preferredDirection is set, prefer that direction
        // 5. If neighborBiomeId is set, find edge bordering that biome
        // 6. Return HexLocalEdgeVector at biome edge

        // For now: Simple implementation - use preferredEdge or default to NORTH_EAST
        WHexGrid.EDGE edge = preferredEdge != null ? preferredEdge : WHexGrid.EDGE.NORTH_EAST;

        // Center position (2 out of 4) on the edge
        int numerator = 2;
        int denominator = de.mhus.nimbus.world.shared.util.HexLocalUtil.DEFAULT_SIDE_DIVIDER;

        return new de.mhus.nimbus.world.shared.world.HexLocalEdgeVector(edge, numerator, denominator);
    }
}
