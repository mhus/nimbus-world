package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;

/**
 * Defines how instances of a world are managed and who can join them.
 *
 * NONE: World does not support instances. Players play directly on the base world.
 * PUBLIC: Anyone can join an instance (up to maxPlayersPerInstance).
 * TEAM: Players can join if they are creator, in the players list, or invited via a team.
 * PRIVATE: Only the creator and explicitly listed players can join.
 */
@GenerateTypeScript("entities")
public enum WorldInstanceType {
    NONE,
    PUBLIC,
    TEAM,
    PRIVATE
}
