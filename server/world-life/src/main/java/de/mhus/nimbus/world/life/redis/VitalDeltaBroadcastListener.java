package de.mhus.nimbus.world.life.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.service.LifeSoundUtil;
import de.mhus.nimbus.world.life.service.SimulatorService;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.gameplay.CombatResolver;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.gameplay.VitalType;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.shared.redis.EntityStateRedisService;
import de.mhus.nimbus.world.shared.redis.EntityStatusPublisher;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.VitalDeltaPublisher;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Listens for vital delta messages targeting NPC entities on this pod.
 * Channel: world:{worldId}:v.d.e (entity vital deltas)
 *
 * Handles two message types:
 * - ATTACK: Resolves damage using NPC's defense stats via CombatResolver
 * - DELTA: Applies direct vital modification (heal, DoT)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VitalDeltaBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final SimulatorService simulatorService;
    private final WorldDiscoveryService worldDiscoveryService;
    private final EntityStatusPublisher entityStatusPublisher;
    private final EntityStateRedisService entityStateRedisService;
    private final VitalDeltaPublisher vitalDeltaPublisher;
    private final ObjectMapper objectMapper;

    private final Set<WorldId> subscribedWorlds = new HashSet<>();

    @PostConstruct
    public void initialize() {
        worldDiscoveryService.addWorldActivationListener(this::subscribeToWorld);
        updateSubscriptions();
    }

    private synchronized void subscribeToWorld(WorldId worldId) {
        if (subscribedWorlds.contains(worldId)) return;
        redisMessaging.subscribe(worldId.getId(), "v.d.e", (topic, message) -> handleVitalDelta(worldId, message));
        subscribedWorlds.add(worldId);
        log.info("Subscribed to entity vital deltas for world: {}", worldId);
    }

    /**
     * Periodically check for removed worlds and clean up subscriptions.
     * Subscriptions are now primarily handled via WorldActivationListener callback.
     */
    @Scheduled(fixedDelay = 10000)
    public void updateSubscriptions() {
        Set<WorldId> knownWorlds = worldDiscoveryService.getKnownWorldIds();

        // Backup: subscribe to any worlds not yet subscribed
        for (WorldId worldId : knownWorlds) {
            subscribeToWorld(worldId);
        }

        Set<WorldId> toRemove = new HashSet<>(subscribedWorlds);
        toRemove.removeAll(knownWorlds);
        for (WorldId worldId : toRemove) {
            redisMessaging.unsubscribe(worldId.getId(), "v.d.e");
            subscribedWorlds.remove(worldId);
            log.info("Unsubscribed from entity vital deltas for world: {}", worldId);
        }
    }

    private void handleVitalDelta(WorldId worldId, String message) {
        try {
            VitalDeltaBroadcastMessage delta = objectMapper.readValue(message, VitalDeltaBroadcastMessage.class);

            if (delta.getTargetEntityId() == null) {
                log.warn("Invalid vital delta message for world {}: missing targetEntityId", worldId);
                return;
            }

            var state = simulatorService.findSimulationState(worldId, delta.getTargetEntityId());
            if (state == null) {
                log.trace("Vital delta target entity {} not loaded on this pod in world {}",
                        delta.getTargetEntityId(), worldId);
                return;
            }

            EntityCombatData combatData = state.getCombatData();
            if (combatData == null) {
                log.debug("World {}: Entity {} has no combat data, ignoring vital delta",
                        worldId, delta.getTargetEntityId());
                return;
            }

            String type = delta.getType();
            if (VitalDeltaBroadcastMessage.TYPE_PROXIMITY.equals(type)) {
                handleProximity(worldId, state, combatData, delta);
            } else if (VitalDeltaBroadcastMessage.TYPE_ATTACK.equals(type)) {
                handleAttack(worldId, state, combatData, delta);
            } else if (VitalDeltaBroadcastMessage.TYPE_REVIVE.equals(type)) {
                handleRevive(worldId, state, combatData, delta);
            } else {
                handleDelta(worldId, combatData, delta);
            }

        } catch (Exception e) {
            log.error("Failed to handle vital delta for world {}: {}", worldId, e.getMessage(), e);
        }
    }

    /**
     * Handle an incoming ATTACK message.
     * Uses the NPC's defense stats to resolve damage via CombatResolver.
     */
    private void handleAttack(WorldId worldId, SimulationState state,
                               EntityCombatData combatData, VitalDeltaBroadcastMessage msg) {
        // Read defender's effective combat stats
        double defPhysDef = getEffectiveStat(combatData, "physical.defense");
        double defPhysEvasion = getEffectiveStat(combatData, "physical.evasion");
        double defMagDef = getEffectiveStat(combatData, "magical.defense");
        double defMagEvasion = getEffectiveStat(combatData, "magical.evasion");

        // Resolve damage using CombatResolver
        double damage = CombatResolver.resolve(
                msg.getPhysicalDamage(), msg.getPhysicalAccuracy(),
                msg.getMagicalDamage(), msg.getMagicalAccuracy(),
                msg.getCritChance(), msg.getCritMultiplier(),
                defPhysDef, defPhysEvasion,
                defMagDef, defMagEvasion);

        log.debug("World {}: Attack on {} from {}: physDmg={}, physAcc={}, magDmg={}, magAcc={}, crit={}/{}, def: phys={}/{}, mag={}/{} → damage={}",
                worldId, msg.getTargetEntityId(), msg.getSourceEntityId(),
                msg.getPhysicalDamage(), msg.getPhysicalAccuracy(),
                msg.getMagicalDamage(), msg.getMagicalAccuracy(),
                msg.getCritChance(), msg.getCritMultiplier(),
                defPhysDef, defPhysEvasion, defMagDef, defMagEvasion, damage);

        // Resolve NPC hit sound (only on hit)
        String hitSound = null;
        double soundX = 0, soundY = 0, soundZ = 0;
        if (damage != 0 && state.getEntity() != null) {
            String soundValue = state.getEntity().getServer() != null
                    ? state.getEntity().getServer().get("sound_hit") : null;
            hitSound = LifeSoundUtil.resolveSound(soundValue, LifeSoundUtil.SOUND_NPC_HIT);
            Vector3 pos = state.getEntity().getPosition();
            if (pos != null) {
                soundX = pos.getX();
                soundY = pos.getY();
                soundZ = pos.getZ();
            }
        }

        // Send attack result back to attacker (with optional NPC hit sound)
        vitalDeltaPublisher.publishAttackResult(
                worldId.getId(), msg.getSourceEntityId(), msg.getTargetEntityId(),
                damage != 0, damage,
                hitSound, soundX, soundY, soundZ);

        // Track attacker for loot eligibility (even on miss — they are engaged)
        if (msg.getSourceEntityId() != null) {
            state.getAttackers().add(msg.getSourceEntityId());
        }

        // Activate or refresh combat mode (even on miss — entity should react to being attacked)
        long now = System.currentTimeMillis();
        if (combatData.getCombatStrategy() != null) {
            if (!state.isInCombat()) {
                state.setCombatStrategy(combatData.getCombatStrategy());
                state.enterCombat(msg.getSourceEntityId(), msg.getSourceSessionId(), now);
                log.info("World {}: Entity {} entered combat (strategy={}, attacker={})",
                        worldId, msg.getTargetEntityId(), combatData.getCombatStrategy(), msg.getSourceEntityId());
                // Spread combat to nearby entities
                simulatorService.spreadCombatMode(worldId, msg.getTargetEntityId(),
                        msg.getSourceEntityId(), msg.getSourceSessionId());
            } else {
                state.refreshCombat(msg.getSourceEntityId(), msg.getSourceSessionId(), now);
            }
        }

        if (damage == 0) {
            return;
        }

        // Apply damage to health
        VitalValue health = combatData.getVital("health");
        if (health != null) {
            health.setCurrent(health.getCurrent() + damage); // damage is negative
            health.clamp();

            // Publish health status to clients
            publishHealthStatus(worldId, msg.getTargetEntityId(), health);

            log.debug("World {}: Entity {} took {} damage from {}, health now {}/{}",
                    worldId, msg.getTargetEntityId(), -damage, msg.getSourceEntityId(),
                    health.getCurrent(), health.getEffectiveMax());
        }
    }

    /**
     * Handle a PROXIMITY message.
     * A player entered the attention range of this entity.
     * If the entity has combat_aggroOnProximity=true, it enters combat mode.
     */
    private void handleProximity(WorldId worldId, SimulationState state,
                                  EntityCombatData combatData, VitalDeltaBroadcastMessage msg) {
        String aggroOnProximity = state.getEntity() != null && state.getEntity().getServer() != null
                ? state.getEntity().getServer().get("combat_aggroOnProximity") : null;
        if (!"true".equals(aggroOnProximity)) {
            log.trace("World {}: Entity {} does not aggro on proximity", worldId, msg.getTargetEntityId());
            return;
        }

        if (state.isInCombat()) {
            log.trace("World {}: Entity {} already in combat, ignoring proximity", worldId, msg.getTargetEntityId());
            return;
        }

        if (combatData.getCombatStrategy() == null) {
            log.trace("World {}: Entity {} has no combat strategy, ignoring proximity", worldId, msg.getTargetEntityId());
            return;
        }

        long now = System.currentTimeMillis();
        state.setCombatStrategy(combatData.getCombatStrategy());
        state.enterCombat(msg.getSourceEntityId(), msg.getSourceSessionId(), now);
        log.info("World {}: Entity {} entered combat via proximity (attacker={})",
                worldId, msg.getTargetEntityId(), msg.getSourceEntityId());

        // Spread combat to nearby entities
        simulatorService.spreadCombatMode(worldId, msg.getTargetEntityId(),
                msg.getSourceEntityId(), msg.getSourceSessionId());
    }

    /**
     * Handle a DELTA message (direct vital modification).
     */
    private void handleDelta(WorldId worldId, EntityCombatData combatData, VitalDeltaBroadcastMessage msg) {
        if (msg.getVitalType() == null) {
            log.warn("Invalid DELTA message: missing vitalType");
            return;
        }

        VitalType vitalType;
        try {
            vitalType = VitalType.valueOf(msg.getVitalType());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown vital type in delta: {}", msg.getVitalType());
            return;
        }

        VitalValue vital = combatData.getVital(vitalType.vitalName());
        if (vital == null) {
            log.trace("Vital {} not found on entity {}", vitalType.vitalName(), msg.getTargetEntityId());
            return;
        }

        vital.setCurrent(vital.getCurrent() + msg.getDelta());
        vital.clamp();

        log.debug("World {}: Applied vital delta to entity {}: {} {} (from {}), now {}",
                worldId, msg.getTargetEntityId(), vitalType, msg.getDelta(),
                msg.getSourceEntityId(), vital.getCurrent());
    }

    /**
     * Handle a REVIVE message for a dead entity.
     * Revives the entity in place if it is in DEAD state (before GONE).
     */
    private void handleRevive(WorldId worldId, SimulationState state,
                               EntityCombatData combatData, VitalDeltaBroadcastMessage msg) {
        boolean revived = simulatorService.reviveEntity(worldId, state);
        if (revived) {
            log.info("World {}: Entity {} revived by {}", worldId, msg.getTargetEntityId(), msg.getSourceEntityId());
        } else {
            log.debug("World {}: Entity {} cannot be revived (not in DEAD state)", worldId, msg.getTargetEntityId());
        }
    }

    private void publishHealthStatus(WorldId worldId, String entityId, VitalValue health) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("health", health.getCurrent());
        status.put("healthMax", health.getEffectiveMax());
        entityStatusPublisher.publishStatusUpdate(worldId.getId(), entityId, status, null);
        entityStateRedisService.updateHealth(worldId.getId(), entityId, health.getCurrent(), health.getEffectiveMax());
    }

    private double getEffectiveStat(EntityCombatData data, String statName) {
        CombatStat stat = data.getCombatStat(statName);
        return stat != null ? stat.getEffective() : 0;
    }
}
