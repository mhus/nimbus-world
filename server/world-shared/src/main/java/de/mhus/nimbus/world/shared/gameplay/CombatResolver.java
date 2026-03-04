package de.mhus.nimbus.world.shared.gameplay;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stateless utility class for combat damage resolution.
 *
 * Damage formula (6 steps):
 * 1. Physical Hit Check:  random < physicalAccuracy * (1 - defenderEvasion)
 * 2. Magical Hit Check:   random < magicalAccuracy * (1 - defenderMagicalEvasion)
 * 3. Crit Check:          random < critChance → damage *= critMultiplier
 * 4. Physical Mitigation: physDmg = max(0, physicalDamage - defenderPhysicalDefense)
 * 5. Magical Mitigation:  magDmg = max(0, magicalDamage - defenderMagicalDefense)
 * 6. Total:               totalDamage = -(physDmg + magDmg)
 *
 * Returns negative value (damage on health) or 0 (complete miss).
 */
public final class CombatResolver {

    private CombatResolver() {}

    /**
     * Resolve an attack against a defender.
     *
     * @param physDmg        Attacker's physical raw damage
     * @param physAcc        Attacker's physical hit chance (0-1)
     * @param magDmg         Attacker's magical raw damage
     * @param magAcc         Attacker's magical hit chance (0-1)
     * @param critChance     Attacker's critical hit chance (0-1)
     * @param critMult       Attacker's critical multiplier (e.g. 1.5)
     * @param defPhysDef     Defender's physical defense (flat reduction)
     * @param defPhysEvasion Defender's physical evasion chance (0-1)
     * @param defMagDef      Defender's magical defense (flat reduction)
     * @param defMagEvasion  Defender's magical evasion chance (0-1)
     * @return Negative value = damage to health, 0 = complete miss
     */
    public static double resolve(
            double physDmg, double physAcc, double magDmg, double magAcc,
            double critChance, double critMult,
            double defPhysDef, double defPhysEvasion,
            double defMagDef, double defMagEvasion) {

        var rng = ThreadLocalRandom.current();

        // 1. Physical hit check
        boolean physHit = physDmg > 0 && rng.nextDouble() < physAcc * (1.0 - defPhysEvasion);

        // 2. Magical hit check
        boolean magHit = magDmg > 0 && rng.nextDouble() < magAcc * (1.0 - defMagEvasion);

        if (!physHit && !magHit) {
            return 0; // Complete miss
        }

        // 3. Crit check (applies to all damage that hit)
        boolean crit = rng.nextDouble() < critChance;
        double critFactor = crit ? critMult : 1.0;

        // 4. Physical mitigation
        double resolvedPhys = 0;
        if (physHit) {
            resolvedPhys = Math.max(0, physDmg * critFactor - defPhysDef);
        }

        // 5. Magical mitigation
        double resolvedMag = 0;
        if (magHit) {
            resolvedMag = Math.max(0, magDmg * critFactor - defMagDef);
        }

        // 6. Total damage (negative = damage on health)
        double total = resolvedPhys + resolvedMag;
        return total > 0 ? -total : 0;
    }
}
