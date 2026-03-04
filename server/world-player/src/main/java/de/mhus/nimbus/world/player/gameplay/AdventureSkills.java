package de.mhus.nimbus.world.player.gameplay;

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
            "combat.melee", "Nahkampf",
            "Verbessert Nahkampfschaden und Trefferchance",
            100, 50, 500);

    public static final Skill COMBAT_RANGED = Skill.of(
            "combat.ranged", "Fernkampf",
            "Verbessert Fernkampfschaden und Praezision",
            100, 50, 500);

    public static final Skill COMBAT_MAGIC = Skill.of(
            "combat.magic", "Magie",
            "Verbessert magischen Schaden und Trefferchance",
            100, 50, 500);

    public static final Skill COMBAT_DEFENSE = Skill.of(
            "combat.defense", "Verteidigung",
            "Verbessert physische Abwehr und Ausweichen",
            100, 50, 500);

    public static final Skill COMBAT_MAGIC_DEFENSE = Skill.of(
            "combat.magicDefense", "Magische Abwehr",
            "Verbessert magische Abwehr und Ausweichen",
            100, 50, 500);

    // --- Survival Skills (additive: level is a direct bonus value) ---

    public static final Skill SURVIVAL_VITALITY = Skill.of(
            "survival.vitality", "Vitalitaet",
            "Mehr Lebenspunkte und Regeneration",
            1, 0, 100);

    public static final Skill SURVIVAL_ENDURANCE = Skill.of(
            "survival.endurance", "Ausdauer",
            "Mehr Ausdauer und schnellere Erholung",
            1, 0, 100);

    public static final Skill SURVIVAL_WILLPOWER = Skill.of(
            "survival.willpower", "Willenskraft",
            "Mehr Mana und Mana-Regeneration",
            1, 0, 100);

    public static final Skill SURVIVAL_RESILIENCE = Skill.of(
            "survival.resilience", "Widerstandskraft",
            "Hunger und Durst sinken langsamer",
            1, 0, 100);

    public static final Skill SURVIVAL_ACROBATICS = Skill.of(
            "survival.acrobatics", "Akrobatik",
            "Erlaubt hoeheres Fallen ohne Schaden",
            2, 2, 100);

    // --- Gathering Skills (multiplicative: 100 = normal speed, 200 = double speed) ---

    public static final Skill GATHERING_MINING = Skill.of(
            "gathering.mining", "Bergbau",
            "Schnelleres Abbauen von Steinen und Erzen",
            100, 50, 300);

    public static final Skill GATHERING_WOODWORK = Skill.of(
            "gathering.woodwork", "Holzarbeit",
            "Schnelleres Faellen von Baeumen",
            100, 50, 300);

    public static final Skill GATHERING_HERBALISM = Skill.of(
            "gathering.herbalism", "Kraeuterkunde",
            "Bessere Ernte bei Pflanzen und Kraeutern",
            100, 50, 300);

    public static final Skill GATHERING_FISHING = Skill.of(
            "gathering.fishing", "Angeln",
            "Bessere Faenge beim Angeln",
            100, 50, 300);

    // --- Utility Skills ---

    /** Additive: level = bonus slots (start=0, each level = +1 slot) */
    public static final Skill UTILITY_BACKPACK = Skill.of(
            "utility.backpack", "Tragfaehigkeit",
            "Erlaubt das Tragen von mehr Items",
            0, 0, 30);

    /** Multiplicative: 100 = normal prices, 150 = 50% better */
    public static final Skill UTILITY_TRADING = Skill.of(
            "utility.trading", "Handel",
            "Bessere Kauf- und Verkaufspreise bei Haendlern",
            100, 50, 300);

    /** Multiplicative: 100 = normal detection, 50 = half detection range */
    public static final Skill UTILITY_STEALTH = Skill.of(
            "utility.stealth", "Schleichen",
            "NPCs und Tiere erkennen den Spieler spaeter",
            100, 50, 300);

    /**
     * All defined skills as a list.
     */
    public static final List<Skill> ALL = List.of(
            COMBAT_MELEE, COMBAT_RANGED, COMBAT_MAGIC, COMBAT_DEFENSE, COMBAT_MAGIC_DEFENSE,
            SURVIVAL_VITALITY, SURVIVAL_ENDURANCE, SURVIVAL_WILLPOWER, SURVIVAL_RESILIENCE, SURVIVAL_ACROBATICS,
            GATHERING_MINING, GATHERING_WOODWORK, GATHERING_HERBALISM, GATHERING_FISHING,
            UTILITY_BACKPACK, UTILITY_TRADING, UTILITY_STEALTH
    );

    /**
     * Find a skill definition by its technical name.
     *
     * @param name Skill ID, e.g. "combat.melee"
     * @return Skill or null if not found
     */
    public static Skill byName(String name) {
        for (Skill skill : ALL) {
            if (skill.getName().equals(name)) return skill;
        }
        return null;
    }
}
