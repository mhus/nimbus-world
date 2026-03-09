package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;

/**
 * Defines who is allowed to join a world instance.
 *
 * PRIVATE: Only the creator can play. Players list is ignored.
 * TEAM: Only players explicitly listed in the players list (and the creator) can join.
 * PUBLIC: Anyone can join, players list is ignored.
 */
@GenerateTypeScript("entities")
public enum InstanceAccessType {
    PRIVATE,
    TEAM,
    PUBLIC
}
