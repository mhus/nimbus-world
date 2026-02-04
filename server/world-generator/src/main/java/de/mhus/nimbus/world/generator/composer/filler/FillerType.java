package de.mhus.nimbus.world.generator.composer.filler;

/**
 * Type of filler for empty hex grids
 */
public enum FillerType {
    /**
     * Ocean/water filler
     */
    OCEAN,

    /**
     * Land filler (plains/grass)
     */
    LAND,

    /**
     * Coast - transition between land and ocean
     */
    COAST
}
