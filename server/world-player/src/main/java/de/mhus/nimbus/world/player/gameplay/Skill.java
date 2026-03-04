package de.mhus.nimbus.world.player.gameplay;

import lombok.Getter;

import java.util.Map;

/**
 * Defines a skill type with its metadata and level boundaries.
 * Instances are immutable constants defined in {@link AdventureSkills}.
 */
@Getter
public class Skill {

    /** Technical skill ID, e.g. "combat.melee" */
    private final String name;

    /** Display title, e.g. "Nahkampf" */
    private final String title;

    /** Short description of what the skill does */
    private final String description;

    /** Starting level for new characters */
    private final int start;

    /** Minimum possible level */
    private final int min;

    /** Maximum possible level */
    private final int max;

    private Skill(String name, String title, String description, int start, int min, int max) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.start = start;
        this.min = min;
        this.max = max;
    }

    /**
     * Create a new Skill definition.
     *
     * @param name        Technical skill ID (e.g. "combat.melee")
     * @param title       Display name
     * @param description Short description
     * @param start       Starting level for new characters
     * @param min         Minimum level
     * @param max         Maximum level
     * @return Skill instance
     */
    public static Skill of(String name, String title, String description, int start, int min, int max) {
        return new Skill(name, title, description, start, min, max);
    }

    /**
     * Get the current level of this skill from a skills map.
     * Returns {@link #start} if the skill is not present in the map.
     *
     * @param skills Map of skill name to level (may be null)
     * @return Current skill level, clamped to [min, max]
     */
    public int getValue(Map<String, Integer> skills) {
        if (skills == null) return start;
        Integer level = skills.get(name);
        if (level == null) return start;
        return Math.clamp(level, min, max);
    }

    /**
     * Apply this skill as a multiplicative factor to a base value.
     * The skill level is interpreted as percentage points: level / 100.0
     * <ul>
     *   <li>Level 0 → factor 0.0 → result = 0</li>
     *   <li>Level 100 → factor 1.0 → result = baseValue (unchanged)</li>
     *   <li>Level 200 → factor 2.0 → result = baseValue * 2</li>
     * </ul>
     *
     * @param skills    Map of skill name to level (may be null)
     * @param baseValue The base value to multiply
     * @return baseValue * (skillLevel / 100.0)
     */
    public double applyMultiplicative(Map<String, Integer> skills, double baseValue) {
        return baseValue * getValue(skills) / 100.0;
    }

    @Override
    public String toString() {
        return name + "[" + start + "/" + min + "-" + max + "]";
    }
}
