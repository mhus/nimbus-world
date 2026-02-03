package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Defines how a Flow's level (height) is calculated relative to terrain.
 */
public enum LevelMode {
    /**
     * Fixed level - stays constant regardless of terrain height.
     * Uses the 'level' parameter.
     */
    FIXED,

    /**
     * Adjust level - adapts to terrain height of biomes.
     * Uses the 'meanLevelOffset' parameter.
     * Level is calculated as: (gridA.meanHeight + gridB.meanHeight) / 2 + meanLevelOffset
     * where meanHeight = landLevel + landOffset/2
     */
    ADJUST
}
