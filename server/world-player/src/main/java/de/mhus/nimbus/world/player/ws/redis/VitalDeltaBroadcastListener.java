package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.VitalValue;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.gameplay.VitalType;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Listens for vital delta messages targeting players on this pod.
 * Channel pattern: world:*:v.d.p (player vital deltas)
 *
 * Receives deltas from other entities (players/NPCs) and applies them
 * to the targeted player's vitals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VitalDeltaBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribeToVitalDeltas() {
        redisMessaging.subscribeToAllWorlds("v.d.p", this::handleVitalDelta);
        log.info("Subscribed to player vital deltas for all worlds (pattern: world:*:v.d.p)");
    }

    private void handleVitalDelta(String topic, String message) {
        try {
            VitalDeltaBroadcastMessage delta = objectMapper.readValue(message, VitalDeltaBroadcastMessage.class);

            if (delta.getTargetEntityId() == null || delta.getVitalType() == null) {
                log.warn("Invalid vital delta message: missing targetEntityId or vitalType");
                return;
            }

            // Find the target player session on this pod
            PlayerSession targetSession = sessionManager.findByEntityId(delta.getTargetEntityId());
            if (targetSession == null) {
                // Target player not on this pod - normal in a multi-pod setup
                log.trace("Vital delta target {} not found on this pod", delta.getTargetEntityId());
                return;
            }

            if (!(targetSession.getGameplayData() instanceof AdventureData data)) {
                log.trace("Target session {} has no AdventureData", delta.getTargetEntityId());
                return;
            }

            // Resolve vital type
            VitalType vitalType;
            try {
                vitalType = VitalType.valueOf(delta.getVitalType());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown vital type in delta: {}", delta.getVitalType());
                return;
            }

            VitalValue vital = data.getVital(vitalType.vitalName());
            if (vital == null) {
                log.trace("Vital {} not found on target {}", vitalType.vitalName(), delta.getTargetEntityId());
                return;
            }

            // Apply delta and clamp
            vital.setCurrent(vital.getCurrent() + delta.getDelta());
            vital.clamp();

            log.debug("Applied vital delta to {}: {} {} (from {}), now {}",
                    delta.getTargetEntityId(), vitalType, delta.getDelta(),
                    delta.getSourceEntityId(), vital.getCurrent());

        } catch (Exception e) {
            log.error("Failed to handle vital delta from topic {}: {}", topic, e.getMessage(), e);
        }
    }
}
