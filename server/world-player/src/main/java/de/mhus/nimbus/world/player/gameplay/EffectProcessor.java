package de.mhus.nimbus.world.player.gameplay;

import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.gameplay.BaseEffectProcessor;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Adventure-specific effect processor.
 * Extends BaseEffectProcessor with hunger/thirst penalties, underwater air, and adrenaline decay.
 */
@Slf4j
public class EffectProcessor extends BaseEffectProcessor {

    private static final double HUNGER_HIGH_THRESHOLD = 80.0;
    private static final double THIRST_HIGH_THRESHOLD = 80.0;
    private static final double HUNGER_DEGEN_ON_MAX = -1.0;
    private static final double THIRST_DEGEN_ON_MAX = -2.0;
    private static final double HUNGER_HIGH_HEALTH_REGEN_FACTOR = 0.5;
    private static final double THIRST_HIGH_STAMINA_REGEN_FACTOR = 0.5;
    private static final double ADRENALINE_COMBAT_IDLE_THRESHOLD = 5.0;
    private static final double ADRENALINE_DECAY_RATE = -0.5;
    private static final double AIR_DEGEN_RATE = -5.0;
    private static final double AIR_REGEN_RATE = 10.0;
    private static final double AIR_DEPLETED_HEALTH_DEGEN = -10.0;
    private static final double SPRINT_STAMINA_DRAIN = -3.0;

    /**
     * Process one tick of effects on the adventure data.
     * Delegates to BaseEffectProcessor which calls afterAccumulate() for adventure-specific logic.
     *
     * @param data           The adventure data to process
     * @param deltaSeconds   Time elapsed since last tick (usually ~1.0)
     * @param outgoingDeltas Collects VitalDelta messages for remote effects (may be null for local-only processing)
     * @param worldId        World ID for outgoing delta messages
     * @param sourceEntityId Entity ID of the player owning these effects (source of remote deltas)
     * @return true if the player died (health <= 0)
     */
    public boolean processTick(AdventureData data, double deltaSeconds,
                               List<VitalDeltaBroadcastMessage> outgoingDeltas,
                               String worldId, String sourceEntityId) {
        return super.processTick(data, deltaSeconds, outgoingDeltas, worldId, sourceEntityId);
    }

    @Override
    protected void afterAccumulate(EntityCombatData data, double deltaSeconds) {
        if (!(data instanceof AdventureData adventureData)) return;

        // Adventure-specific penalties between accumulate and recalculate
        applyVitalPenalties(adventureData);
        applyUnderwaterAir(adventureData);
        applySprintStaminaDrain(adventureData);
        applyAdrenalineDecay(adventureData, deltaSeconds);
    }

    /**
     * Apply hunger/thirst penalties to health and stamina regen.
     * Hunger/thirst rise from 0 (sated) to max (starving/dehydrated).
     */
    private void applyVitalPenalties(AdventureData data) {
        var hunger = data.getVital("hunger");
        var thirst = data.getVital("thirst");
        var health = data.getVital("health");
        var stamina = data.getVital("stamina");

        if (hunger != null && health != null) {
            if (hunger.getCurrent() >= hunger.getEffectiveMax()) {
                health.setEffectiveRegenRate(health.getEffectiveRegenRate() + HUNGER_DEGEN_ON_MAX);
            } else if (hunger.getCurrent() > HUNGER_HIGH_THRESHOLD) {
                double currentRegen = health.getEffectiveRegenRate();
                if (currentRegen > 0) {
                    health.setEffectiveRegenRate(currentRegen * HUNGER_HIGH_HEALTH_REGEN_FACTOR);
                }
            }
        }

        if (thirst != null) {
            if (thirst.getCurrent() >= thirst.getEffectiveMax() && health != null) {
                health.setEffectiveRegenRate(health.getEffectiveRegenRate() + THIRST_DEGEN_ON_MAX);
            } else if (thirst.getCurrent() > THIRST_HIGH_THRESHOLD && stamina != null) {
                double currentRegen = stamina.getEffectiveRegenRate();
                if (currentRegen > 0) {
                    stamina.setEffectiveRegenRate(currentRegen * THIRST_HIGH_STAMINA_REGEN_FACTOR);
                }
            }
        }
    }

    /**
     * Apply underwater air depletion.
     */
    private void applyUnderwaterAir(AdventureData data) {
        var air = data.getVital("air");
        if (air == null) return;

        if (data.isUnderwater()) {
            air.setEffectiveRegenRate(AIR_DEGEN_RATE);
            if (air.isDepleted()) {
                var health = data.getVital("health");
                if (health != null) {
                    health.setEffectiveRegenRate(health.getEffectiveRegenRate() + AIR_DEPLETED_HEALTH_DEGEN);
                }
            }
        } else if (!air.isFull()) {
            air.setEffectiveRegenRate(AIR_REGEN_RATE);
        }
    }

    /**
     * Apply stamina drain while sprinting.
     * Endurance skill reduces drain: at level 100, sprint costs no stamina.
     */
    private void applySprintStaminaDrain(AdventureData data) {
        if (!data.isSprinting()) return;

        var stamina = data.getVital("stamina");
        if (stamina == null) return;

        int endurance = AdventureSkills.SURVIVAL_ENDURANCE.getValue(data.getCachedSkills());
        double reduction = Math.min(endurance / 100.0, 1.0);
        double drain = SPRINT_STAMINA_DRAIN * (1.0 - reduction);

        if (drain < 0) {
            stamina.setEffectiveRegenRate(stamina.getEffectiveRegenRate() + drain);
        }
    }

    /**
     * Apply adrenaline decay when out of combat.
     */
    private void applyAdrenalineDecay(AdventureData data, double deltaSeconds) {
        var adrenaline = data.getVital("adrenaline");
        if (adrenaline == null) return;

        data.setCombatIdleTimer(data.getCombatIdleTimer() + deltaSeconds);

        if (data.getCombatIdleTimer() >= ADRENALINE_COMBAT_IDLE_THRESHOLD) {
            adrenaline.setEffectiveRegenRate(adrenaline.getEffectiveRegenRate() + ADRENALINE_DECAY_RATE);
        }
    }

    /**
     * Register a combat action (resets the combat idle timer for adrenaline).
     */
    public void onCombatAction(AdventureData data) {
        data.setCombatIdleTimer(0);
    }

    /**
     * Add adrenaline for a specific combat event.
     *
     * @param data   The adventure data
     * @param amount Amount of adrenaline to add
     */
    public void addAdrenaline(AdventureData data, double amount) {
        var adrenaline = data.getVital("adrenaline");
        if (adrenaline != null) {
            adrenaline.setCurrent(adrenaline.getCurrent() + amount);
            adrenaline.clamp();
        }
        onCombatAction(data);
    }
}
