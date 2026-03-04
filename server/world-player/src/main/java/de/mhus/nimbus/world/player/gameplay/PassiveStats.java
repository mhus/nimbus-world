package de.mhus.nimbus.world.player.gameplay;

import lombok.Data;

/**
 * Passive stats derived from worn equipment and skills.
 * Recalculated whenever inventory or skills change, NOT every tick.
 * These values are applied as permanent buffs during tick processing.
 *
 * <p>All values are final computed totals (equipment + skill contributions combined).
 * They are added to the buff accumulators in EffectProcessor alongside active effects.</p>
 */
@Data
public class PassiveStats {

    // --- Vital buffs (additive) ---
    private double healthMax;
    private double healthRegen;
    private double staminaMax;
    private double staminaRegen;
    private double manaMax;
    private double manaRegen;

    // --- Combat stats (additive flat) ---
    private double physicalDamage;
    private double physicalAccuracy;
    private double physicalDefense;
    private double physicalEvasion;
    private double magicalDamage;
    private double magicalAccuracy;
    private double magicalDefense;
    private double magicalEvasion;
    private double attackSpeed;
    private double critChance;
    private double critMultiplier;

    // --- Combat stats (percent) ---
    private double physicalDamagePercent;
    private double physicalDefensePercent;
    private double magicalDamagePercent;
    private double magicalDefensePercent;

    // --- Vital buffs (percent) ---
    private double healthMaxPercent;
    private double staminaMaxPercent;
    private double manaMaxPercent;

    /**
     * Reset all passive stats to zero.
     */
    public void reset() {
        healthMax = 0;
        healthRegen = 0;
        staminaMax = 0;
        staminaRegen = 0;
        manaMax = 0;
        manaRegen = 0;
        physicalDamage = 0;
        physicalAccuracy = 0;
        physicalDefense = 0;
        physicalEvasion = 0;
        magicalDamage = 0;
        magicalAccuracy = 0;
        magicalDefense = 0;
        magicalEvasion = 0;
        attackSpeed = 0;
        critChance = 0;
        critMultiplier = 0;
        physicalDamagePercent = 0;
        physicalDefensePercent = 0;
        magicalDamagePercent = 0;
        magicalDefensePercent = 0;
        healthMaxPercent = 0;
        staminaMaxPercent = 0;
        manaMaxPercent = 0;
    }

    /**
     * Add an effect definition to the passive stats.
     * Parses stat identifier and modifier type, adds value to the appropriate field.
     *
     * @param stat  Stat identifier, e.g. "physical.defense", "health.max", "health.maxPercent"
     * @param value Effect value (positive = buff, negative = debuff)
     */
    public void addEffect(String stat, double value) {
        if (stat == null || stat.isEmpty()) return;

        switch (stat) {
            // Vital flat
            case "health.max" -> healthMax += value;
            case "health.regen" -> healthRegen += value;
            case "stamina.max" -> staminaMax += value;
            case "stamina.regen" -> staminaRegen += value;
            case "mana.max" -> manaMax += value;
            case "mana.regen" -> manaRegen += value;
            // Vital percent
            case "health.maxPercent" -> healthMaxPercent += value;
            case "stamina.maxPercent" -> staminaMaxPercent += value;
            case "mana.maxPercent" -> manaMaxPercent += value;
            // Combat flat
            case "physical.damage" -> physicalDamage += value;
            case "physical.accuracy" -> physicalAccuracy += value;
            case "physical.defense" -> physicalDefense += value;
            case "physical.evasion" -> physicalEvasion += value;
            case "magical.damage" -> magicalDamage += value;
            case "magical.accuracy" -> magicalAccuracy += value;
            case "magical.defense" -> magicalDefense += value;
            case "magical.evasion" -> magicalEvasion += value;
            case "attackSpeed" -> attackSpeed += value;
            case "critChance" -> critChance += value;
            case "critMultiplier" -> critMultiplier += value;
            // Combat percent
            case "physical.damagePercent" -> physicalDamagePercent += value;
            case "physical.defensePercent" -> physicalDefensePercent += value;
            case "magical.damagePercent" -> magicalDamagePercent += value;
            case "magical.defensePercent" -> magicalDefensePercent += value;
            default -> { /* unknown stat, ignore */ }
        }
    }

    /**
     * Apply all passive stats as buffs to vitals and combat stats.
     * Called during tick processing after resetBuffs() and before active effects.
     */
    public void applyTo(AdventureData data) {
        // Vital buffs
        applyVitalBuff(data, "health", healthMax, healthMaxPercent, healthRegen);
        applyVitalBuff(data, "stamina", staminaMax, staminaMaxPercent, staminaRegen);
        applyVitalBuff(data, "mana", manaMax, manaMaxPercent, manaRegen);

        // Combat stat buffs (flat)
        applyCombatBuff(data, "physical.damage", physicalDamage, physicalDamagePercent);
        applyCombatBuff(data, "physical.accuracy", physicalAccuracy, 0);
        applyCombatBuff(data, "physical.defense", physicalDefense, physicalDefensePercent);
        applyCombatBuff(data, "physical.evasion", physicalEvasion, 0);
        applyCombatBuff(data, "magical.damage", magicalDamage, magicalDamagePercent);
        applyCombatBuff(data, "magical.accuracy", magicalAccuracy, 0);
        applyCombatBuff(data, "magical.defense", magicalDefense, magicalDefensePercent);
        applyCombatBuff(data, "magical.evasion", magicalEvasion, 0);
        applyCombatBuff(data, "attackSpeed", attackSpeed, 0);
        applyCombatBuff(data, "critChance", critChance, 0);
        applyCombatBuff(data, "critMultiplier", critMultiplier, 0);
    }

    private void applyVitalBuff(AdventureData data, String type, double flat, double percent, double regen) {
        VitalValue vital = data.getVital(type);
        if (vital == null) return;
        vital.setBuffFlat(vital.getBuffFlat() + flat);
        vital.setBuffPercent(vital.getBuffPercent() + percent);
        vital.setEffectiveRegenRate(vital.getEffectiveRegenRate() + regen);
    }

    private void applyCombatBuff(AdventureData data, String type, double flat, double percent) {
        CombatStat stat = data.getCombatStat(type);
        if (stat == null) return;
        stat.setBuffFlat(stat.getBuffFlat() + flat);
        stat.setBuffPercent(stat.getBuffPercent() + percent);
    }
}
