package de.mhus.nimbus.world.generator.flora;

/**
 * Categorizes flora based on water conditions at a given position.
 * <ul>
 *   <li>LAND - terrestrial plants (trees, bushes, grass) - above water</li>
 *   <li>WATER - freshwater plants (seaweed, water lilies) - underwater, waterLevel > seaLevel</li>
 *   <li>SEA - marine plants (kelp, coral) - underwater, waterLevel <= seaLevel</li>
 * </ul>
 */
public enum FloraCategory {
    LAND,
    WATER,
    SEA;

    /**
     * Determine the flora category for a position based on ground, water, and sea levels.
     *
     * @param groundLevel the ground level at this position
     * @param waterLevel  the water level at this position (-1 if no water)
     * @param seaLevel    the global sea level, or null if not defined
     * @return the appropriate flora category
     */
    public static FloraCategory determine(int groundLevel, int waterLevel, Integer seaLevel) {
        if (waterLevel <= groundLevel) {
            return LAND;
        }
        if (seaLevel != null && waterLevel <= seaLevel) {
            return SEA;
        }
        return WATER;
    }
}
