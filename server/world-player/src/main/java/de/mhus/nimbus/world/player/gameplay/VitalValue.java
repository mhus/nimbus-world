package de.mhus.nimbus.world.player.gameplay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a single vital value (health, hunger, thirst, stamina, mana, adrenaline).
 * Follows the unified buff model: effectiveMax = (base + buffFlat) * (1.0 + buffPercent)
 *
 * <p>Transient fields (buffFlat, buffPercent, effectiveMax, effectiveRegenRate) are
 * recalculated each tick from active effects and not persisted.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalValue {

    /** Vital type identifier: "health", "hunger", "thirst", "stamina", "mana", "adrenaline" */
    private String type;

    /** Base maximum value (from character/level) */
    private double base;

    /** Current value (0 to effectiveMax) */
    private double current;

    /** Base regeneration/degeneration rate per second (positive = regen, negative = degen) */
    private double baseRegenRate;

    // --- Transient fields, recalculated each tick ---

    /** Sum of all additive buffs on max value */
    private transient double buffFlat;

    /** Sum of all percent buffs on max value (0.5 = +50%) */
    private transient double buffPercent;

    /** Calculated: (base + buffFlat) * (1.0 + buffPercent) */
    private transient double effectiveMax;

    /** Calculated: baseRegenRate + sum of regen buffs */
    private transient double effectiveRegenRate;

    /** Display color for UI (hex) */
    private String color;

    /** Display name */
    private String displayName;

    /** Display order in UI (lower = first) */
    private int order;

    /** Only send to client when percentage drops below this threshold (0.0 - 1.0). 0 = always send. */
    private double sendThreshold;

    /** Options flags as compact string. Each character is a flag: 'p' = pinned (don't auto-hide when full). */
    private String options;

    /**
     * Reset transient buff accumulators before recalculation.
     */
    public void resetBuffs() {
        buffFlat = 0;
        buffPercent = 0;
        effectiveRegenRate = baseRegenRate;
    }

    /**
     * Recalculate effective values from accumulated buffs.
     */
    public void recalculate() {
        effectiveMax = (base + buffFlat) * (1.0 + buffPercent);
        if (effectiveMax < 1) effectiveMax = 1;
    }

    /**
     * Apply regen/degen for the given delta time and clamp.
     *
     * @param deltaSeconds Time elapsed since last tick
     */
    public void applyRegen(double deltaSeconds) {
        current += effectiveRegenRate * deltaSeconds;
        clamp();
    }

    /**
     * Clamp current value between 0 and effectiveMax.
     */
    public void clamp() {
        if (current < 0) current = 0;
        if (current > effectiveMax) current = effectiveMax;
    }

    /**
     * Check if this vital is depleted (current <= 0).
     */
    public boolean isDepleted() {
        return current <= 0;
    }

    /**
     * Check if this vital is full (current >= effectiveMax).
     */
    public boolean isFull() {
        return current >= effectiveMax;
    }

    /**
     * Get current as percentage (0.0 - 1.0).
     */
    public double getPercentage() {
        return effectiveMax > 0 ? current / effectiveMax : 0;
    }

    /**
     * Create a standard vital value with defaults.
     */
    public static VitalValue of(String type, double base, double baseRegenRate, String color, String displayName, int order) {
        return of(type, base, baseRegenRate, color, displayName, order, 0);
    }

    /**
     * Create a standard vital value with send threshold.
     * @param sendThreshold Only send to client when percentage drops below this (0.0 = always send)
     */
    public static VitalValue of(String type, double base, double baseRegenRate, String color, String displayName, int order, double sendThreshold) {
        var v = VitalValue.builder()
                .type(type)
                .base(base)
                .current(base)
                .baseRegenRate(baseRegenRate)
                .color(color)
                .displayName(displayName)
                .order(order)
                .sendThreshold(sendThreshold)
                .build();
        v.resetBuffs();
        v.recalculate();
        return v;
    }

    /**
     * Serialize to a Map for persistence.
     */
    public Map<String, Object> toMap() {
        var map = new java.util.HashMap<String, Object>();
        map.put("type", type);
        map.put("base", base);
        map.put("current", current);
        map.put("baseRegenRate", baseRegenRate);
        map.put("color", color != null ? color : "");
        map.put("displayName", displayName != null ? displayName : "");
        map.put("order", order);
        if (options != null) {
            map.put("options", options);
        }
        return map;
    }

    /**
     * Deserialize from a Map.
     */
    public static VitalValue fromMap(Map<String, Object> map) {
        var v = VitalValue.builder()
                .type(stringVal(map.get("type"), "unknown"))
                .base(doubleVal(map.get("base"), 100))
                .current(doubleVal(map.get("current"), 100))
                .baseRegenRate(doubleVal(map.get("baseRegenRate"), 0))
                .color(stringVal(map.get("color"), "#FFFFFF"))
                .displayName(stringVal(map.get("displayName"), ""))
                .order(intVal(map.get("order"), 0))
                .options(stringVal(map.get("options"), null))
                .build();
        v.resetBuffs();
        v.recalculate();
        return v;
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

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        return def;
    }
}
