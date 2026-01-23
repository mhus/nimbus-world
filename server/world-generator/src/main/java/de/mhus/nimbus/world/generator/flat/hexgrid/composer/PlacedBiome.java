package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a biome that has been placed on the grid
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacedBiome {
    /**
     * The prepared biome definition
     */
    private PreparedBiome biome;

    /**
     * All HexGrid coordinates occupied by this biome
     */
    private List<HexVector2> coordinates;

    /**
     * Center coordinate of the biome
     */
    private HexVector2 center;

    /**
     * Actual size used (number of hexes)
     */
    private int actualSize;
}
