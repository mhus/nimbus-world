package de.mhus.nimbus.world.shared.session;

/**
 * Target types for session commands broadcast via Redis.
 */
public enum SessionCommandTarget {
    /** All authenticated sessions on all pods */
    ALL,
    /** All sessions with a specific team (target = teamId) */
    TEAM,
    /** A specific player (target = playerId) */
    PLAYER,
    /** All sessions in a specific world (target = worldId) */
    WORLD
}
