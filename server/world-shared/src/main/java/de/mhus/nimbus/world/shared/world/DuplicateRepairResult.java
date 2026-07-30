package de.mhus.nimbus.world.shared.world;

/**
 * Neutral result of a duplicate repair run performed by an owner service.
 * <p>
 * Lives in world-shared so owner services (data ownership) can return it
 * without depending on world-control types. Adapters in world-control map it
 * to their own contract.
 *
 * @param typeName  logical resource type name (matches the repairer's name())
 * @param success   whether the repair completed successfully
 * @param message   human readable summary (reports found/removed counts)
 * @param timestamp completion timestamp in epoch milliseconds
 * @param found     number of duplicate documents found (excluding the kept one)
 * @param removed   number of duplicate documents actually removed
 */
public record DuplicateRepairResult(
        String typeName,
        boolean success,
        String message,
        long timestamp,
        int found,
        int removed
) {
}
