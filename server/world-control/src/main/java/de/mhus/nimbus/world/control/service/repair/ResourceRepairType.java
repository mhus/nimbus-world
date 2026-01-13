package de.mhus.nimbus.world.control.service.repair;

import de.mhus.nimbus.shared.types.WorldId;

/**
 * Interface for type-specific resource repair implementations.
 * Each implementation handles one type of resource (asset, backdrop, blocktype, model, ground).
 * Repairs duplicate entries and orphaned storage references.
 */
public interface ResourceRepairType {

    /**
     * Type identifier.
     *
     * @return Type name: "asset", "backdrop", "blocktype", "model", or "ground"
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
     * @param dryRun  If true, only report issues without fixing them
     * @return Repair result with details
     */
    RepairResult repair(WorldId worldId, boolean dryRun);

    /**
     * Result of repair operation for a single type.
     */
    record RepairResult(
            int duplicatesFound,
            int duplicatesRemoved,
            int orphanedStorageFound,
            int orphanedStorageRemoved,
            int totalIssuesFound,
            int totalIssuesFixed
    ) {
        public static RepairResult of(int duplicatesFound, int duplicatesRemoved,
                                      int orphanedStorageFound, int orphanedStorageRemoved) {
            int totalFound = duplicatesFound + orphanedStorageFound;
            int totalFixed = duplicatesRemoved + orphanedStorageRemoved;
            return new RepairResult(duplicatesFound, duplicatesRemoved,
                    orphanedStorageFound, orphanedStorageRemoved,
                    totalFound, totalFixed);
        }

        public static RepairResult empty() {
            return new RepairResult(0, 0, 0, 0, 0, 0);
        }
    }
}
