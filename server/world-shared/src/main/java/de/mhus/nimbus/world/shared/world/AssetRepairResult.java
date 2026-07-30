package de.mhus.nimbus.world.shared.world;

/**
 * Neutral result of an asset repair run performed by {@code SAssetService}.
 * <p>
 * The asset repair reports more counters than a plain duplicate repair
 * (it additionally sweeps orphaned storage), hence its own neutral record.
 * Lives in world-shared so the owner service can return it without depending
 * on world-control types.
 *
 * @param typeName               logical resource type name (matches the repairer's name())
 * @param success                whether the repair completed successfully
 * @param message                human readable summary (reports all counts)
 * @param timestamp              completion timestamp in epoch milliseconds
 * @param duplicatesFound        number of duplicate documents found (excluding the kept one)
 * @param duplicatesRemoved      number of duplicate documents actually removed
 * @param orphanedStorageFound   number of orphaned storage references found
 * @param orphanedStorageRemoved number of orphaned storage references actually removed
 */
public record AssetRepairResult(
        String typeName,
        boolean success,
        String message,
        long timestamp,
        int duplicatesFound,
        int duplicatesRemoved,
        int orphanedStorageFound,
        int orphanedStorageRemoved
) {
}
