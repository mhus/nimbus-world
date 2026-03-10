package de.mhus.nimbus.world.control.service.epoch;

import de.mhus.nimbus.world.shared.world.WEpochMeta;

import java.util.List;

/**
 * Interface for epoch operations on a specific resource type.
 * Each implementation handles one type of epoch-aware entity
 * (WChunk, WLayer, WEntity, WItemPosition, WHexGrid).
 */
public interface ResourceEpochType {

    /**
     * Resource type name.
     */
    String name();

    /**
     * Validate epoch consistency for a world.
     * Checks:
     * - No epoch values that aren't defined in WWorld.epoches
     * - No documents with empty epoches arrays
     * - Warns about epochs not used by any document
     *
     * @param worldId    Base world ID
     * @param epochMetas Epoch definitions from WWorld
     * @return Validation result
     */
    ResourceEpochService.ProcessResult validate(String worldId, List<WEpochMeta> epochMetas);

    /**
     * Create (propagate) a new epoch by copying from the source epoch.
     * Adds the new epoch to all documents that contain the source epoch.
     *
     * @param worldId     Base world ID
     * @param sourceEpoch Epoch to copy from (typically the previous highest epoch)
     * @param newEpoch    New epoch number to add
     * @return Result with count of updated documents
     */
    ResourceEpochService.ProcessResult create(String worldId, int sourceEpoch, int newEpoch);

    /**
     * Delete an epoch by removing it from all documents' epoches arrays.
     *
     * @param worldId Base world ID
     * @param epoch   Epoch number to remove
     * @return Result with count of updated documents
     */
    ResourceEpochService.ProcessResult delete(String worldId, int epoch);
}
