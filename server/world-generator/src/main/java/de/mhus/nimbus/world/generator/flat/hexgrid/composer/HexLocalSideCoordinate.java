package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a position on the side/edge of a hex grid.
 * Used for points that should be placed at the boundary between hexes.
 *
 * @deprecated Use de.mhus.nimbus.world.shared.world.HexLocalEdgeVector instead.
 *             This composer-specific version is being phased out in favor of the shared version.
 *             Note: HexLocalEdgeVector only stores the edge position (side, numerator, denominator).
 *             Grid coordinate and biome should be stored separately in Point.PointComposed.
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HexLocalSideCoordinate {
    /**
     * Hex coordinate where this side position is located.
     */
    private HexVector2 coordinate;

    /**
     * Which side of the hex this position is on.
     */
    private WHexGrid.EDGE side;

    /**
     * Optional: Distance along the side (0.0 = start of side, 1.0 = end of side).
     */
    private Double offset;

    /**
     * Name of the biome this position is in.
     */
    private String biome;
}
