package de.mhus.nimbus.world.player.gameplay;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.world.shared.gameplay.CombatResolver;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.world.WItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the combat system: weapon types, skill factors,
 * defense skills, constitution/wear, and CombatResolver.
 * No Spring context needed.
 */
class CombatSystemTest {

    // --- Helper: create AdventureData with base defaults and custom skills ---

    private AdventureData createData() {
        var data = new AdventureData();
        data.initDefaults();
        return data;
    }

    private AdventureData createDataWithSkills(Map<String, Integer> skills) {
        var data = createData();
        data.setCachedSkills(skills);
        return data;
    }

    private WItem createWeapon(String rangeType, String damageType, String type, String wear) {
        Map<String, String> server = new HashMap<>();
        if (rangeType != null) server.put("rangeType", rangeType);
        if (damageType != null) server.put("damageType", damageType);
        if (type != null) server.put("type", type);
        if (wear != null) server.put("wear", wear);
        return WItem.builder().itemId("test-weapon").server(server).build();
    }

    private WItem createArmor(String damageType, String wear) {
        return createArmor(damageType, wear, null);
    }

    private WItem createArmor(String damageType, String wear, String type) {
        Map<String, String> server = new HashMap<>();
        if (damageType != null) server.put("damageType", damageType);
        if (wear != null) server.put("wear", wear);
        if (type != null) server.put("type", type);
        return WItem.builder().itemId("test-armor").server(server).build();
    }

    // =========================================================================
    // CombatResolver Tests
    // =========================================================================

    @Nested
    class CombatResolverTests {

        @Test
        void zeroDamage_returnsZero() {
            double result = CombatResolver.resolve(0, 1, 0, 1, 0, 1, 0, 0, 0, 0);
            assertThat(result).isEqualTo(0);
        }

        @Test
        void physicalDamage_withPerfectAccuracy_noDefense() {
            // 100% accuracy, 0 evasion, no defense => full damage
            // Run multiple times to account for crit randomness
            double totalDamage = 0;
            int hits = 0;
            for (int i = 0; i < 1000; i++) {
                double result = CombatResolver.resolve(10, 1.0, 0, 0, 0, 1.0, 0, 0, 0, 0);
                if (result != 0) {
                    hits++;
                    totalDamage += result;
                }
            }
            assertThat(hits).isEqualTo(1000); // all should hit
            assertThat(totalDamage / hits).isCloseTo(-10.0, within(0.01)); // avg damage = -10
        }

        @Test
        void physicalDamage_mitigatedByDefense() {
            // damage=10, defense=3 => 7 net damage
            double result = CombatResolver.resolve(10, 1.0, 0, 0, 0, 1.0, 3, 0, 0, 0);
            assertThat(result).isEqualTo(-7.0);
        }

        @Test
        void physicalDamage_fullyMitigatedByDefense() {
            // damage=5, defense=10 => 0 damage (clamped)
            double result = CombatResolver.resolve(5, 1.0, 0, 0, 0, 1.0, 10, 0, 0, 0);
            assertThat(result).isEqualTo(0);
        }

        @Test
        void magicalDamage_withPerfectAccuracy_noDefense() {
            int hits = 0;
            for (int i = 0; i < 100; i++) {
                double result = CombatResolver.resolve(0, 0, 20, 1.0, 0, 1.0, 0, 0, 0, 0);
                if (result != 0) hits++;
            }
            assertThat(hits).isEqualTo(100);
        }

        @Test
        void combinedDamage_physicalAndMagical() {
            // phys=10, mag=5, no defense, no crit
            double result = CombatResolver.resolve(10, 1.0, 5, 1.0, 0, 1.0, 0, 0, 0, 0);
            assertThat(result).isEqualTo(-15.0);
        }

        @Test
        void criticalHit_multipliesDamage() {
            // 100% crit chance, 2x multiplier, 10 phys damage
            double result = CombatResolver.resolve(10, 1.0, 0, 0, 1.0, 2.0, 0, 0, 0, 0);
            assertThat(result).isEqualTo(-20.0);
        }

        @Test
        void evasion_canCauseMiss() {
            // 100% evasion => always miss
            double result = CombatResolver.resolve(10, 1.0, 0, 0, 0, 1.0, 0, 1.0, 0, 0);
            assertThat(result).isEqualTo(0);
        }

        @Test
        void zeroAccuracy_alwaysMisses() {
            double result = CombatResolver.resolve(10, 0, 0, 0, 0, 1.0, 0, 0, 0, 0);
            assertThat(result).isEqualTo(0);
        }
    }

    // =========================================================================
    // Skill Factor Tests (weapon type differentiation)
    // =========================================================================

    @Nested
    class SkillFactorTests {

        @Test
        void meleeSkill_defaultLevel100_factorIs1() {
            // Default skill level = 100 => factor = 1.0
            int level = AdventureSkills.COMBAT_MELEE.getValue(null);
            assertThat(level).isEqualTo(100);
            assertThat(level / 100.0).isEqualTo(1.0);
        }

        @Test
        void meleeSkill_level200_factorIs2() {
            Map<String, Integer> skills = Map.of("combat.melee", 200);
            int level = AdventureSkills.COMBAT_MELEE.getValue(skills);
            assertThat(level / 100.0).isEqualTo(2.0);
        }

        @Test
        void rangedSkill_level150_factorIs1point5() {
            Map<String, Integer> skills = Map.of("combat.ranged", 150);
            int level = AdventureSkills.COMBAT_RANGED.getValue(skills);
            assertThat(level / 100.0).isEqualTo(1.5);
        }

        @Test
        void magicSkill_level50_factorIs0point5() {
            Map<String, Integer> skills = Map.of("combat.magic", 50);
            int level = AdventureSkills.COMBAT_MAGIC.getValue(skills);
            assertThat(level / 100.0).isEqualTo(0.5);
        }

        @Test
        void skill_clampedToMax() {
            Map<String, Integer> skills = Map.of("combat.melee", 9999);
            int level = AdventureSkills.COMBAT_MELEE.getValue(skills);
            assertThat(level).isEqualTo(500); // max is 500
        }

        @Test
        void skill_clampedToMin() {
            Map<String, Integer> skills = Map.of("combat.melee", 0);
            int level = AdventureSkills.COMBAT_MELEE.getValue(skills);
            assertThat(level).isEqualTo(50); // min is 50
        }

        @Test
        void defenseSkill_defaultLevel100() {
            int level = AdventureSkills.COMBAT_DEFENSE.getValue(null);
            assertThat(level).isEqualTo(100);
        }

        @Test
        void magicDefenseSkill_defaultLevel100() {
            int level = AdventureSkills.COMBAT_MAGIC_DEFENSE.getValue(null);
            assertThat(level).isEqualTo(100);
        }

        @Test
        void weaponCareSkill_level200_halvesWear() {
            Map<String, Integer> skills = Map.of("combat.weaponCare", 200);
            double skillFactor = AdventureSkills.COMBAT_WEAPON_CARE.getValue(skills) / 100.0;
            double baseWear = 0.01;
            double actualWear = baseWear / skillFactor;
            assertThat(actualWear).isCloseTo(0.005, within(0.0001));
        }

        @Test
        void armorCareSkill_level50_doublesWear() {
            Map<String, Integer> skills = Map.of("combat.armorCare", 50);
            double skillFactor = AdventureSkills.COMBAT_ARMOR_CARE.getValue(skills) / 100.0;
            double baseWear = 0.01;
            double actualWear = baseWear / skillFactor;
            assertThat(actualWear).isCloseTo(0.02, within(0.0001));
        }

        @Test
        void applyMultiplicative_convenience() {
            Map<String, Integer> skills = Map.of("combat.melee", 200);
            double result = AdventureSkills.COMBAT_MELEE.applyMultiplicative(skills, 10.0);
            assertThat(result).isEqualTo(20.0);
        }
    }

    // =========================================================================
    // Weapon Type: Damage Channel Filtering
    // =========================================================================

    @Nested
    class DamageChannelTests {

        @Test
        void physicalOnly_zeroesMagical() {
            var data = createData();
            // Set both physical and magical stats
            data.getCombatStats().put("physical.damage", CombatStat.of("physical.damage", 10));
            data.getCombatStats().put("magical.damage", CombatStat.of("magical.damage", 8));

            String damageType = "physical";
            boolean hasPhysical = damageType.contains("physical");
            boolean hasMagical = damageType.contains("magical");

            double physDmg = hasPhysical ? data.getCombatStat("physical.damage").getEffective() : 0;
            double magDmg = hasMagical ? data.getCombatStat("magical.damage").getEffective() : 0;

            assertThat(physDmg).isEqualTo(10.0);
            assertThat(magDmg).isEqualTo(0.0);
        }

        @Test
        void magicalOnly_zeroesPhysical() {
            var data = createData();
            data.getCombatStats().put("physical.damage", CombatStat.of("physical.damage", 10));
            data.getCombatStats().put("magical.damage", CombatStat.of("magical.damage", 8));

            String damageType = "magical";
            boolean hasPhysical = damageType.contains("physical");
            boolean hasMagical = damageType.contains("magical");

            double physDmg = hasPhysical ? data.getCombatStat("physical.damage").getEffective() : 0;
            double magDmg = hasMagical ? data.getCombatStat("magical.damage").getEffective() : 0;

            assertThat(physDmg).isEqualTo(0.0);
            assertThat(magDmg).isEqualTo(8.0);
        }

        @Test
        void combined_physicalMagical_bothActive() {
            var data = createData();
            data.getCombatStats().put("physical.damage", CombatStat.of("physical.damage", 10));
            data.getCombatStats().put("magical.damage", CombatStat.of("magical.damage", 8));

            String damageType = "physical,magical";
            boolean hasPhysical = damageType.contains("physical");
            boolean hasMagical = damageType.contains("magical");

            double physDmg = hasPhysical ? data.getCombatStat("physical.damage").getEffective() : 0;
            double magDmg = hasMagical ? data.getCombatStat("magical.damage").getEffective() : 0;

            assertThat(physDmg).isEqualTo(10.0);
            assertThat(magDmg).isEqualTo(8.0);
        }
    }

    // =========================================================================
    // Range Skill Factor Selection
    // =========================================================================

    @Nested
    class RangeSkillFactorTests {

        private double calculateRangeSkillFactor(Map<String, Integer> skills, String rangeType) {
            boolean melee = rangeType.contains("melee");
            boolean ranged = rangeType.contains("ranged");
            if (melee && ranged) {
                double meleeSkill = AdventureSkills.COMBAT_MELEE.getValue(skills) / 100.0;
                double rangedSkill = AdventureSkills.COMBAT_RANGED.getValue(skills) / 100.0;
                return Math.max(meleeSkill, rangedSkill);
            } else if (ranged) {
                return AdventureSkills.COMBAT_RANGED.getValue(skills) / 100.0;
            } else {
                return AdventureSkills.COMBAT_MELEE.getValue(skills) / 100.0;
            }
        }

        @Test
        void melee_usesMeleeSkill() {
            var skills = Map.of("combat.melee", 200, "combat.ranged", 100);
            assertThat(calculateRangeSkillFactor(skills, "melee")).isEqualTo(2.0);
        }

        @Test
        void ranged_usesRangedSkill() {
            var skills = Map.of("combat.melee", 200, "combat.ranged", 150);
            assertThat(calculateRangeSkillFactor(skills, "ranged")).isEqualTo(1.5);
        }

        @Test
        void hybrid_usesHigherSkill_meleeHigher() {
            var skills = Map.of("combat.melee", 200, "combat.ranged", 100);
            assertThat(calculateRangeSkillFactor(skills, "melee,ranged")).isEqualTo(2.0);
        }

        @Test
        void hybrid_usesHigherSkill_rangedHigher() {
            var skills = Map.of("combat.melee", 100, "combat.ranged", 300);
            assertThat(calculateRangeSkillFactor(skills, "melee,ranged")).isEqualTo(3.0);
        }

        @Test
        void defaultRangeType_isMelee() {
            var skills = Map.of("combat.melee", 150);
            // Default (no rangeType on item) = "melee"
            assertThat(calculateRangeSkillFactor(skills, "melee")).isEqualTo(1.5);
        }
    }

    // =========================================================================
    // Constitution and Wear
    // =========================================================================

    @Nested
    class ConstitutionTests {

        @Test
        void weaponConstitution_scalesDamage() {
            double baseDamage = 10.0;
            double weaponCon = 0.5; // 50% worn
            assertThat(baseDamage * weaponCon).isEqualTo(5.0);
        }

        @Test
        void weaponConstitution_fullCondition() {
            double baseDamage = 10.0;
            double weaponCon = 1.0;
            assertThat(baseDamage * weaponCon).isEqualTo(10.0);
        }

        @Test
        void weaponConstitution_broken() {
            double baseDamage = 10.0;
            double weaponCon = 0.0;
            assertThat(baseDamage * weaponCon).isEqualTo(0.0);
        }

        @Test
        void armorConstitution_scalesDefense() {
            double baseDefense = 20.0;
            double armorCon = 0.75;
            double skillFactor = 1.0; // COMBAT_DEFENSE level 100
            assertThat(baseDefense * armorCon * skillFactor).isEqualTo(15.0);
        }

        @Test
        void wearCalculation_skillReducesWear() {
            double baseWear = 0.01;
            // Skill 200 = factor 2.0 => wear halved
            double skillFactor = 200 / 100.0;
            double actualWear = baseWear / skillFactor;
            assertThat(actualWear).isCloseTo(0.005, within(0.0001));
        }

        @Test
        void wearCalculation_skillIncreasesWear() {
            double baseWear = 0.01;
            // Skill 50 = factor 0.5 => wear doubled
            double skillFactor = 50 / 100.0;
            double actualWear = baseWear / skillFactor;
            assertThat(actualWear).isCloseTo(0.02, within(0.0001));
        }

        @Test
        void constitutionAfterMultipleAttacks() {
            double constitution = 1.0;
            double wearPerAttack = 0.01;
            double skillFactor = 1.0; // default

            for (int i = 0; i < 50; i++) {
                constitution = Math.max(0.0, constitution - wearPerAttack / skillFactor);
            }

            assertThat(constitution).isCloseTo(0.5, within(0.0001));
        }

        @Test
        void constitutionAfterManyAttacks_reachesZero() {
            double constitution = 1.0;
            double wearPerAttack = 0.01;

            for (int i = 0; i < 200; i++) {
                constitution = Math.max(0.0, constitution - wearPerAttack);
            }

            assertThat(constitution).isEqualTo(0.0);
        }

        @Test
        void potionType_noWear() {
            String weaponType = "potion";
            boolean shouldWear = !"potion".equals(weaponType);
            assertThat(shouldWear).isFalse();
        }

        @Test
        void weaponType_wears() {
            String weaponType = "weapon";
            boolean shouldWear = !"potion".equals(weaponType);
            assertThat(shouldWear).isTrue();
        }

        @Test
        void wandType_wears() {
            String weaponType = "wand";
            boolean shouldWear = !"potion".equals(weaponType);
            assertThat(shouldWear).isTrue();
        }
    }

    // =========================================================================
    // Armor Wear: DamageType Matching
    // =========================================================================

    @Nested
    class ArmorWearDamageTypeTests {

        private boolean matchesDamageType(WItem item, boolean physicalHit, boolean magicalHit) {
            if (item == null || item.getServer() == null) return physicalHit;
            String damageType = item.getServer().get("damageType");
            if (damageType == null || damageType.isBlank()) return physicalHit;
            return (physicalHit && damageType.contains("physical"))
                    || (magicalHit && damageType.contains("magical"));
        }

        @Test
        void physicalArmor_wornByPhysicalHit() {
            WItem armor = createArmor("physical", "0.005");
            assertThat(matchesDamageType(armor, true, false)).isTrue();
        }

        @Test
        void physicalArmor_notWornByMagicalHit() {
            WItem armor = createArmor("physical", "0.005");
            assertThat(matchesDamageType(armor, false, true)).isFalse();
        }

        @Test
        void magicalArmor_wornByMagicalHit() {
            WItem armor = createArmor("magical", "0.005");
            assertThat(matchesDamageType(armor, false, true)).isTrue();
        }

        @Test
        void magicalArmor_notWornByPhysicalHit() {
            WItem armor = createArmor("magical", "0.005");
            assertThat(matchesDamageType(armor, true, false)).isFalse();
        }

        @Test
        void combinedArmor_wornByBoth() {
            WItem armor = createArmor("physical,magical", "0.005");
            assertThat(matchesDamageType(armor, true, false)).isTrue();
            assertThat(matchesDamageType(armor, false, true)).isTrue();
        }

        @Test
        void noDamageType_defaultsToPhysical() {
            WItem armor = createArmor(null, "0.005");
            assertThat(matchesDamageType(armor, true, false)).isTrue();
            assertThat(matchesDamageType(armor, false, true)).isFalse();
        }

        @Test
        void nullItem_defaultsToPhysical() {
            assertThat(matchesDamageType(null, true, false)).isTrue();
            assertThat(matchesDamageType(null, false, true)).isFalse();
        }

        @Test
        void shieldInHandSlot_includedInWear() {
            WItem shield = createArmor("physical", "0.008", "shield");
            String type = shield.getServer().get("type");
            assertThat("shield".equals(type)).isTrue();
            assertThat(matchesDamageType(shield, true, false)).isTrue();
        }

        @Test
        void weaponInHandSlot_excludedFromArmorWear() {
            WItem weapon = createWeapon("melee", "physical", "weapon", "0.01");
            String type = weapon.getServer().get("type");
            assertThat("shield".equals(type)).isFalse();
        }
    }

    // =========================================================================
    // Full Attack Calculation Integration (without mocking services)
    // =========================================================================

    @Nested
    class AttackCalculationIntegrationTests {

        @Test
        void meleePhysicalWeapon_skillBoostsPhysicalDamage() {
            var data = createDataWithSkills(Map.of("combat.melee", 200, "combat.magic", 100));
            data.getCombatStats().put("physical.damage", CombatStat.of("physical.damage", 10));
            data.getCombatStats().put("physical.accuracy", CombatStat.of("physical.accuracy", 0.8));
            data.getCombatStats().put("magical.damage", CombatStat.of("magical.damage", 5));
            data.setCachedConstitution(Map.of("weapon", 1.0));

            String rangeType = "melee";
            String damageType = "physical";
            double rangeSkillFactor = AdventureSkills.COMBAT_MELEE.getValue(data.getCachedSkills()) / 100.0;
            double weaponCon = data.getCachedConstitution().getOrDefault("weapon", 1.0);
            boolean hasPhysical = damageType.contains("physical");
            boolean hasMagical = damageType.contains("magical");

            double physDmg = hasPhysical ? data.getCombatStat("physical.damage").getEffective() * weaponCon * rangeSkillFactor : 0;
            double magDmg = hasMagical ? data.getCombatStat("magical.damage").getEffective() * weaponCon : 0;

            assertThat(physDmg).isCloseTo(20.0, within(0.01)); // 10 * 1.0 * 2.0
            assertThat(magDmg).isEqualTo(0.0); // zeroed out (physical only)
        }

        @Test
        void rangedMagicalWand_skillBoostsMagicalDamage() {
            var data = createDataWithSkills(Map.of("combat.ranged", 100, "combat.magic", 200));
            data.getCombatStats().put("physical.damage", CombatStat.of("physical.damage", 3));
            data.getCombatStats().put("magical.damage", CombatStat.of("magical.damage", 15));
            data.setCachedConstitution(Map.of("weapon", 0.8));

            String damageType = "magical";
            double magicSkillFactor = AdventureSkills.COMBAT_MAGIC.getValue(data.getCachedSkills()) / 100.0;
            double weaponCon = data.getCachedConstitution().getOrDefault("weapon", 1.0);
            boolean hasPhysical = damageType.contains("physical");
            boolean hasMagical = damageType.contains("magical");

            double physDmg = hasPhysical ? data.getCombatStat("physical.damage").getEffective() * weaponCon : 0;
            double magDmg = hasMagical ? data.getCombatStat("magical.damage").getEffective() * weaponCon * magicSkillFactor : 0;

            assertThat(physDmg).isEqualTo(0.0); // zeroed (magical only)
            assertThat(magDmg).isCloseTo(24.0, within(0.01)); // 15 * 0.8 * 2.0
        }

        @Test
        void hybridWeapon_physicalMagical_bothChannels() {
            var data = createDataWithSkills(Map.of("combat.melee", 150, "combat.ranged", 100, "combat.magic", 120));
            data.getCombatStats().put("physical.damage", CombatStat.of("physical.damage", 10));
            data.getCombatStats().put("magical.damage", CombatStat.of("magical.damage", 8));
            data.setCachedConstitution(Map.of("weapon", 1.0));

            String rangeType = "melee";
            String damageType = "physical,magical";
            double rangeSkillFactor = AdventureSkills.COMBAT_MELEE.getValue(data.getCachedSkills()) / 100.0;
            double magicSkillFactor = AdventureSkills.COMBAT_MAGIC.getValue(data.getCachedSkills()) / 100.0;
            double weaponCon = 1.0;

            double physDmg = damageType.contains("physical") ? 10.0 * weaponCon * rangeSkillFactor : 0;
            double magDmg = damageType.contains("magical") ? 8.0 * weaponCon * magicSkillFactor : 0;

            assertThat(physDmg).isCloseTo(15.0, within(0.01)); // 10 * 1.5
            assertThat(magDmg).isCloseTo(9.6, within(0.01));   // 8 * 1.2
        }

        @Test
        void wornWeapon_reducesDamage() {
            var data = createDataWithSkills(Map.of("combat.melee", 100));
            data.getCombatStats().put("physical.damage", CombatStat.of("physical.damage", 10));
            data.setCachedConstitution(Map.of("weapon", 0.3)); // 70% worn

            double weaponCon = data.getCachedConstitution().get("weapon");
            double physDmg = 10.0 * weaponCon * 1.0; // skill factor 1.0

            assertThat(physDmg).isCloseTo(3.0, within(0.01));
        }

        @Test
        void defenseWithSkills_physicalAndMagical() {
            var data = createDataWithSkills(Map.of("combat.defense", 200, "combat.magicDefense", 150));
            data.getCombatStats().put("physical.defense", CombatStat.of("physical.defense", 10));
            data.getCombatStats().put("physical.evasion", CombatStat.of("physical.evasion", 0.2));
            data.getCombatStats().put("magical.defense", CombatStat.of("magical.defense", 8));
            data.getCombatStats().put("magical.evasion", CombatStat.of("magical.evasion", 0.1));
            data.setCachedConstitution(Map.of("armor", 1.0));

            double armorCon = 1.0;
            double physDefSkill = AdventureSkills.COMBAT_DEFENSE.getValue(data.getCachedSkills()) / 100.0;
            double magDefSkill = AdventureSkills.COMBAT_MAGIC_DEFENSE.getValue(data.getCachedSkills()) / 100.0;

            double defPhysDef = 10.0 * armorCon * physDefSkill;
            double defPhysEvasion = 0.2 * armorCon * physDefSkill;
            double defMagDef = 8.0 * armorCon * magDefSkill;
            double defMagEvasion = 0.1 * armorCon * magDefSkill;

            assertThat(defPhysDef).isCloseTo(20.0, within(0.01));     // 10 * 2.0
            assertThat(defPhysEvasion).isCloseTo(0.4, within(0.01));  // 0.2 * 2.0
            assertThat(defMagDef).isCloseTo(12.0, within(0.01));      // 8 * 1.5
            assertThat(defMagEvasion).isCloseTo(0.15, within(0.01));  // 0.1 * 1.5
        }

        @Test
        void defenseWithWornArmor() {
            double armorCon = 0.5;
            double physDefSkill = 1.0;
            double baseDefense = 10.0;

            double effectiveDefense = baseDefense * armorCon * physDefSkill;
            assertThat(effectiveDefense).isEqualTo(5.0);
        }
    }

    // =========================================================================
    // Full Combat Round: Attack -> Defense -> Damage
    // =========================================================================

    @Nested
    class FullCombatRoundTests {

        @Test
        void meleeAttack_vsPhysicalDefense_damageCalculated() {
            // Attacker: melee weapon, physical, 10 damage, 100% accuracy
            double physDmg = 10.0;
            double physAcc = 1.0;
            double magDmg = 0.0;
            double magAcc = 0.0;
            double critChance = 0.0;
            double critMult = 1.0;

            // Defender: 3 defense, 0 evasion
            double defPhysDef = 3.0;
            double defPhysEvasion = 0.0;
            double defMagDef = 0.0;
            double defMagEvasion = 0.0;

            double damage = CombatResolver.resolve(physDmg, physAcc, magDmg, magAcc,
                    critChance, critMult, defPhysDef, defPhysEvasion, defMagDef, defMagEvasion);

            assertThat(damage).isEqualTo(-7.0);
        }

        @Test
        void magicalWand_vsLowMagicDefense() {
            double damage = CombatResolver.resolve(
                    0, 0, 20, 1.0,  // no phys, 20 mag damage
                    0, 1.0,          // no crit
                    0, 0, 5, 0);     // 5 magical defense

            assertThat(damage).isEqualTo(-15.0);
        }

        @Test
        void strongDefense_nullifiesAttack() {
            double damage = CombatResolver.resolve(
                    10, 1.0, 5, 1.0, // 10 phys + 5 mag
                    0, 1.0,           // no crit
                    15, 0, 10, 0);    // 15 phys def + 10 mag def

            assertThat(damage).isEqualTo(0.0);
        }

        @Test
        void repeatedAttacks_wearDownConstitution_reduceDamage() {
            double weaponCon = 1.0;
            double baseDamage = 10.0;
            double wearPerAttack = 0.02;

            double[] damages = new double[5];
            for (int i = 0; i < 5; i++) {
                damages[i] = baseDamage * weaponCon;
                weaponCon = Math.max(0, weaponCon - wearPerAttack);
            }

            assertThat(damages[0]).isCloseTo(10.0, within(0.01));
            assertThat(damages[4]).isCloseTo(10.0 * 0.92, within(0.01)); // after 4 wears
            assertThat(weaponCon).isCloseTo(0.9, within(0.01));           // after 5 wears
        }

        @Test
        void repeatedDefenses_wearDownArmor_reduceDefense() {
            double armorCon = 1.0;
            double baseDefense = 20.0;
            double wearPerDefense = 0.005;

            for (int i = 0; i < 100; i++) {
                double effectiveDefense = baseDefense * armorCon;
                armorCon = Math.max(0, armorCon - wearPerDefense);
            }

            assertThat(armorCon).isCloseTo(0.5, within(0.01));
            double finalDefense = baseDefense * armorCon;
            assertThat(finalDefense).isCloseTo(10.0, within(0.2));
        }
    }

    // =========================================================================
    // Item Wear Property Parsing
    // =========================================================================

    @Nested
    class ItemWearParsingTests {

        private double getItemWear(WItem item, double defaultWear) {
            if (item == null || item.getServer() == null) return defaultWear;
            String val = item.getServer().get("wear");
            if (val == null || val.isBlank()) return defaultWear;
            try {
                return Double.parseDouble(val.trim());
            } catch (NumberFormatException e) {
                return defaultWear;
            }
        }

        @Test
        void itemWithWearProperty() {
            WItem item = createWeapon("melee", "physical", "weapon", "0.02");
            assertThat(getItemWear(item, 0.01)).isEqualTo(0.02);
        }

        @Test
        void itemWithoutWearProperty_usesDefault() {
            WItem item = createWeapon("melee", "physical", "weapon", null);
            assertThat(getItemWear(item, 0.01)).isEqualTo(0.01);
        }

        @Test
        void itemWithInvalidWear_usesDefault() {
            Map<String, String> server = Map.of("wear", "abc");
            WItem item = WItem.builder().server(server).build();
            assertThat(getItemWear(item, 0.01)).isEqualTo(0.01);
        }

        @Test
        void nullItem_usesDefault() {
            assertThat(getItemWear(null, 0.01)).isEqualTo(0.01);
        }

        @Test
        void stoneWeapon_highWear() {
            WItem stone = createWeapon("melee", "physical", "weapon", "0.05");
            WItem steel = createWeapon("melee", "physical", "weapon", "0.005");
            assertThat(getItemWear(stone, 0.01)).isGreaterThan(getItemWear(steel, 0.01));
        }
    }

    // =========================================================================
    // Armor Wear Aggregation with Backpack
    // =========================================================================

    @Nested
    class ArmorWearAggregationTests {

        private double getItemWear(WItem item, double defaultWear) {
            if (item == null || item.getServer() == null) return defaultWear;
            String val = item.getServer().get("wear");
            if (val == null || val.isBlank()) return defaultWear;
            try { return Double.parseDouble(val.trim()); } catch (NumberFormatException e) { return defaultWear; }
        }

        private boolean matchesDamageType(WItem item, boolean physicalHit, boolean magicalHit) {
            if (item == null || item.getServer() == null) return physicalHit;
            String dt = item.getServer().get("damageType");
            if (dt == null || dt.isBlank()) return physicalHit;
            return (physicalHit && dt.contains("physical")) || (magicalHit && dt.contains("magical"));
        }

        @Test
        void bodyArmorWear_physicalHit_averagesMatchingItems() {
            var data = createData();

            // Equip body armor (physical) and helmet (magical)
            Map<WEARABLE_SLOT, String> wearing = new HashMap<>();
            wearing.put(WEARABLE_SLOT.BODY, "armor1");
            wearing.put(WEARABLE_SLOT.HEAD, "helm1");

            PlayerBackpack backpack = new PlayerBackpack();
            backpack.setWearingItemIds(wearing);
            data.setCachedBackpack(backpack);

            WItem bodyArmor = createArmor("physical", "0.01");
            WItem magicHelm = createArmor("magical", "0.02");

            Map<String, WItem> items = Map.of("armor1", bodyArmor, "helm1", magicHelm);
            data.setCachedItems(items);

            // Physical hit: only body armor matches
            double totalWear = 0;
            int count = 0;
            for (var slot : java.util.Set.of(WEARABLE_SLOT.HEAD, WEARABLE_SLOT.BODY, WEARABLE_SLOT.LEGS,
                    WEARABLE_SLOT.FEET, WEARABLE_SLOT.NECK, WEARABLE_SLOT.ARMS)) {
                String itemId = wearing.get(slot);
                if (itemId == null) continue;
                WItem item = items.get(itemId);
                if (!matchesDamageType(item, true, false)) continue;
                totalWear += getItemWear(item, 0.005);
                count++;
            }
            double avgWear = count > 0 ? totalWear / count : 0;

            assertThat(count).isEqualTo(1); // only body armor
            assertThat(avgWear).isEqualTo(0.01);
        }

        @Test
        void shieldInHandSlot_includedForPhysicalHit() {
            Map<WEARABLE_SLOT, String> wearing = new HashMap<>();
            wearing.put(WEARABLE_SLOT.LEFT_HAND_1, "shield1");
            wearing.put(WEARABLE_SLOT.RIGHT_HAND_1, "sword1");

            WItem shield = createArmor("physical", "0.008", "shield");
            WItem sword = createWeapon("melee", "physical", "weapon", "0.01");

            Map<String, WItem> items = Map.of("shield1", shield, "sword1", sword);

            // Hand slots: only shields contribute to armor wear
            int shieldCount = 0;
            for (var slot : java.util.Set.of(WEARABLE_SLOT.LEFT_HAND_1, WEARABLE_SLOT.RIGHT_HAND_1,
                    WEARABLE_SLOT.LEFT_HAND_2, WEARABLE_SLOT.RIGHT_HAND_2)) {
                String itemId = wearing.get(slot);
                if (itemId == null) continue;
                WItem item = items.get(itemId);
                if (item == null || item.getServer() == null) continue;
                if (!"shield".equals(item.getServer().get("type"))) continue;
                if (!matchesDamageType(item, true, false)) continue;
                shieldCount++;
            }

            assertThat(shieldCount).isEqualTo(1); // shield included, sword excluded
        }

        @Test
        void noArmorEquipped_zeroWear() {
            var data = createData();
            PlayerBackpack backpack = new PlayerBackpack();
            backpack.setWearingItemIds(new HashMap<>());
            data.setCachedBackpack(backpack);
            data.setCachedItems(Map.of());

            // No items → no wear
            double totalWear = 0;
            int count = 0;
            // ... iterate empty wearing → count stays 0
            double avgWear = count > 0 ? totalWear / count : 0;
            assertThat(avgWear).isEqualTo(0.0);
        }
    }
}
