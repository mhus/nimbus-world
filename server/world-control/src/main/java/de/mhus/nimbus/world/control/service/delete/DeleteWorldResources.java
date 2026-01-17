package de.mhus.nimbus.world.control.service.delete;

import java.util.List;

/**
 * Interface for services that delete world resources.
 *
 * Implementations handle the deletion of specific entity types (assets, block types, layers, etc.)
 * for a given world.
 *
 * This is typically used after a world has been deleted to clean up all associated data.
 */
public interface DeleteWorldResources {

    /**
     * Returns the name of this deletion service.
     * Used for logging and identification purposes.
     *
     * @return The service name (e.g., "assets", "blockTypes", "layers")
     */
    String name();

    /**
     * Deletes all resources associated with the given world.
     *
     * This method should:
     * - Find all entities associated with the worldId
     * - Delete them from the database
     * - Handle any dependent data (e.g., storage references)
     *
     * @param worldId The world ID whose resources should be deleted
     * @throws Exception if deletion fails
     */
    void deleteWorldResources(String worldId) throws Exception;

    List<String> getKnownWorldIds() throws Exception;

}
