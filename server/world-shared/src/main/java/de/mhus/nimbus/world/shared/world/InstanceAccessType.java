package de.mhus.nimbus.world.shared.world;

/**
 * Defines who is allowed to join a world instance.
 *
 * PRIVATE: Only the creator can play. Players list is ignored.
 * TEAM: Only players explicitly listed in the players list (and the creator) can join.
 * PUBLIC: Anyone can join, players list is ignored.
 */
public enum InstanceAccessType {
    PRIVATE,
    TEAM,
    PUBLIC
}
