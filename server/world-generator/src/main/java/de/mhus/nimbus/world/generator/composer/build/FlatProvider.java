package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.generator.WFlat;

import java.util.Collection;

/**
 * Provider interface for loading WFlat objects on demand.
 * Allows lazy loading of flat terrain data to avoid memory issues when processing large worlds.
 */
public interface FlatProvider {

    /**
     * Get the epoch this provider operates on.
     *
     * @return The epoch number
     */
    int getEpoch();

    /**
     * Get a flat for the given hex coordinate.
     *
     * @param coordinate The hex coordinate
     * @return The WFlat at that coordinate, or null if not found
     */
    WFlat getFlat(HexVector2 coordinate);

    /**
     * Get all available hex coordinates.
     *
     * @return Collection of all coordinates that have flats
     */
    Collection<HexVector2> getCoordinates();

    /**
     * Check if a flat exists at the given coordinate.
     *
     * @param coordinate The hex coordinate
     * @return true if a flat exists at that coordinate
     */
    default boolean hasFlat(HexVector2 coordinate) {
        return getFlat(coordinate) != null;
    }
}
