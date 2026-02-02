package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Regular point with standard positioning logic.
 * This is the default point type for most use cases like:
 * - City centers
 * - Villages
 * - Landmarks
 * - Quest markers
 * - Spawn points
 *
 * Positioned using biome-relative coordinates:
 * - direction + biomeDistance (e.g., NE + FAR)
 * - biomeSide + sideOffset (e.g., NORTH_EAST side at 0.5 offset)
 * - relativeToPoints (relative to other points)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PositionPoint extends Point {

    /**
     * Composes the position for this regular point.
     * Uses biome-relative positioning logic from the Point base class.
     *
     * @param biome The biome this point belongs to
     * @param context The compose context with all biomes, points, and maps
     * @return Composed HexLocalPosition (shared type) within the biome's center grid
     */
    public de.mhus.nimbus.world.shared.world.HexLocalPosition composePosition(Area biome, ComposeContext context) {
        // For now: Simple implementation - place at biome center
        // TODO: Implement full positioning logic:
        // 1. If biomeSide + sideOffset is set, position on biome side
        // 2. Else if direction + biomeDistance is set, position relative to center
        // 3. Else if relativeToPoints is set, position relative to other points

        // Default: Place at center of biome (0,0 in hex coordinates, divider 4)
        de.mhus.nimbus.generated.types.HexVector2 hexPosition =
            de.mhus.nimbus.generated.types.HexVector2.builder()
                .q(0)
                .r(0)
                .build();

        int divider = de.mhus.nimbus.world.shared.util.HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;

        return new de.mhus.nimbus.world.shared.world.HexLocalPosition(hexPosition, divider, size);
    }
}
