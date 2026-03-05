package de.mhus.nimbus.world.shared.gameplay;

/**
 * Combat strategy for NPC entities when attacked by a player.
 *
 * Configured via WEntity.server property "combat_strategy".
 */
public enum CombatStrategy {

    /** Entity flees from attacker (e.g., deer) */
    FLEE,

    /** Entity attacks once then flees (e.g., cow) */
    ATTACK_FLEE,

    /** Entity attacks repeatedly until target is dead or out of range (e.g., wolf) */
    ATTACK_REPEAT;

    /** Default combat duration in milliseconds (how long entity stays in combat mode) */
    public static final long DEFAULT_COMBAT_DURATION_MS = 15_000;

    /**
     * Parse from string, case-insensitive. Returns FLEE as default.
     */
    public static CombatStrategy fromString(String value) {
        if (value == null || value.isBlank()) return FLEE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FLEE;
        }
    }
}
