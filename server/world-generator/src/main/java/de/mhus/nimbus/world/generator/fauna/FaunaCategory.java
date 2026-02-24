package de.mhus.nimbus.world.generator.fauna;

/**
 * Categorizes fauna based on water conditions at a given position.
 * <ul>
 *   <li>LAND - terrestrial animals (cows, sheep) - above water</li>
 *   <li>WATER - freshwater animals (frogs, fish) - underwater, waterLevel > seaLevel</li>
 *   <li>SEA - marine animals (dolphins, sharks) - underwater, waterLevel <= seaLevel</li>
 *   <li>AERIAL - flying animals (birds, bats) - determined by flag, not position</li>
 * </ul>
 */
public enum FaunaCategory {
    LAND,
    WATER,
    SEA,
    AERIAL;

    /**
     * Determine the fauna category for a position based on ground, water, and sea levels.
     * Note: AERIAL is not determined by position but by animal definition flags.
     *
     * @param groundLevel the ground level at this position
     * @param waterLevel  the water level at this position (equal to groundLevel if no water)
     * @param seaLevel    the global sea level, or null if not defined
     * @return the appropriate fauna category
     */
    public static FaunaCategory determine(int groundLevel, int waterLevel, Integer seaLevel) {
        if (waterLevel <= groundLevel) {
            return LAND;
        }
        if (seaLevel != null && waterLevel <= seaLevel) {
            return SEA;
        }
        return WATER;
    }
}
