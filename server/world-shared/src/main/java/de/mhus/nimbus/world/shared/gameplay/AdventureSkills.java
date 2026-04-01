package de.mhus.nimbus.world.shared.gameplay;

import java.util.List;

/**
 * All skill definitions for Adventure gameplay mode.
 * Skills are grouped by category prefix: combat, survival, gathering, utility.
 *
 * <p>Skills are either additive (level is a direct bonus) or multiplicative
 * (level is a percentage factor, use {@link Skill#applyMultiplicative}).
 * Multiplicative skills use start=100 (= factor 1.0 = no change).</p>
 *
 * @see Skill
 */
public final class AdventureSkills {

    private AdventureSkills() {}

    // --- Combat Skills (multiplicative: 100 = 1.0x, 200 = 2.0x damage/defense) ---

    public static final Skill COMBAT_MELEE = Skill.of(
            "combat_melee", "Nahkampf",
            "Verbessert Nahkampfschaden und Trefferchance",
            "Kampf", true, 100, 50, 500);

    public static final Skill COMBAT_RANGED = Skill.of(
            "combat_ranged", "Fernkampf",
            "Verbessert Fernkampfschaden und Praezision",
            "Kampf", true, 100, 50, 500);

    public static final Skill COMBAT_MAGIC = Skill.of(
            "combat_magic", "Magie",
            "Verbessert magischen Schaden und Trefferchance",
            "Kampf", true, 100, 50, 500);

    public static final Skill COMBAT_DEFENSE = Skill.of(
            "combat_defense", "Verteidigung",
            "Verbessert physische Abwehr und Ausweichen",
            "Kampf", true, 100, 50, 500);

    public static final Skill COMBAT_MAGIC_DEFENSE = Skill.of(
            "combat_magicDefense", "Magische Abwehr",
            "Verbessert magische Abwehr und Ausweichen",
            "Kampf", true, 100, 50, 500);

    public static final Skill COMBAT_WEAPON_CARE = Skill.of(
            "combat_weaponCare", "Waffenpflege",
            "Reduziert den Verschleiss von Waffen",
            "Kampf", false, 100, 50, 500);

    public static final Skill COMBAT_ARMOR_CARE = Skill.of(
            "combat_armorCare", "Ruestungspflege",
            "Reduziert den Verschleiss von Ruestungen",
            "Kampf", false, 100, 50, 500);

    // --- Survival Skills (additive: level is a direct bonus value) ---

    public static final Skill SURVIVAL_VITALITY = Skill.of(
            "survival_vitality", "Vitalitaet",
            "Mehr Lebenspunkte und Regeneration",
            "Ueberleben", true, 1, 0, 100);

    public static final Skill SURVIVAL_ENDURANCE = Skill.of(
            "survival_endurance", "Ausdauer",
            "Mehr Ausdauer und schnellere Erholung",
            "Ueberleben", true, 1, 0, 100);

    public static final Skill SURVIVAL_WILLPOWER = Skill.of(
            "survival_willpower", "Willenskraft",
            "Mehr Mana und Mana-Regeneration",
            "Ueberleben", true, 1, 0, 100);

    public static final Skill SURVIVAL_RESILIENCE = Skill.of(
            "survival_resilience", "Widerstandskraft",
            "Hunger und Durst sinken langsamer",
            "Ueberleben", true, 1, 0, 100);

    public static final Skill SURVIVAL_ACROBATICS = Skill.of(
            "survival_acrobatics", "Akrobatik",
            "Erlaubt hoeheres Fallen ohne Schaden",
            "Ueberleben", true, 2, 2, 100);

    // --- Gathering Skills (multiplicative: 100 = normal speed, 200 = double speed) ---

    public static final Skill GATHERING_MINING = Skill.of(
            "gathering_mining", "Bergbau",
            "Schnelleres Abbauen von Steinen und Erzen",
            "Sammeln", true, 100, 50, 300);

    public static final Skill GATHERING_WOODWORK = Skill.of(
            "gathering_woodwork", "Holzarbeit",
            "Schnelleres Faellen von Baeumen",
            "Sammeln", true, 100, 50, 300);

    public static final Skill GATHERING_HERBALISM = Skill.of(
            "gathering_herbalism", "Kraeuterkunde",
            "Bessere Ernte bei Pflanzen und Kraeutern",
            "Sammeln", true, 100, 50, 300);

    public static final Skill GATHERING_FISHING = Skill.of(
            "gathering_fishing", "Angeln",
            "Bessere Faenge beim Angeln",
            "Sammeln", true, 100, 50, 300);

    // --- Crafting Skills (additive: level is crafting tier, 0 = beginner, higher = better recipes) ---

    public static final Skill CRAFTING_SMITHING = Skill.of(
            "crafting_smithing", "Schmieden",
            "Erlaubt das Schmieden von Waffen und Ruestungen",
            "Handwerk", true, 0, 0, 10);

    public static final Skill CRAFTING_WOODWORKING = Skill.of(
            "crafting_woodworking", "Holzverarbeitung",
            "Erlaubt die Herstellung von Holzgegenstaenden",
            "Handwerk", true, 0, 0, 10);

    public static final Skill CRAFTING_ALCHEMY = Skill.of(
            "crafting_alchemy", "Alchemie",
            "Erlaubt das Brauen von Traenken und Zubereiten von Speisen",
            "Handwerk", true, 0, 0, 10);

    public static final Skill CRAFTING_WRITING = Skill.of(
            "crafting_writing", "Schreibkunst",
            "Erlaubt die Herstellung von Schriftrollen und Buechern",
            "Handwerk", true, 0, 0, 10);

    // --- Utility Skills ---

    /** Additive: level = bonus slots (start=0, each level = +1 slot) */
    public static final Skill UTILITY_BACKPACK = Skill.of(
            "utility_backpack", "Tragfaehigkeit",
            "Erlaubt das Tragen von mehr Items",
            "Sonstiges", false, 0, 0, 30);

    /** Multiplicative: 100 = normal prices, 150 = 50% better */
    public static final Skill UTILITY_TRADING = Skill.of(
            "utility_trading", "Handel",
            "Bessere Kauf- und Verkaufspreise bei Haendlern",
            "Sonstiges", true, 100, 50, 300);

    /** Multiplicative: 100 = normal detection, 50 = half detection range */
    public static final Skill UTILITY_STEALTH = Skill.of(
            "utility_stealth", "Schleichen",
            "NPCs und Tiere erkennen den Spieler spaeter",
            "Sonstiges", false, 100, 50, 300);

    /**
     * All defined skills as a list.
     */
    public static final List<Skill> ALL = List.of(
            COMBAT_MELEE, COMBAT_RANGED, COMBAT_MAGIC, COMBAT_DEFENSE, COMBAT_MAGIC_DEFENSE,
            COMBAT_WEAPON_CARE, COMBAT_ARMOR_CARE,
            SURVIVAL_VITALITY, SURVIVAL_ENDURANCE, SURVIVAL_WILLPOWER, SURVIVAL_RESILIENCE, SURVIVAL_ACROBATICS,
            GATHERING_MINING, GATHERING_WOODWORK, GATHERING_HERBALISM, GATHERING_FISHING,
            CRAFTING_SMITHING, CRAFTING_WOODWORKING, CRAFTING_ALCHEMY, CRAFTING_WRITING,
            UTILITY_BACKPACK, UTILITY_TRADING, UTILITY_STEALTH
    );

    /**
     * Find a skill definition by its technical name.
     *
     * @param name Skill ID, e.g. "combat_melee"
     * @return Skill or null if not found
     */
    public static Skill byName(String name) {
        for (Skill skill : ALL) {
            if (skill.getName().equals(name)) return skill;
        }
        return null;
    }
}
