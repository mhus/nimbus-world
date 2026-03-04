package de.mhus.nimbus.world.player.gameplay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an active effect (buff/debuff) applied to a player.
 * Effects modify vitals or combat stats using the unified stat naming scheme.
 *
 * <p>String format: "stat:value[:duration[:probability]]"</p>
 * <p>DoT format: "damage:interval:duration:probability[:type]" (parsed separately)</p>
 *
 * <p>Stat naming scheme:</p>
 * <ul>
 *   <li>Vitals: health.max, health.regen, health.current, hunger.max, hunger.regen, etc.</li>
 *   <li>Combat: physical.damage, physical.accuracy, physical.defense, physical.evasion, etc.</li>
 *   <li>Percent modifiers: health.maxPercent, physical.damagePercent, etc.</li>
 *   <li>Simple stats: attackSpeed, critChance, critMultiplier</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveEffect {

    /** Unique effect ID */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** Source identifier: "item:sword_01", "env:campfire", "skill:strength", "consumable:health_potion" */
    private String source;

    /** Target stat identifier: "health.regen", "physical.damage", etc. */
    private String stat;

    /** Effect value (positive = buff, negative = debuff) */
    private double value;

    /** Remaining duration in seconds (0 = permanent until source removed) */
    private double duration;

    /** Original max duration for UI display */
    private double maxDuration;

    /** Tick probability (0.0-1.0), checked each time the effect ticks */
    @Builder.Default
    private double probability = 1.0;

    /** For DoT/periodic effects: seconds between ticks (0 = apply every game tick) */
    private double tickInterval;

    /** Timer tracking time since last periodic tick */
    private double tickTimer;

    /** Whether this effect stacks with identical stat+source effects */
    private boolean stackable;

    /** Current number of stacks */
    @Builder.Default
    private int stacks = 1;

    /** Target entity ID for remote effects. null = self (local effect). */
    private String targetEntityId;

    /**
     * Checks if this is a remote effect (targets another entity).
     */
    public boolean isRemote() {
        return targetEntityId != null;
    }

    /**
     * Checks if this effect has expired.
     */
    public boolean isExpired() {
        return maxDuration > 0 && duration <= 0;
    }

    /**
     * Checks if this is a permanent effect (duration 0 with maxDuration 0).
     */
    public boolean isPermanent() {
        return maxDuration == 0;
    }

    /**
     * Checks if this is an instant effect (.current suffix).
     */
    public boolean isInstant() {
        return stat != null && stat.endsWith(".current");
    }

    /**
     * Checks if this is a periodic/DoT effect (has tick interval).
     */
    public boolean isPeriodic() {
        return tickInterval > 0;
    }

    /**
     * Extracts the vital type from the stat identifier.
     * E.g., "health.regen" -> "health", "physical.damage" -> "physical"
     */
    public String getStatGroup() {
        if (stat == null) return "";
        int dot = stat.indexOf('.');
        return dot >= 0 ? stat.substring(0, dot) : stat;
    }

    /**
     * Extracts the modifier type from the stat identifier.
     * E.g., "health.regen" -> "regen", "physical.damage" -> "damage"
     */
    public String getModifierType() {
        if (stat == null) return "";
        int dot = stat.indexOf('.');
        return dot >= 0 ? stat.substring(dot + 1) : "";
    }

    /**
     * Parse an effect definition string into an ActiveEffect.
     * Format: "stat:value[:duration[:probability]]"
     *
     * @param definition Effect string, e.g., "health.regen:10:30:0.5"
     * @param source     Source identifier, e.g., "item:sword_01"
     * @return Parsed ActiveEffect
     */
    public static ActiveEffect parse(String definition, String source) {
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("Effect definition must not be blank");
        }

        String[] parts = definition.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Effect definition must have at least stat:value, got: " + definition);
        }

        String stat = parts[0].trim();
        double value = Double.parseDouble(parts[1].trim());
        double duration = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 0;
        double probability = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : 1.0;

        return ActiveEffect.builder()
                .source(source)
                .stat(stat)
                .value(value)
                .duration(duration)
                .maxDuration(duration)
                .probability(probability)
                .build();
    }

    /**
     * Parse a DoT definition string into an ActiveEffect.
     * Format: "damage:interval:duration:probability[:type]"
     * The resulting effect targets "health.regen" (negative = damage).
     *
     * @param definition DoT string, e.g., "100:5:30:0.5:physical"
     * @param source     Source identifier
     * @return Parsed ActiveEffect configured as periodic damage
     */
    public static ActiveEffect parseDot(String definition, String source) {
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("DoT definition must not be blank");
        }

        String[] parts = definition.split(":");
        if (parts.length < 4) {
            throw new IllegalArgumentException("DoT definition must have damage:interval:duration:probability, got: " + definition);
        }

        double damage = Double.parseDouble(parts[0].trim());
        double interval = Double.parseDouble(parts[1].trim());
        double duration = Double.parseDouble(parts[2].trim());
        double probability = Double.parseDouble(parts[3].trim());
        String type = parts.length > 4 ? parts[4].trim() : "physical";

        return ActiveEffect.builder()
                .source(source)
                .stat("dot." + type)
                .value(-damage)
                .duration(duration)
                .maxDuration(duration)
                .probability(probability)
                .tickInterval(interval)
                .tickTimer(0)
                .build();
    }

    /**
     * Parse a list of effect definition strings.
     *
     * @param definitions List of effect strings
     * @param source      Source identifier
     * @return List of parsed ActiveEffects
     */
    public static List<ActiveEffect> parseAll(List<String> definitions, String source) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<ActiveEffect> effects = new ArrayList<>(definitions.size());
        for (String def : definitions) {
            effects.add(parse(def, source));
        }
        return effects;
    }

    /**
     * Serialize this effect to a Map for persistence.
     */
    public Map<String, Object> toMap() {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("source", source);
        map.put("stat", stat);
        map.put("value", value);
        map.put("duration", duration);
        map.put("maxDuration", maxDuration);
        map.put("probability", probability);
        map.put("tickInterval", tickInterval);
        map.put("tickTimer", tickTimer);
        map.put("stackable", stackable);
        map.put("stacks", stacks);
        if (targetEntityId != null) {
            map.put("targetEntityId", targetEntityId);
        }
        return map;
    }

    /**
     * Deserialize an ActiveEffect from a Map.
     */
    @SuppressWarnings("unchecked")
    public static ActiveEffect fromMap(Map<String, Object> map) {
        return ActiveEffect.builder()
                .id(stringVal(map.get("id"), UUID.randomUUID().toString()))
                .source(stringVal(map.get("source"), ""))
                .stat(stringVal(map.get("stat"), ""))
                .value(doubleVal(map.get("value"), 0))
                .duration(doubleVal(map.get("duration"), 0))
                .maxDuration(doubleVal(map.get("maxDuration"), 0))
                .probability(doubleVal(map.get("probability"), 1.0))
                .tickInterval(doubleVal(map.get("tickInterval"), 0))
                .tickTimer(doubleVal(map.get("tickTimer"), 0))
                .stackable(boolVal(map.get("stackable"), false))
                .stacks(intVal(map.get("stacks"), 1))
                .targetEntityId(stringVal(map.get("targetEntityId"), null))
                .build();
    }

    private static String stringVal(Object v, String def) {
        return v instanceof String s ? s : def;
    }

    private static double doubleVal(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private static boolean boolVal(Object v, boolean def) {
        if (v instanceof Boolean b) return b;
        return def;
    }

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        return def;
    }
}
