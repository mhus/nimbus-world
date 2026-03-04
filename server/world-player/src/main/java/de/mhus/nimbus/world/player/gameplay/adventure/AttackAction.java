package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.CombatStat;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.gameplay.VitalValue;
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

    private final AdventureGameplay basic;

    public AttackAction(AdventureGameplay basic) {
        this.basic = basic;
    }

    @Override
    public void handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        // Blocks cannot be attacked
    }

    @Override
    public void handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (entity == null) return;
        String targetEntityId = entity.getEntityId();
        if (targetEntityId == null) return;

        performAttack(session, targetEntityId);
    }

    @Override
    public void handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Items with action=attack need a target, no self-attack
    }

    @Override
    public void handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        if (targetEntityId == null) return;
        performAttack(session, targetEntityId);
    }

    private void performAttack(PlayerSession session, String targetEntityId) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
        if (worldId == null) return;

        // Check stamina
        VitalValue stamina = data.getVital("stamina");
        if (stamina != null && stamina.getCurrent() < STAMINA_COST) {
            log.debug("Player {} has insufficient stamina for attack ({} < {})",
                    session.getEntityId(), stamina.getCurrent(), STAMINA_COST);
            return;
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
            return;
        }
        data.setNextAttackAllowed(now + (long) cooldownMs);

        // Deduct stamina
        if (stamina != null) {
            stamina.setCurrent(stamina.getCurrent() - STAMINA_COST);
            stamina.clamp();
        }

        // Read effective combat stats
        double physDmg = getEffective(data, "physical.damage");
        double physAcc = getEffective(data, "physical.accuracy");
        double magDmg = getEffective(data, "magical.damage");
        double magAcc = getEffective(data, "magical.accuracy");
        double critChance = getEffective(data, "critChance");
        double critMult = getEffective(data, "critMultiplier");

        // Publish attack via Redis
        basic.getVitalDeltaPublisher().publishAttack(
                worldId, targetEntityId, session.getEntityId(),
                physDmg, physAcc, magDmg, magAcc, critChance, critMult);

        // Adrenaline gain + combat timer reset
        basic.getEffectProcessor().addAdrenaline(data, ATTACK_ADRENALINE);
        basic.getEffectProcessor().onCombatAction(data);

        log.debug("Player {} attacked {} [phys={}/{}, mag={}/{}, crit={}/{}]",
                session.getEntityId(), targetEntityId,
                physDmg, physAcc, magDmg, magAcc, critChance, critMult);
    }

    private double getEffective(AdventureData data, String statName) {
        CombatStat stat = data.getCombatStat(statName);
        return stat != null ? stat.getEffective() : 0;
    }
}
