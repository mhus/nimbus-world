package de.mhus.nimbus.world.player.gameplay.adventure.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Handles vital signs management: sending updates, stamina speed control,
 * resetting vitals, applying damage, and setting vital values.
 */
@Slf4j
public class VitalsHandler {

    private static final double STAMINA_DEPLETED_SPEED = 0.1;

    private final AdventureGameplay gameplay;

    public VitalsHandler(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    /**
     * Send vitals data to the client as a server command.
     */
    public void sendVitalsUpdate(PlayerSession session, AdventureData data) {
        try {
            ObjectMapper objectMapper = gameplay.getObjectMapper();
            ArrayNode vitalsArray = objectMapper.createArrayNode();
            for (var vital : data.getVitals().values()) {
                // Skip vitals with sendThreshold if percentage has not yet crossed the threshold
                if (vital.getSendThreshold() > 0 && vital.getPercentage() <= vital.getSendThreshold()) {
                    continue;
                }
                // Skip air when not underwater and full
                if ("air".equals(vital.getType()) && !data.isUnderwater() && vital.isFull()) {
                    continue;
                }
                ObjectNode vNode = objectMapper.createObjectNode();
                vNode.put("type", vital.getType());
                vNode.put("current", (int) Math.ceil(vital.getCurrent()));
                vNode.put("max", (int) Math.ceil(vital.getEffectiveMax()));
                vNode.put("regenRate", vital.getEffectiveRegenRate());
                vNode.put("degenRate", vital.getEffectiveRegenRate() < 0 ? -vital.getEffectiveRegenRate() : 0);
                vNode.put("color", vital.getColor());
                vNode.put("name", vital.getDisplayName());
                vNode.put("order", vital.getOrder());
                if (vital.getOptions() != null) {
                    vNode.put("options", vital.getOptions());
                }
                vitalsArray.add(vNode);
            }
            String currentVitalisData = objectMapper.writeValueAsString(vitalsArray);
            if (!currentVitalisData.equals(data.getLastVitalisData())) {
                data.setLastVitalisData(currentVitalisData);
            } else {
                // No changes in vitals, skip sending update
                return;
            }

            ObjectNode commandData = objectMapper.createObjectNode();
            commandData.put("cmd", "vitals");
            commandData.set("args", vitalsArray);
            commandData.put("oneway", true);

            gameplay.getClientService().sendCommand(session, "vitals", commandData);

            // Low health alert flash
            VitalValue health = data.getVital("health");
            if (health != null && health.getPercentage() > 0 && health.getPercentage() <= 0.25) {
                gameplay.getClientService().sendCommand(session, "flashImage",
                        List.of("n:textures/actions/health_alert.png", "500", "0.5"));
            }

            // Broadcast health status to other players via entity status update
            gameplay.getCombatHandler().publishPlayerHealthStatus(session, data);
        } catch (Exception e) {
            log.error("Failed to send vitals update to session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    /**
     * Check stamina depletion and send/reset slow speed to the client.
     */
    public void checkStaminaSpeed(PlayerSession session, AdventureData data) {
        VitalValue stamina = data.getVital("stamina");
        if (stamina == null) return;

        boolean depleted = stamina.getCurrent() <= 0;
        if (depleted && !data.isStaminaSlowSent()) {
            data.setStaminaSlowSent(true);
            gameplay.getClientService().sendCommand(session, "speed",
                    List.of(String.valueOf(STAMINA_DEPLETED_SPEED)));
            log.debug("Stamina depleted for {}, sending slow speed {}", session.getEntityId(), STAMINA_DEPLETED_SPEED);
        } else if (!depleted && data.isStaminaSlowSent()) {
            data.setStaminaSlowSent(false);
            gameplay.getClientService().sendCommand(session, "speed", List.of("0"));
            log.debug("Stamina recovered for {}, resetting speed override", session.getEntityId());
        }
    }

    /**
     * Reset all vitals to their default current values (full revive).
     * Health, stamina, mana, air -> max. Hunger, thirst -> 0. Adrenaline -> 0.
     */
    public void resetVitalsToDefaults(AdventureData data) {
        for (var vital : data.getVitals().values()) {
            switch (vital.getType()) {
                case "hunger", "thirst", "adrenaline" -> vital.setCurrent(0);
                default -> vital.setCurrent(vital.getEffectiveMax());
            }
            vital.clamp();
        }
    }

    /**
     * Central method to apply damage to a player's health.
     * Clamps health, sends vitals update, and triggers death if health reaches 0.
     *
     * @param session The player session
     * @param data    The player's adventure data
     * @param amount  Positive damage value (will be subtracted from health)
     */
    public void applyDamage(PlayerSession session, AdventureData data, double amount) {
        amount = Math.abs(amount);
        if (amount == 0) return;

        VitalValue health = data.getVital("health");
        if (health == null) return;

        health.setCurrent(health.getCurrent() - amount);
        health.clamp();

        sendVitalsUpdate(session, data);

        if (health.getCurrent() <= 0) {
            log.info("Player {} died (damage={})", session.getEntityId(), amount);
            gameplay.getCombatHandler().onPlayerDeath(session, data);
        }
    }

    /**
     * Set the current value of a vital by type.
     */
    public void setVitalCurrent(AdventureData data, String type, double value) {
        var vital = data.getVital(type);
        if (vital != null) vital.setCurrent(value);
    }

    /**
     * Set the base value of a vital by type and recalculate.
     */
    public void setVitalBase(AdventureData data, String type, double value) {
        var vital = data.getVital(type);
        if (vital != null) {
            vital.setBase(value);
            vital.recalculate();
        }
    }
}
