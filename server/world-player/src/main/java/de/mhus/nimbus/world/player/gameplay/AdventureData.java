package de.mhus.nimbus.world.player.gameplay;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adventure gameplay state container.
 * Holds all vital values, combat stats, and active effects for a player session.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AdventureData extends GameplayData {

    /** All vital values keyed by type (health, hunger, thirst, stamina, mana, adrenaline) */
    private Map<String, VitalValue> vitals = new LinkedHashMap<>();

    /** All combat stats keyed by type (physical.damage, physical.defense, etc.) */
    private Map<String, CombatStat> combatStats = new LinkedHashMap<>();

    /** Active effects (buffs, debuffs, DoTs) */
    private List<ActiveEffect> activeEffects = new ArrayList<>();

    /** Timestamp of last tick for delta calculation */
    private long lastTickTimestamp;

    /** Seconds since last combat action (for adrenaline decay) */
    private double combatIdleTimer;

    /**
     * Initialize with default vital values and combat stats.
     */
    public void initDefaults() {
        vitals.put("health",    VitalValue.of("health",    100, 0.5,   "#FF4444", "Health",    0));
        vitals.put("hunger",    VitalValue.of("hunger",    100, -0.1,  "#CC8800", "Hunger",    1));
        vitals.put("thirst",    VitalValue.of("thirst",    100, -0.15, "#4488FF", "Thirst",    2));
        vitals.put("stamina",   VitalValue.of("stamina",   100, 2.0,   "#44CC44", "Stamina",   3));
        vitals.put("mana",      VitalValue.of("mana",      100, 1.0,   "#AA44FF", "Mana",      4));
        vitals.put("adrenaline",VitalValue.of("adrenaline",100, 0,     "#FF8800", "Adrenaline",5));

        combatStats.put("physical.damage",      CombatStat.of("physical.damage",      5));
        combatStats.put("physical.accuracy",    CombatStat.of("physical.accuracy",    0.7));
        combatStats.put("physical.defense",     CombatStat.of("physical.defense",     0));
        combatStats.put("physical.evasion",     CombatStat.of("physical.evasion",     0.1));
        combatStats.put("magical.damage",       CombatStat.of("magical.damage",       0));
        combatStats.put("magical.accuracy",     CombatStat.of("magical.accuracy",     0.7));
        combatStats.put("magical.defense",      CombatStat.of("magical.defense",      0));
        combatStats.put("magical.evasion",      CombatStat.of("magical.evasion",      0.05));
        combatStats.put("attackSpeed",          CombatStat.of("attackSpeed",          1.0));
        combatStats.put("critChance",           CombatStat.of("critChance",           0.05));
        combatStats.put("critMultiplier",       CombatStat.of("critMultiplier",       1.5));

        lastTickTimestamp = System.currentTimeMillis();
    }

    /**
     * Get a vital value by type, or null if not found.
     */
    public VitalValue getVital(String type) {
        return vitals.get(type);
    }

    /**
     * Get a combat stat by type, or null if not found.
     */
    public CombatStat getCombatStat(String type) {
        return combatStats.get(type);
    }

    /**
     * Add an active effect. Instant effects (.current) are applied immediately.
     */
    public void addEffect(ActiveEffect effect) {
        if (effect.isInstant()) {
            applyInstantEffect(effect);
        } else {
            activeEffects.add(effect);
        }
    }

    /**
     * Remove all effects from a specific source.
     */
    public void removeEffectsBySource(String source) {
        activeEffects.removeIf(e -> source.equals(e.getSource()));
    }

    /**
     * Apply an instant effect (e.g., "health.current:50" = heal 50 HP immediately).
     */
    private void applyInstantEffect(ActiveEffect effect) {
        String vitalType = effect.getStatGroup();
        VitalValue vital = vitals.get(vitalType);
        if (vital != null) {
            vital.setCurrent(vital.getCurrent() + effect.getValue() * effect.getStacks());
            vital.clamp();
        }
    }

    // --- Convenience accessors for backward compatibility ---

    public double getHealth() { return getVitalCurrent("health"); }
    public double getHunger() { return getVitalCurrent("hunger"); }
    public double getThirst() { return getVitalCurrent("thirst"); }
    public double getStamina() { return getVitalCurrent("stamina"); }
    public double getMana() { return getVitalCurrent("mana"); }
    public double getAdrenaline() { return getVitalCurrent("adrenaline"); }

    public double getMaxHealth() { return getVitalEffectiveMax("health"); }
    public double getMaxHunger() { return getVitalEffectiveMax("hunger"); }
    public double getMaxThirst() { return getVitalEffectiveMax("thirst"); }
    public double getMaxStamina() { return getVitalEffectiveMax("stamina"); }
    public double getMaxMana() { return getVitalEffectiveMax("mana"); }

    private double getVitalCurrent(String type) {
        var v = vitals.get(type);
        return v != null ? v.getCurrent() : 0;
    }

    private double getVitalEffectiveMax(String type) {
        var v = vitals.get(type);
        return v != null ? v.getEffectiveMax() : 0;
    }
}
