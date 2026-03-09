package de.mhus.nimbus.world.player.gameplay.adventure.handler;

import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.ActiveEffect;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.gameplay.PassiveStats;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Handles skills, constitution, and passive stats calculation.
 */
@Slf4j
public class StatsHandler {

    private final AdventureGameplay gameplay;

    public StatsHandler(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    /**
     * Reload skills from RCharacter and cache in AdventureData.
     */
    public void refreshSkillsCache(PlayerSession session, AdventureData data) {
        try {
            String entityId = session.getEntityId();
            if (entityId == null || session.getWorldId() == null) return;

            PlayerId playerId = PlayerId.of(entityId).orElse(null);
            if (playerId == null) return;

            String regionId = session.getWorldId().getRegionId();
            var characterOpt = gameplay.getCharacterService().getCharacter(
                    playerId.getUserId(), regionId, playerId.getCharacterId());
            if (characterOpt.isEmpty()) return;

            var character = characterOpt.get();
            data.setCachedCharacterDocId(character.getId());
            data.setCachedSkills(new HashMap<>(character.getSkills()));

            // Recalculate passive stats (skills affect them)
            recalculatePassiveStats(data);

            log.debug("Refreshed skills cache for player {}: skills={}",
                    entityId, data.getCachedSkills().size());
        } catch (Exception e) {
            log.error("Failed to refresh skills cache for session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Reload constitution from RCharacter and cache in AdventureData.
     */
    public void refreshConstitutionCache(PlayerSession session, AdventureData data) {
        try {
            String entityId = session.getEntityId();
            if (entityId == null || session.getWorldId() == null) return;

            PlayerId playerId = PlayerId.of(entityId).orElse(null);
            if (playerId == null) return;

            String regionId = session.getWorldId().getRegionId();
            var characterOpt = gameplay.getCharacterService().getCharacter(
                    playerId.getUserId(), regionId, playerId.getCharacterId());
            if (characterOpt.isEmpty()) return;

            var character = characterOpt.get();
            data.setCachedCharacterDocId(character.getId());
            data.setCachedConstitution(new HashMap<>(character.getConstitution()));

            log.debug("Refreshed constitution cache for player {}: {}",
                    entityId, data.getCachedConstitution());
        } catch (Exception e) {
            log.error("Failed to refresh constitution cache for session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Recalculate passive stats from worn equipment and skills.
     * Called when inventory or skills change. Equipment effects are parsed from
     * item.server "effects" (format: "stat:value[:duration[:probability]]").
     * Only permanent effects (no duration) from wearings are considered.
     *
     * Skills apply multiplicative bonuses via Skill.applyMultiplicative:
     * - combat.melee/ranged/magic: multiply physical/magical damage
     * - combat.defense/magicDefense: multiply physical/magical defense
     * - survival.*: additive bonuses to vitals
     */
    public void recalculatePassiveStats(AdventureData data) {
        PassiveStats stats = data.getPassiveStats();
        if (stats == null) {
            stats = new PassiveStats();
            data.setPassiveStats(stats);
        }
        stats.reset();

        var skills = data.getCachedSkills();

        // 1. Collect effects from worn items (non-weapon slots provide passive stats)
        var backpack = data.getCachedBackpack();
        var items = data.getCachedItems();
        if (backpack != null && backpack.getWearingItemIds() != null && items != null) {
            for (var entry : backpack.getWearingItemIds().entrySet()) {
                String itemId = entry.getValue();
                if (itemId == null) continue;

                WItem item = items.get(itemId);
                if (item == null || item.getServer() == null) continue;

                String effectsDef = item.getServer().get("effects");
                if (effectsDef == null || effectsDef.isBlank()) continue;

                // Effects are comma-separated or a JSON array string; items store them as single strings
                // Format in server map: "physical.defense:30,physical.evasion:-0.05"
                for (String effectStr : effectsDef.split(",")) {
                    String trimmed = effectStr.trim();
                    if (trimmed.isEmpty()) continue;
                    try {
                        ActiveEffect effect = ActiveEffect.parse(trimmed, "item:" + itemId);
                        // Only permanent effects (no duration) count as passive
                        if (effect.isPermanent() && !effect.isInstant()) {
                            stats.addEffect(effect.getStat(), effect.getValue());
                        }
                    } catch (Exception e) {
                        log.trace("Skipping unparseable wearing effect '{}' on item {}", trimmed, itemId);
                    }
                }
            }
        }

        // 2. Apply skill bonuses

        // Survival skills (additive): level directly adds to vitals
        // survival.vitality: +1.0 health.max, +0.01 health.regen per level
        int vitality = AdventureSkills.SURVIVAL_VITALITY.getValue(skills);
        stats.addEffect("health.max", vitality * 1.0);
        stats.addEffect("health.regen", vitality * 0.01);

        // survival.endurance: +0.5 stamina.max, +0.02 stamina.regen per level
        int endurance = AdventureSkills.SURVIVAL_ENDURANCE.getValue(skills);
        stats.addEffect("stamina.max", endurance * 0.5);
        stats.addEffect("stamina.regen", endurance * 0.02);

        // survival.willpower: +1.0 mana.max, +0.01 mana.regen per level
        int willpower = AdventureSkills.SURVIVAL_WILLPOWER.getValue(skills);
        stats.addEffect("mana.max", willpower * 1.0);
        stats.addEffect("mana.regen", willpower * 0.01);

        // survival.resilience: reduces hunger/thirst degen (additive regen buff)
        int resilience = AdventureSkills.SURVIVAL_RESILIENCE.getValue(skills);
        stats.addEffect("hunger.regen", resilience * 0.001);
        stats.addEffect("thirst.regen", resilience * 0.001);

        // Combat skills (multiplicative): applied as percent buffs
        // combat.melee: multiplies physical damage (start=100 = no change)
        double meleePercent = AdventureSkills.COMBAT_MELEE.getValue(skills) / 100.0 - 1.0;
        if (meleePercent != 0) stats.addEffect("physical.damagePercent", meleePercent);

        // combat.ranged: also contributes to physical accuracy
        double rangedPercent = AdventureSkills.COMBAT_RANGED.getValue(skills) / 100.0 - 1.0;
        if (rangedPercent != 0) stats.addEffect("physical.accuracy", rangedPercent * 0.1);

        // combat.magic: multiplies magical damage
        double magicPercent = AdventureSkills.COMBAT_MAGIC.getValue(skills) / 100.0 - 1.0;
        if (magicPercent != 0) stats.addEffect("magical.damagePercent", magicPercent);

        // combat.defense: multiplies physical defense
        double defensePercent = AdventureSkills.COMBAT_DEFENSE.getValue(skills) / 100.0 - 1.0;
        if (defensePercent != 0) stats.addEffect("physical.defensePercent", defensePercent);

        // combat.magicDefense: multiplies magical defense
        double mDefensePercent = AdventureSkills.COMBAT_MAGIC_DEFENSE.getValue(skills) / 100.0 - 1.0;
        if (mDefensePercent != 0) stats.addEffect("magical.defensePercent", mDefensePercent);

        log.debug("Recalculated passive stats: physDef={}, magDef={}, healthMax=+{}, manaMax=+{}",
                stats.getPhysicalDefense(), stats.getMagicalDefense(),
                stats.getHealthMax(), stats.getManaMax());
    }

    /**
     * Refresh skills cache when skills are modified.
     */
    public void onSkillsModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshSkillsCache(session, data);
        }
    }

    /**
     * Refresh constitution cache when constitution is modified.
     */
    public void onConstitutionModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshConstitutionCache(session, data);
        }
    }

    /**
     * Add +1 skill experience for the player in the given session.
     * Uses the cached character document ID for a fast atomic increment.
     */
    public void addSkillExperienceForSession(PlayerSession session) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;
        String docId = data.getCachedCharacterDocId();
        if (docId != null) {
            gameplay.getCharacterService().addSkillExperience(docId, 1);
        }
    }
}
