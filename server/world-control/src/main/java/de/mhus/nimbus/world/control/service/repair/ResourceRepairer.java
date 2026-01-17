package de.mhus.nimbus.world.control.service.repair;

import de.mhus.nimbus.shared.types.WorldId;

/**
 * Interface for type-specific resource repair implementations.
 * Each implementation handles one type of resource (asset, backdrop, blocktype, model, ground).
 * Repairs duplicate entries and orphaned storage references.
 */
public interface ResourceRepairer {

    /**
     * Type identifier.
     *
     * @return Type title: "asset", "backdrop", "blocktype", "model", or "ground"
     */
    String name();

    /**
     * Repair resources in database for given world.
     * Finds and fixes:
     * - Duplicate entries (e.g., with and without _schema field)
     * - Orphaned storage references
     * - Invalid or corrupted entries
     *
     * @param worldId World to repair
     * @return Repair result with details
     */
    ResourceRepairService.ProcessResult repair(WorldId worldId);

}
