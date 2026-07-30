package de.mhus.nimbus.world.shared.world;

/**
 * Redis message payload for the "epoch.switch" channel.
 * Serialized to {@code {"epoch": <int>}} and consumed by world-life and
 * world-player epoch-switch listeners.
 *
 * @param epoch the new epoch value of the affected world instance
 */
public record EpochSwitchMessage(int epoch) {
}
