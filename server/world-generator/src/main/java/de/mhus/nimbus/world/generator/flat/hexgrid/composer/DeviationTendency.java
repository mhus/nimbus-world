package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

/**
 * Direction deviation tendency for LINE-shaped areas.
 * Controls how much a line tends to deviate from straight direction.
 */
public enum DeviationTendency {
    /**
     * No deviation - perfectly straight lines
     */
    NONE(0.0),

    /**
     * Slight deviation - subtle curves (20% chance per step)
     */
    SLIGHT(0.2),

    /**
     * Moderate deviation - natural curves (40% chance per step)
     */
    MODERATE(0.4),

    /**
     * Strong deviation - pronounced curves (60% chance per step)
     */
    STRONG(0.6);

    private final double probability;

    DeviationTendency(double probability) {
        this.probability = probability;
    }

    public double getProbability() {
        return probability;
    }

    /**
     * Gets the total deviation probability from left and right tendencies.
     * Used for backward compatibility with directionDeviation parameter.
     *
     * @param tendLeft Left tendency
     * @param tendRight Right tendency
     * @return Combined probability (0.0 - 1.0)
     */
    public static double getCombinedProbability(DeviationTendency tendLeft, DeviationTendency tendRight) {
        if (tendLeft == null && tendRight == null) {
            return 0.0;
        }
        double left = tendLeft != null ? tendLeft.getProbability() : 0.0;
        double right = tendRight != null ? tendRight.getProbability() : 0.0;
        return left + right;
    }
}
