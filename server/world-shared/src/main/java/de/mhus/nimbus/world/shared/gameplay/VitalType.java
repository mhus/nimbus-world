package de.mhus.nimbus.world.shared.gameplay;

import java.util.Set;

/**
 * Vital types that can be remotely modified via VitalDelta messages.
 * Only these vitals are allowed to be manipulated across entity boundaries.
 */
public enum VitalType {
    HEALTH,
    MANA,
    STAMINA;

    private static final Set<String> REMOTE_MODIFIABLE_PREFIXES = Set.of("health", "mana", "stamina", "dot");

    /**
     * Derive VitalType from a stat identifier.
     * Maps stat groups and DoT types to the affected vital.
     *
     * @param stat Stat identifier, e.g. "health.regen", "dot.physical", "mana.current"
     * @return The matching VitalType, or null if not mapped
     */
    public static VitalType fromStat(String stat) {
        if (stat == null || stat.isEmpty()) return null;

        String group = stat.contains(".") ? stat.substring(0, stat.indexOf('.')) : stat;

        return switch (group) {
            case "health" -> HEALTH;
            case "mana" -> MANA;
            case "stamina" -> STAMINA;
            case "dot" -> HEALTH; // DoT effects always target health
            default -> null;
        };
    }

    /**
     * Check if a stat identifier targets a remotely modifiable vital.
     *
     * @param stat Stat identifier, e.g. "health.regen", "physical.damage"
     * @return true if this stat can be sent as a remote VitalDelta
     */
    public static boolean isRemoteModifiable(String stat) {
        if (stat == null || stat.isEmpty()) return false;
        String group = stat.contains(".") ? stat.substring(0, stat.indexOf('.')) : stat;
        return REMOTE_MODIFIABLE_PREFIXES.contains(group);
    }

    /**
     * Get the vital name as used in AdventureData (lowercase).
     *
     * @return lowercase vital name
     */
    public String vitalName() {
        return name().toLowerCase();
    }
}
