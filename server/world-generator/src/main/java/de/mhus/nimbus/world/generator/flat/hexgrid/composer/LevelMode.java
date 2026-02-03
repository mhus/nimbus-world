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
     * Adjust level with half offset - adapts to terrain height of biomes.
     * Uses the 'meanLevelOffset' parameter.
     * Level is calculated as: meanHeight + offset/2
     * where meanHeight = (gridA.meanHeight + gridB.meanHeight) / 2
     * and meanHeight = landLevel + landOffset/2
     */
    ADJUST_MEAN,

    /**
     * Adjust to minimum level - adapts to terrain without offset.
     * Level is calculated as: meanHeight (no offset applied)
     * where meanHeight = (gridA.meanHeight + gridB.meanHeight) / 2
     * and meanHeight = landLevel + landOffset/2
     */
    ADJUST_MINIMUM,

    /**
     * Adjust to maximum level - adapts to terrain with full offset.
     * Uses the 'meanLevelOffset' parameter.
     * Level is calculated as: meanHeight + offset
     * where meanHeight = (gridA.meanHeight + gridB.meanHeight) / 2
     * and meanHeight = landLevel + landOffset/2
     */
    ADJUST_MAXIMUM,

}
