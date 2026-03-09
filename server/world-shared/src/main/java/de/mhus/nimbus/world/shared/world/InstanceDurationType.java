package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;

/**
 * Defines the lifespan/duration type of a world instance.
 *
 * SHORT: Temporary instance, deleted when all players leave (current default behavior).
 * SEASONAL: Persistent for a defined season/period, survives player disconnects.
 * EVENT: Tied to a specific event, valid for the event duration.
 */
@GenerateTypeScript("entities")
public enum InstanceDurationType {
    SHORT,
    SEASONAL,
    EVENT
}
