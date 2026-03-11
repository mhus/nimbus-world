package de.mhus.nimbus.world.player.gameplay.adventure.handler;

import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.service.GameplayUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.gameplay.CombatResolver;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.Skill;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles combat mechanics: incoming attacks, player death, armor wear,
 * damage type matching, and health status broadcasting.
 */
@Slf4j
public class CombatHandler {

    public static final Set<WEARABLE_SLOT> BODY_ARMOR_SLOTS = Set.of(
            WEARABLE_SLOT.HEAD, WEARABLE_SLOT.BODY, WEARABLE_SLOT.LEGS, WEARABLE_SLOT.FEET,
            WEARABLE_SLOT.NECK, WEARABLE_SLOT.ARMS, WEARABLE_SLOT.LEFT_RING, WEARABLE_SLOT.RIGHT_RING);

    public static final Set<WEARABLE_SLOT> HAND_SLOTS = Set.of(
            WEARABLE_SLOT.LEFT_HAND_1, WEARABLE_SLOT.RIGHT_HAND_1,
            WEARABLE_SLOT.LEFT_HAND_2, WEARABLE_SLOT.RIGHT_HAND_2);

    private static final double DEFAULT_ARMOR_WEAR = 0.005;

    private final AdventureGameplay gameplay;

    public CombatHandler(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    /**
     * Handle an incoming ATTACK broadcast on the defender side.
     * Uses cached defence values (base + PassiveStats) to resolve damage via CombatResolver.
     * Checks gameMode on the defender's hex grid before applying damage.
     *
     * @param session The defender's session
     * @param data    The defender's AdventureData
     * @param msg     The incoming attack message
     */
    public void handleIncomingAttack(PlayerSession session, AdventureData data, VitalDeltaBroadcastMessage msg) {
        log.debug("Incoming attack on player {} from {} [phys={}/{}, mag={}/{}]",
                msg.getTargetEntityId(), msg.getSourceEntityId(),
                msg.getPhysicalDamage(), msg.getPhysicalAccuracy(),
                msg.getMagicalDamage(), msg.getMagicalAccuracy());
        // Check gameMode on defender side
        if (!isAttackAllowed(session, msg.getSourceEntityId())) {
            log.debug("Incoming attack blocked by gameMode: {} -> {} (gameMode={})",
                    msg.getSourceEntityId(), msg.getTargetEntityId(), gameplay.resolveGameMode(session));
            return;
        }

        // Read defender's cached defence stats, scaled by armor constitution and defense skills
        double armorCon = getConstitutionValue(data, "armor");
        double physDefSkill = AdventureSkills.COMBAT_DEFENSE.getValue(data.getCachedSkills()) / 100.0;
        double magDefSkill = AdventureSkills.COMBAT_MAGIC_DEFENSE.getValue(data.getCachedSkills()) / 100.0;
        double defPhysDef = getEffectiveStat(data, "physical.defense") * armorCon * physDefSkill;
        double defPhysEvasion = getEffectiveStat(data, "physical.evasion") * armorCon * physDefSkill;
        double defMagDef = getEffectiveStat(data, "magical.defense") * armorCon * magDefSkill;
        double defMagEvasion = getEffectiveStat(data, "magical.evasion") * armorCon * magDefSkill;

        // Resolve damage
        double damage = CombatResolver.resolve(
                msg.getPhysicalDamage(), msg.getPhysicalAccuracy(),
                msg.getMagicalDamage(), msg.getMagicalAccuracy(),
                msg.getCritChance(), msg.getCritMultiplier(),
                defPhysDef, defPhysEvasion,
                defMagDef, defMagEvasion);

        if (damage == 0) {
            log.debug("Attack from {} on {} missed (phyDef={}, phyEva={}, magDef={}, magEva={})",
                    msg.getSourceEntityId(), msg.getTargetEntityId(),
                    defPhysDef, defPhysEvasion, defMagDef, defMagEvasion);
            // Sound: attack blocked
            gameplay.getClientService().sendCommand(session, "playSound", List.of(GameplayUtil.resolveSound(null, GameplayUtil.SOUND_ATTACK_BLOCKED)));
            // Successful active defense: +1 skill experience
            if (data.getCachedCharacterDocId() != null) {
                gameplay.getCharacterService().addSkillExperience(data.getCachedCharacterDocId(), 1);
            }
            return;
        }

        // Adrenaline gain + combat timer reset for defender
        gameplay.getEffectProcessor().addAdrenaline(data, 3.0);
        gameplay.getEffectProcessor().onCombatAction(data);

        gameplay.getVitalsHandler().applyDamage(session, data, damage);

        // Sound: attack hit
        gameplay.getClientService().sendCommand(session, "playSound", List.of(GameplayUtil.resolveSound(null, GameplayUtil.SOUND_ATTACK_HIT)));

        // Armor constitution wear - only wear items matching the incoming damage type
        boolean physicalHit = msg.getPhysicalDamage() > 0;
        boolean magicalHit = msg.getMagicalDamage() > 0;
        double armorWear = calculateArmorWear(data, physicalHit, magicalHit);
        if (armorWear > 0) {
            applyConstitutionWear(session, data, "armor", armorWear, AdventureSkills.COMBAT_ARMOR_CARE);
        }
    }

    /**
     * Handle player death: check for 1up item first, otherwise normal death.
     */
    public void onPlayerDeath(PlayerSession session, AdventureData data) {
        // Check backpack for a 1up item
        var oneUpItems = gameplay.getGameplayService().findItemsByEffect(session, "1up");
        String oneUpItemId = oneUpItems.isEmpty() ? null : oneUpItems.getFirst().getItemId();
        if (oneUpItemId != null) {
            // Consume the 1up item
            gameplay.getGameplayService().reduceItem(session, oneUpItemId, 1);

            // Reset all vitals to their default values (full revive)
            gameplay.getVitalsHandler().resetVitalsToDefaults(data);

            // Remove all non-permanent effects
            data.getActiveEffects().removeIf(e -> !e.isPermanent());

            data.setMovementState("WALK");

            gameplay.getClientService().sendSystemNotification(session, "1Up", "You have been revived!");
            log.info("Player {} used 1Up item {} to revive", session.getEntityId(), oneUpItemId);
        } else {
            // Normal death: partial reset
            var health = data.getVital("health");
            if (health != null) {
                health.setCurrent(health.getEffectiveMax());
            }

            data.getActiveEffects().removeIf(e -> !e.isPermanent());

            var hunger = data.getVital("hunger");
            if (hunger != null) {
                hunger.setCurrent(hunger.getEffectiveMax() * 0.5);
            }
            var thirst = data.getVital("thirst");
            if (thirst != null) {
                thirst.setCurrent(thirst.getEffectiveMax() * 0.5);
            }

            var adrenaline = data.getVital("adrenaline");
            if (adrenaline != null) {
                adrenaline.setCurrent(0);
            }

            data.setMovementState("WALK");
            var air = data.getVital("air");
            if (air != null) {
                air.setCurrent(air.getEffectiveMax());
            }

            gameplay.getClientService().sendSystemNotification(session, "Death", "You have died and been revived.");
        }

        gameplay.getVitalsHandler().sendVitalsUpdate(session, data);
    }

    /**
     * Check if an attack is allowed based on the attacker's current hex grid gameMode.
     *
     * @param session       The attacker's session
     * @param targetEntityId The target entity ID (@ prefix = player -> PvP, else -> PvE)
     * @return true if the attack is allowed
     */
    public boolean isAttackAllowed(PlayerSession session, String targetEntityId) {
        String gameMode = gameplay.resolveGameMode(session);
        boolean targetIsPlayer = targetEntityId != null && targetEntityId.startsWith("@");
        return targetIsPlayer ? gameMode.contains("P") : gameMode.contains("E");
    }

    /**
     * Calculate average armor wear from equipped defense items matching the incoming damage type.
     * Body armor slots are always included. Hand slots are only included if the item type is "shield".
     * Items are filtered by their damageType property - only items matching the incoming damage are worn.
     */
    public double calculateArmorWear(AdventureData data, boolean physicalHit, boolean magicalHit) {
        var backpack = data.getCachedBackpack();
        if (backpack == null || backpack.getWearingItemIds() == null) return 0;
        var cachedItems = data.getCachedItems();

        double totalWear = 0;
        int count = 0;

        // Body armor slots
        for (var slot : BODY_ARMOR_SLOTS) {
            String itemId = backpack.getWearingItemIds().get(slot);
            if (itemId == null) continue;
            WItem item = cachedItems != null ? cachedItems.get(itemId) : null;
            if (!matchesDamageType(item, physicalHit, magicalHit)) continue;
            totalWear += getItemWear(item, DEFAULT_ARMOR_WEAR);
            count++;
        }

        // Hand slots - only shields
        for (var slot : HAND_SLOTS) {
            String itemId = backpack.getWearingItemIds().get(slot);
            if (itemId == null) continue;
            WItem item = cachedItems != null ? cachedItems.get(itemId) : null;
            if (!"shield".equals(getServerProp(item, "type"))) continue;
            if (!matchesDamageType(item, physicalHit, magicalHit)) continue;
            totalWear += getItemWear(item, DEFAULT_ARMOR_WEAR);
            count++;
        }

        return count > 0 ? totalWear / count : 0;
    }

    /**
     * Apply constitution wear after attack or defense.
     * Calculates actual wear from item base wear and care skill, then reduces
     * the constitution value atomically in MongoDB and updates the local cache.
     *
     * @param session   Player session
     * @param data      Adventure data with cached constitution
     * @param category  Constitution category ("weapon" or "armor")
     * @param itemWear  Base wear from item server property (e.g. 0.01)
     * @param careSkill Skill that reduces wear (higher = less wear)
     */
    public void applyConstitutionWear(PlayerSession session, AdventureData data,
                                       String category, double itemWear, Skill careSkill) {
        if (itemWear <= 0) return;

        // Skill factor: skill 100 = 1.0x wear, skill 200 = 0.5x wear
        double skillFactor = careSkill.getValue(data.getCachedSkills()) / 100.0;
        if (skillFactor <= 0) skillFactor = 0.01;
        double actualWear = itemWear / skillFactor;

        // Update local cache directly
        var con = data.getCachedConstitution();
        if (con == null) {
            con = new java.util.HashMap<>();
            data.setCachedConstitution(con);
        }
        double current = con.getOrDefault(category, 1.0);
        double newValue = Math.max(0.0, current - actualWear);
        con.put(category, newValue);

        // Atomic DB update
        String entityId = session.getEntityId();
        if (entityId == null || session.getWorldId() == null) return;
        var playerId = de.mhus.nimbus.shared.types.PlayerId.of(entityId).orElse(null);
        if (playerId == null) return;
        String regionId = session.getWorldId().getRegionId();
        var characterOpt = gameplay.getCharacterService().getCharacter(
                playerId.getUserId(), regionId, playerId.getCharacterId());
        if (characterOpt.isEmpty()) return;

        gameplay.getCharacterService().reduceConstitution(characterOpt.get().getId(), category, actualWear);
    }

    /**
     * Check if an item matches the incoming damage type.
     */
    public boolean matchesDamageType(WItem item, boolean physicalHit, boolean magicalHit) {
        String damageType = getServerProp(item, "damageType");
        if (damageType == null || damageType.isBlank()) return physicalHit; // default: physical armor
        return (physicalHit && damageType.contains("physical"))
                || (magicalHit && damageType.contains("magical"));
    }

    /**
     * Get effective combat stat value.
     */
    public double getEffectiveStat(AdventureData data, String statName) {
        CombatStat stat = data.getCombatStat(statName);
        return stat != null ? stat.getEffective() : 0;
    }

    /**
     * Get constitution value for a category, defaulting to 1.0.
     */
    public double getConstitutionValue(AdventureData data, String category) {
        var con = data.getCachedConstitution();
        if (con == null) return 1.0;
        return con.getOrDefault(category, 1.0);
    }

    /**
     * Get the wear value from an item's server properties.
     * Returns defaultWear if no "wear" property is set.
     */
    public double getItemWear(WItem item, double defaultWear) {
        if (item == null || item.getServer() == null) return defaultWear;
        String val = item.getServer().get("wear");
        if (val == null || val.isBlank()) return defaultWear;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultWear;
        }
    }

    /**
     * Get a property from an item's server map.
     */
    public String getServerProp(WItem item, String key) {
        if (item == null || item.getServer() == null) return null;
        return item.getServer().get(key);
    }

    /**
     * Broadcast health status to other players via entity status update.
     */
    public void publishPlayerHealthStatus(PlayerSession session, AdventureData data) {
        VitalValue health = data.getVital("health");
        if (health == null) return;
        String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
        String entityId = session.getEntityId();
        if (worldId == null || entityId == null) return;

        gameplay.getEntityStatusPublisher().publishStatusUpdate(worldId, entityId,
                Map.of("health", health.getCurrent(), "healthMax", health.getEffectiveMax()),
                session.getWebSocketSession().getId());
    }
}
