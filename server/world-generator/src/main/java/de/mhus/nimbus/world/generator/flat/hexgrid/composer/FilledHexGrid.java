package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a filled hex grid (either biome or filler)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilledHexGrid {
    /**
     * The hex grid coordinate
     */
    private HexVector2 coordinate;

    /**
     * The WHexGrid instance
     */
    private WHexGrid hexGrid;

    /**
     * Type of fill (null if part of a biome)
     */
    private FillerType fillerType;

    /**
     * True if this is a filler grid, false if it's a biome grid
     */
    private boolean isFiller;

    /**
     * Reference to the biome if this is part of a biome
     */
    private PlacedBiome biome;
}
