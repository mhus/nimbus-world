package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;

import java.util.Map;

/**
 * Interface for WFlat terrain manipulators.
 * Manipulators modify a flat terrain in a specific region.
 * <p>
 * Implementations should be Spring @Component beans.
 * They will be automatically discovered by FlatManipulatorService.
 */
public interface FlatManipulator {

    /**
     * Get the unique name of this manipulator.
     * This name is used to reference the manipulator in jobs and API calls.
     *
     * @return Manipulator name (e.g., "raise", "lower", "flatten", "smooth")
     */
    String getName();

    /**
     * Execute the manipulation on a WFlat region.
     *
     * @param flat The WFlat to manipulate (will be modified in place)
     * @param x X coordinate of the region start (relative to flat, 0-based)
     * @param z Z coordinate of the region start (relative to flat, 0-based)
     * @param sizeX Width of the region to manipulate
     * @param sizeZ Height of the region to manipulate
     * @param parameters Manipulator-specific parameters (e.g., height, strength, pattern)
     * @throws IllegalArgumentException if parameters are invalid or region is out of bounds
     */
    void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ, Map<String, String> parameters);
}
