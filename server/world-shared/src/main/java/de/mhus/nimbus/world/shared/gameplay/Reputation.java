package de.mhus.nimbus.world.shared.gameplay;

import lombok.Getter;

import java.util.Map;

/**
 * Defines a reputation type with its metadata and level boundaries.
 * Instances are immutable constants defined in {@link AdventureReputations}.
 */
@Getter
public class Reputation {

    /** Technical reputation ID, e.g. "renowned" */
    private final String name;

    /** Display title, e.g. "Berühmt" */
    private final String title;

    /** Short description of what this reputation represents */
    private final String description;

    /** Group for UI display, e.g. "social", "combat" */
    private final String group;

    /** Starting value for new characters */
    private final int start;

    /** Minimum possible value */
    private final int min;

    /** Maximum possible value */
    private final int max;

    private Reputation(String name, String title, String description, String group, int start, int min, int max) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.group = group;
        this.start = start;
        this.min = min;
        this.max = max;
    }

    /**
     * Create a new Reputation definition.
     *
     * @param name        Technical reputation ID (e.g. "renowned")
     * @param title       Display name
     * @param description Short description
     * @param group       Group for UI display
     * @param start       Starting value for new characters
     * @param min         Minimum value
     * @param max         Maximum value
     * @return Reputation instance
     */
    public static Reputation of(String name, String title, String description, String group, int start, int min, int max) {
        return new Reputation(name, title, description, group, start, min, max);
    }

    /**
     * Get the current value of this reputation from a reputation map.
     * Returns {@link #start} if not present in the map.
     *
     * @param reputations Map of reputation name to value (may be null)
     * @return Current reputation value, clamped to [min, max]
     */
    public int getValue(Map<String, Integer> reputations) {
        if (reputations == null) return start;
        Integer value = reputations.get(name);
        if (value == null) return start;
        return Math.clamp(value, min, max);
    }

    @Override
    public String toString() {
        return name + "[" + start + "/" + min + "-" + max + "]";
    }
}
