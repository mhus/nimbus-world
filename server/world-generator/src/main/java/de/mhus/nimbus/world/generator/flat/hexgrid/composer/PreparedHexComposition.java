package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;

import java.util.List;

/**
 * Prepared version of HexComposition with all abstract values converted to concrete ranges.
 * This is the working model for the composition algorithm.
 */
@Data
public class PreparedHexComposition {
    private HexComposition original;
    private List<PreparedBiome> biomes;
    private List<PreparedVillage> villages;
}
