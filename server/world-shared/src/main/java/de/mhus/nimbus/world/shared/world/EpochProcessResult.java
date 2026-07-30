package de.mhus.nimbus.world.shared.world;

/**
 * Neutral result type for epoch operations executed by owner services.
 * Lives in world-shared so owner services do not depend on world-control types.
 */
public record EpochProcessResult(String typeName, boolean success, String message, long timestamp) {
}
