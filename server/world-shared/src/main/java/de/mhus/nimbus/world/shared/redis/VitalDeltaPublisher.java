package de.mhus.nimbus.world.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.shared.gameplay.VitalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Publisher for vital delta broadcasts via Redis.
 *
 * Routes messages based on target entity ID:
 * - "@" prefix (player) -> channel "v.d.p"
 * - No "@" prefix (NPC/entity) -> channel "v.d.e"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VitalDeltaPublisher {

    private static final String CHANNEL_PLAYER = "v.d.p";
    private static final String CHANNEL_ENTITY = "v.d.e";

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;

    /**
     * Publish a single vital delta to the appropriate channel.
     *
     * @param worldId        World ID
     * @param targetEntityId Target entity (@ prefix = player, else = NPC)
     * @param vitalType      Vital type to modify
     * @param delta          Delta value (negative = damage, positive = heal)
     * @param sourceEntityId Entity that caused this delta
     */
    public void publishDelta(String worldId, String targetEntityId, VitalType vitalType, double delta, String sourceEntityId) {
        if (targetEntityId == null || vitalType == null || delta == 0) return;

        try {
            VitalDeltaBroadcastMessage message = VitalDeltaBroadcastMessage.builder()
                    .targetEntityId(targetEntityId)
                    .vitalType(vitalType.name())
                    .delta(delta)
                    .sourceEntityId(sourceEntityId)
                    .worldId(worldId)
                    .build();

            String json = objectMapper.writeValueAsString(message);
            String channel = targetEntityId.startsWith("@") ? CHANNEL_PLAYER : CHANNEL_ENTITY;
            redisMessaging.publish(worldId, channel, json);

            log.debug("Published vital delta: {} {} {} -> {} [source={}]",
                    vitalType, delta, targetEntityId, channel, sourceEntityId);

        } catch (Exception e) {
            log.error("Failed to publish vital delta for {} in world {}", targetEntityId, worldId, e);
        }
    }

    /**
     * Publish an attack to the appropriate channel.
     * The receiver will calculate actual damage using their own defense stats.
     *
     * @param worldId         World ID
     * @param targetEntityId  Target entity (@ prefix = player, else = NPC)
     * @param sourceEntityId  Entity that initiated the attack
     * @param physDmg         Physical raw damage
     * @param physAcc         Physical hit chance (0-1)
     * @param magDmg          Magical raw damage
     * @param magAcc          Magical hit chance (0-1)
     * @param critChance      Critical hit chance (0-1)
     * @param critMult        Critical hit multiplier (e.g. 1.5)
     */
    public void publishAttack(String worldId, String targetEntityId, String sourceEntityId,
                              double physDmg, double physAcc, double magDmg, double magAcc,
                              double critChance, double critMult) {
        publishAttack(worldId, targetEntityId, sourceEntityId, physDmg, physAcc, magDmg, magAcc, critChance, critMult, null, null);
    }

    /**
     * Publish an attack with source session ID and weapon item ID.
     */
    public void publishAttack(String worldId, String targetEntityId, String sourceEntityId,
                              double physDmg, double physAcc, double magDmg, double magAcc,
                              double critChance, double critMult,
                              String sourceSessionId, String weaponItemId) {
        if (targetEntityId == null) return;

        try {
            VitalDeltaBroadcastMessage message = VitalDeltaBroadcastMessage.builder()
                    .type(VitalDeltaBroadcastMessage.TYPE_ATTACK)
                    .targetEntityId(targetEntityId)
                    .sourceEntityId(sourceEntityId)
                    .worldId(worldId)
                    .physicalDamage(physDmg)
                    .physicalAccuracy(physAcc)
                    .magicalDamage(magDmg)
                    .magicalAccuracy(magAcc)
                    .critChance(critChance)
                    .critMultiplier(critMult)
                    .sourceSessionId(sourceSessionId)
                    .weaponItemId(weaponItemId)
                    .build();

            String json = objectMapper.writeValueAsString(message);
            String channel = targetEntityId.startsWith("@") ? CHANNEL_PLAYER : CHANNEL_ENTITY;
            redisMessaging.publish(worldId, channel, json);

            log.debug("Published attack: {} -> {} [phys={}/{}, mag={}/{}, crit={}/{}]",
                    sourceEntityId, targetEntityId, physDmg, physAcc, magDmg, magAcc, critChance, critMult);

        } catch (Exception e) {
            log.error("Failed to publish attack for {} in world {}", targetEntityId, worldId, e);
        }
    }

    /**
     * Publish attack result back to the attacker (hit or miss).
     * Sent to the player channel so world-player can show feedback.
     *
     * @param worldId        World ID
     * @param attackerEntityId Player entity ID (@ prefix)
     * @param targetEntityId  NPC entity ID that was attacked
     * @param hit            True if attack hit, false if missed/blocked
     * @param damage         Actual damage dealt (0 if missed)
     */
    public void publishAttackResult(String worldId, String attackerEntityId, String targetEntityId,
                                     boolean hit, double damage) {
        if (attackerEntityId == null) return;

        try {
            VitalDeltaBroadcastMessage message = VitalDeltaBroadcastMessage.builder()
                    .type(VitalDeltaBroadcastMessage.TYPE_ATTACK_RESULT)
                    .targetEntityId(attackerEntityId)
                    .sourceEntityId(targetEntityId)
                    .worldId(worldId)
                    .delta(damage)
                    .build();

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, CHANNEL_PLAYER, json);

            log.debug("Published attack result: {} -> {} hit={} damage={}",
                    targetEntityId, attackerEntityId, hit, damage);

        } catch (Exception e) {
            log.error("Failed to publish attack result for {} in world {}", attackerEntityId, worldId, e);
        }
    }

    /**
     * Publish multiple vital deltas at once.
     *
     * @param deltas List of delta messages to publish
     */
    public void publishDeltas(List<VitalDeltaBroadcastMessage> deltas) {
        if (deltas == null || deltas.isEmpty()) return;

        for (VitalDeltaBroadcastMessage delta : deltas) {
            try {
                String json = objectMapper.writeValueAsString(delta);
                String channel = delta.getTargetEntityId().startsWith("@") ? CHANNEL_PLAYER : CHANNEL_ENTITY;
                redisMessaging.publish(delta.getWorldId(), channel, json);
            } catch (Exception e) {
                log.error("Failed to publish vital delta for {} in world {}",
                        delta.getTargetEntityId(), delta.getWorldId(), e);
            }
        }

        log.debug("Published {} vital deltas", deltas.size());
    }
}
