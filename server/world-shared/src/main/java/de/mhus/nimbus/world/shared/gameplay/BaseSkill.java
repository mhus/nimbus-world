package de.mhus.nimbus.world.shared.gameplay;

import java.util.Map;

/**
 * Base skill definition for combat-relevant skills shared across all entity types.
 *
 * @param id           Technical skill ID, e.g. "combat_melee"
 * @param category     Skill category, e.g. "combat"
 * @param defaultValue Default value when not present in skills map
 * @param scaleFactor  Scaling factor for applying the skill (e.g. 100.0 for multiplicative skills)
 */
public record BaseSkill(String id, String category, int defaultValue, double scaleFactor) {

    /**
     * Get the current value of this skill from a skills map.
     * Returns {@link #defaultValue} if the skill is not present in the map.
     *
     * @param skills Map of skill name to level (may be null)
     * @return Current skill value
     */
    public int getValue(Map<String, Integer> skills) {
        return skills != null ? skills.getOrDefault(id, defaultValue) : defaultValue;
    }

    /** Combat melee skill — affects physical damage */
    public static final BaseSkill COMBAT_MELEE = new BaseSkill("combat_melee", "combat", 100, 100.0);

    /** Combat ranged skill — affects ranged accuracy */
    public static final BaseSkill COMBAT_RANGED = new BaseSkill("combat_ranged", "combat", 100, 100.0);

    /** Combat magic skill — affects magical damage */
    public static final BaseSkill COMBAT_MAGIC = new BaseSkill("combat_magic", "combat", 100, 100.0);

    /** Combat defense skill — affects physical defense */
    public static final BaseSkill COMBAT_DEFENSE = new BaseSkill("combat_defense", "combat", 100, 100.0);

    /** Combat magic defense skill — affects magical defense */
    public static final BaseSkill COMBAT_MAGIC_DEFENSE = new BaseSkill("combat_magicDefense", "combat", 100, 100.0);
}
