package de.mhus.nimbus.world.player.gameplay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a combat stat (physical.damage, physical.defense, magical.damage, etc.).
 * Follows the same buff model as VitalValue: effective = (base + buffFlat) * (1.0 + buffPercent)
 *
 * <p>Transient fields are recalculated each tick from active effects.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatStat {

    /** Stat type identifier: "physical.damage", "physical.accuracy", "attackSpeed", etc. */
    private String type;

    /** Base value (from character/level) */
    private double base;

    // --- Transient fields, recalculated each tick ---

    /** Sum of all additive buffs */
    private transient double buffFlat;

    /** Sum of all percent buffs (0.5 = +50%) */
    private transient double buffPercent;

    /** Calculated: (base + buffFlat) * (1.0 + buffPercent) */
    private transient double effective;

    /**
     * Reset transient buff accumulators before recalculation.
     */
    public void resetBuffs() {
        buffFlat = 0;
        buffPercent = 0;
    }

    /**
     * Recalculate effective value from accumulated buffs.
     */
    public void recalculate() {
        effective = (base + buffFlat) * (1.0 + buffPercent);
        if (effective < 0) effective = 0;
    }

    /**
     * Create a standard combat stat.
     */
    public static CombatStat of(String type, double base) {
        var s = CombatStat.builder()
                .type(type)
                .base(base)
                .build();
        s.resetBuffs();
        s.recalculate();
        return s;
    }

    /**
     * Serialize to a Map for persistence.
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "type", type,
                "base", base
        );
    }

    /**
     * Deserialize from a Map.
     */
    public static CombatStat fromMap(Map<String, Object> map) {
        var s = CombatStat.builder()
                .type(stringVal(map.get("type"), "unknown"))
                .base(doubleVal(map.get("base"), 0))
                .build();
        s.resetBuffs();
        s.recalculate();
        return s;
    }

    private static String stringVal(Object v, String def) {
        return v instanceof String s ? s : def;
    }

    private static double doubleVal(Object v, double def) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s2) {
            try { return Double.parseDouble(s2); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }
}
