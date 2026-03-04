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
