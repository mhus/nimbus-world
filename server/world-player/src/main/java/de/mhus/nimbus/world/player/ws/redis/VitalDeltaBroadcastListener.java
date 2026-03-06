package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.gameplay.VitalType;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;

import java.util.List;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Listens for vital delta messages targeting players on this pod.
 * Channel pattern: world:*:v.d.p (player vital deltas)
 *
 * Receives deltas from other entities (players/NPCs) and applies them
 * to the targeted player's vitals. ATTACK messages are delegated to
 * AdventureGameplay for defence resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VitalDeltaBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final AdventureGameplay adventureGameplay;
    private final ClientService clientService;

    @PostConstruct
    public void subscribeToVitalDeltas() {
        redisMessaging.subscribeToAllWorlds("v.d.p", this::handleVitalDelta);
        log.info("Subscribed to player vital deltas for all worlds (pattern: world:*:v.d.p)");
    }

    private void handleVitalDelta(String topic, String message) {
        try {
            VitalDeltaBroadcastMessage msg = objectMapper.readValue(message, VitalDeltaBroadcastMessage.class);

            if (msg.getTargetEntityId() == null) {
                log.warn("Invalid vital delta message: missing targetEntityId");
                return;
            }

            // Find the target player session on this pod
            PlayerSession targetSession = sessionManager.findByEntityId(msg.getTargetEntityId());
            if (targetSession == null) {
                log.trace("Vital delta target {} not found on this pod", msg.getTargetEntityId());
                return;
            }

            if (!(targetSession.getGameplayData() instanceof AdventureData data)) {
                log.trace("Target session {} has no AdventureData", msg.getTargetEntityId());
                return;
            }

            String type = msg.getType();
            log.debug("Received vital delta: type={}, target={}, source={}", type, msg.getTargetEntityId(), msg.getSourceEntityId());
            if (VitalDeltaBroadcastMessage.TYPE_ATTACK.equals(type)) {
                adventureGameplay.handleIncomingAttack(targetSession, data, msg);
            } else if (VitalDeltaBroadcastMessage.TYPE_ATTACK_RESULT.equals(type)) {
                handleAttackResult(targetSession, msg);
            } else {
                handleDelta(msg, data);
            }

        } catch (Exception e) {
            log.error("Failed to handle vital delta from topic {}: {}", topic, e.getMessage(), e);
        }
    }

    private void handleAttackResult(PlayerSession session, VitalDeltaBroadcastMessage msg) {
        boolean hit = msg.getDelta() != 0;
        String texture = hit
                ? "n:textures/actions/attack_hit.png"
                : "n:textures/actions/attack_blocked.png";
        clientService.sendCommand(session, "flashImage", List.of(texture, "500", "0.5"));

        log.debug("Attack result for {}: {} (damage={})",
                session.getEntityId(), hit ? "HIT" : "BLOCKED", msg.getDelta());
    }

    private void handleDelta(VitalDeltaBroadcastMessage msg, AdventureData data) {
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

        VitalValue vital = data.getVital(vitalType.vitalName());
        if (vital == null) {
            log.trace("Vital {} not found on target {}", vitalType.vitalName(), msg.getTargetEntityId());
            return;
        }

        vital.setCurrent(vital.getCurrent() + msg.getDelta());
        vital.clamp();

        log.debug("Applied vital delta to {}: {} {} (from {}), now {}",
                msg.getTargetEntityId(), vitalType, msg.getDelta(),
                msg.getSourceEntityId(), vital.getCurrent());
    }
}
