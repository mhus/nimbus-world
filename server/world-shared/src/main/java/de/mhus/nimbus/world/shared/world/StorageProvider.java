package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.WorldId;

import java.util.List;

/**
 * If you use StorageService to store data implement this interface to provide
 * a way to find all storage ids for a world.
 */
public interface StorageProvider {

    List<String> findDistinctStorageIds(WorldId worldId);

}
