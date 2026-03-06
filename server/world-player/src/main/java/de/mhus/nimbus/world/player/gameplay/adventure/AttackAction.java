package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Attack action: publishes the attacker's combat stats via Redis.
 * The target (player or NPC) receives the ATTACK message and calculates
 * actual damage using their own defense values via CombatResolver.
 */
@Slf4j
public class AttackAction implements GameplayAction {

    private static final double STAMINA_COST = 5.0;
    private static final double ATTACK_ADRENALINE = 5.0;
    private static final double DEFAULT_WEAPON_WEAR = 0.01;

    private final AdventureGameplay basic;

    public AttackAction(AdventureGameplay basic) {
        this.basic = basic;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        // Blocks cannot be attacked
        return false;
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (entity == null) return false;
        String targetEntityId = entity.getEntityId();
        if (targetEntityId == null) return false;

        return performAttack(session, targetEntityId, shortcutKey);
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Items with action=attack need a target, no self-attack
        return false;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        if (targetEntityId == null) return false;
        return performAttack(session, targetEntityId, shortcutKey);
    }

    private boolean performAttack(PlayerSession session, String targetEntityId, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
        if (worldId == null) return false;

        // Check gameMode (PvP/PvE)
        if (!basic.isAttackAllowed(session, targetEntityId)) {
            log.debug("Attack blocked by gameMode: {} -> {}", session.getEntityId(), targetEntityId);
            return false;
        }

        // Check stamina
        VitalValue stamina = data.getVital("stamina");
        if (stamina != null && stamina.getCurrent() < STAMINA_COST) {
            log.debug("Player {} has insufficient stamina for attack ({} < {})",
                    session.getEntityId(), stamina.getCurrent(), STAMINA_COST);
            return false;
        }

        // Check attack speed cooldown
        CombatStat attackSpeedStat = data.getCombatStat("attackSpeed");
        double attackSpeed = attackSpeedStat != null ? attackSpeedStat.getEffective() : 1.0;
        if (attackSpeed <= 0) attackSpeed = 1.0;
        double cooldownMs = 1000.0 / attackSpeed;
        long now = System.currentTimeMillis();
        if (now < data.getNextAttackAllowed()) {
            log.trace("Player {} attack on cooldown ({}ms remaining)",
                    session.getEntityId(), data.getNextAttackAllowed() - now);
            return false;
        }
        data.setNextAttackAllowed(now + (long) cooldownMs);

        // Deduct stamina
        if (stamina != null) {
            stamina.setCurrent(stamina.getCurrent() - STAMINA_COST);
            stamina.clamp();
        }

        // Resolve weapon item first to read its properties
        String weaponItemId = resolveWeaponItemId(session, data, shortcutKey);
        WItem weaponItem = null;
        if (weaponItemId != null) {
            var cachedItems = data.getCachedItems();
            weaponItem = cachedItems != null ? cachedItems.get(weaponItemId) : null;
        }

        // Read weapon properties (bare fists: melee + physical + weapon)
        String rangeType = getServerProp(weaponItem, "rangeType", "melee");
        String damageType = getServerProp(weaponItem, "damageType", "physical");
        String weaponType = getServerProp(weaponItem, "type", "weapon");

        // Skill factors based on weapon properties
        double rangeSkillFactor = calculateRangeSkillFactor(data, rangeType);
        double magicSkillFactor = AdventureSkills.COMBAT_MAGIC.getValue(data.getCachedSkills()) / 100.0;

        // Combat stats scaled by weapon constitution, skill, and damage type
        double weaponCon = getConstitution(data, "weapon");
        boolean hasPhysical = damageType.contains("physical");
        boolean hasMagical = damageType.contains("magical");

        double physDmg = hasPhysical ? getEffective(data, "physical.damage") * weaponCon * rangeSkillFactor : 0;
        double physAcc = hasPhysical ? getEffective(data, "physical.accuracy") * weaponCon * rangeSkillFactor : 0;
        double magDmg = hasMagical ? getEffective(data, "magical.damage") * weaponCon * magicSkillFactor : 0;
        double magAcc = hasMagical ? getEffective(data, "magical.accuracy") * weaponCon * magicSkillFactor : 0;
        double critChance = getEffective(data, "critChance") * weaponCon;
        double critMult = getEffective(data, "critMultiplier");

        // Publish attack via Redis (include sessionId for position lookup and weaponItemId)
        basic.getVitalDeltaPublisher().publishAttack(
                worldId, targetEntityId, session.getEntityId(),
                physDmg, physAcc, magDmg, magAcc, critChance, critMult,
                session.getSessionId(), weaponItemId);

        // Adrenaline gain + combat timer reset
        basic.getEffectProcessor().addAdrenaline(data, ATTACK_ADRENALINE);
        basic.getEffectProcessor().onCombatAction(data);

        // Weapon constitution wear (not for potions — they are consumed)
        if (weaponItemId != null && !"potion".equals(weaponType)) {
            double itemWear = basic.getItemWear(weaponItem, DEFAULT_WEAPON_WEAR);
            basic.applyConstitutionWear(session, data, "weapon", itemWear, AdventureSkills.COMBAT_WEAPON_CARE);
        }

        log.debug("Player {} attacked {} with {} {} [range={}, dmgType={}, phys={}/{}, mag={}/{}, crit={}/{}]",
                session.getEntityId(), targetEntityId, weaponType, weaponItemId,
                rangeType, damageType, physDmg, physAcc, magDmg, magAcc, critChance, critMult);
        return true;
    }

    private String resolveWeaponItemId(PlayerSession session, AdventureData data, String shortcutKey) {
        // Try to resolve from shortcut (e.g. shortcut type "right_hand_1")
        if (shortcutKey != null) {
            String itemId = basic.resolveShortcutItemId(session, shortcutKey);
            if (itemId != null) return itemId;
        }
        // Fallback: check RIGHT_HAND_1, then LEFT_HAND_1
        var backpack = data.getCachedBackpack();
        if (backpack == null || backpack.getWearingItemIds() == null) return null;
        String right = backpack.getWearingItemIds().get(WEARABLE_SLOT.RIGHT_HAND_1);
        if (right != null) return right;
        return backpack.getWearingItemIds().get(WEARABLE_SLOT.LEFT_HAND_1);
    }

    private double calculateRangeSkillFactor(AdventureData data, String rangeType) {
        boolean melee = rangeType.contains("melee");
        boolean ranged = rangeType.contains("ranged");
        if (melee && ranged) {
            // Hybrid weapon: use the higher skill
            double meleeSkill = AdventureSkills.COMBAT_MELEE.getValue(data.getCachedSkills()) / 100.0;
            double rangedSkill = AdventureSkills.COMBAT_RANGED.getValue(data.getCachedSkills()) / 100.0;
            return Math.max(meleeSkill, rangedSkill);
        } else if (ranged) {
            return AdventureSkills.COMBAT_RANGED.getValue(data.getCachedSkills()) / 100.0;
        } else {
            return AdventureSkills.COMBAT_MELEE.getValue(data.getCachedSkills()) / 100.0;
        }
    }

    private String getServerProp(WItem item, String key, String defaultValue) {
        if (item == null || item.getServer() == null) return defaultValue;
        String val = item.getServer().get(key);
        return val != null && !val.isBlank() ? val.trim() : defaultValue;
    }

    private double getEffective(AdventureData data, String statName) {
        CombatStat stat = data.getCombatStat(statName);
        return stat != null ? stat.getEffective() : 0;
    }

    private double getConstitution(AdventureData data, String category) {
        var con = data.getCachedConstitution();
        if (con == null) return 1.0;
        return con.getOrDefault(category, 1.0);
    }
}
