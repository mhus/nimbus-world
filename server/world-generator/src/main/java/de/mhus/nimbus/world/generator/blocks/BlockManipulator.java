package de.mhus.nimbus.world.generator.blocks;

/**
 * Interface for block generators and manipulators.
 * Implementations provide specific block manipulation algorithms like terrain generation,
 * structure placement, or block modifications.
 */
public interface BlockManipulator {

    /**
     * Get the technical name of this manipulator.
     * Used for registration and lookup.
     *
     * @return technical name (e.g., "perlin-terrain", "flat-generator")
     */
    String getName();

    /**
     * Get the display title of this manipulator.
     * Used in UI and logs.
     *
     * @return human-readable title (e.g., "Perlin Noise Terrain Generator")
     */
    String getTitle();

    /**
     * Get the description and usage information.
     * Should explain what this manipulator does and what parameters it expects.
     *
     * @return description text with usage information
     */
    String getDescription();

    /**
     * Execute the block manipulation.
     * The context provides all necessary information like world, region, parameters, etc.
     *
     * @param context manipulation context with all required data
     * @return result of the manipulation including status and statistics
     * @throws BlockManipulatorException if manipulation fails
     */
    ManipulatorResult execute(ManipulatorContext context) throws BlockManipulatorException;
}
