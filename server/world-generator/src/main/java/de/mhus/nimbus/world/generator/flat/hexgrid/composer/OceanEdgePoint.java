package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Special point type that must be placed at the edge of an ocean biome.
 * The composing logic ensures this point is positioned at the ocean boundary.
 *
 * Example uses:
 * - Harbor points at ocean edges
 * - Beach landing points
 * - Coastal fortress locations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OceanEdgePoint extends Point {

    /**
     * Preferred direction towards ocean (optional).
     * If set, tries to find ocean edge in this direction.
     */
    private Direction oceanDirection;

    /**
     * Composes the position for this ocean edge point.
     * Finds the nearest ocean edge from the biome.
     *
     * @param biome The biome this point belongs to
     * @param context The compose context with all biomes, points, and maps
     * @return Composed HexLocalPosition (shared type) at ocean edge
     */
    public de.mhus.nimbus.world.shared.world.HexLocalPosition composePosition(Area biome, ComposeContext context) {
        // TODO: Implement full ocean edge finding logic:
        // 1. Get biome's assigned coordinates
        // 2. For each coordinate, check neighbors
        // 3. Find coordinates that border ocean biomes
        // 4. Select best match based on oceanDirection if set
        // 5. Return HexLocalPosition at ocean edge

        // For now: Simple implementation - place at biome center
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
