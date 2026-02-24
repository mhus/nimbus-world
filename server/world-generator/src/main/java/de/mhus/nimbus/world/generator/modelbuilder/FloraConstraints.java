package de.mhus.nimbus.world.generator.modelbuilder;

import de.mhus.nimbus.world.generator.flora.FloraCategory;

import java.util.OptionalInt;

/**
 * Constraints for flora placement resolved from model metadata.
 *
 * @param maxHeight maximum height of the model in blocks (plant too tall for shallow water)
 * @param minWater  minimum water depth required (plant needs at least N blocks of water)
 * @param maxWater  maximum water depth allowed (plant can't handle deeper than N blocks)
 * @param land      plant can grow on LAND positions
 * @param water     plant can grow in WATER (freshwater) positions
 * @param sea       plant can grow in SEA (saltwater) positions
 * @param emerse    plant can grow above water surface (relaxes maxHeight check)
 */
public record FloraConstraints(
        OptionalInt maxHeight,
        OptionalInt minWater,
        OptionalInt maxWater,
        boolean land,
        boolean water,
        boolean sea,
        boolean emerse
) {

    public static final FloraConstraints UNCONSTRAINED =
            new FloraConstraints(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                    true, true, true, false);

    /**
     * Check whether a plant fits at a given position.
     *
     * @param waterDepth available water depth (waterLevel - groundLevel)
     * @param category   the flora category at this position
     * @return true if the plant is allowed at this position
     */
    public boolean fitsPosition(int waterDepth, FloraCategory category) {
        // Check category flags
        switch (category) {
            case LAND -> { if (!land) return false; }
            case WATER -> { if (!water) return false; }
            case SEA -> { if (!sea) return false; }
        }

        // For LAND, no water constraints apply
        if (category == FloraCategory.LAND) return true;

        // maxHeight check - skip if plant is emerse (allowed to grow above water)
        if (!emerse && maxHeight.isPresent() && maxHeight.getAsInt() > waterDepth) return false;

        // Water depth range check
        if (minWater.isPresent() && waterDepth < minWater.getAsInt()) return false;
        if (maxWater.isPresent() && waterDepth > maxWater.getAsInt()) return false;

        return true;
    }
}
