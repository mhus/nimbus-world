package de.mhus.nimbus.world.control.service.duplicate;

/**
 * Interface for services that duplicate world data from a source world to a target world.
 *
 * Implementations handle the duplication of specific entity types (assets, block types, layers, etc.)
 * from one world to another.
 *
 * The target world must already exist before duplication begins.
 */
public interface DuplicateToWorld {

    /**
     * Returns the name of this duplication service.
     * Used for logging and identification purposes.
     *
     * @return The service name (e.g., "assets", "blockTypes", "layers")
     */
    String name();

    /**
     * Duplicates data from the source world to the target world.
     *
     * This method should:
     * - Load all relevant entities from the source world
     * - Create copies with the target worldId
     * - Save the copies to the database
     * - Handle any dependent data (e.g., storage references)
     *
     * The target world must already exist.
     *
     * @param sourceWorldId The world ID to copy from
     * @param targetWorldId The world ID to copy to (must already exist)
     * @throws Exception if duplication fails
     */
    void duplicate(String sourceWorldId, String targetWorldId) throws Exception;
}
