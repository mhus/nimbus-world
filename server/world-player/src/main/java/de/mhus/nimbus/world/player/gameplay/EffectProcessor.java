package de.mhus.nimbus.world.player.gameplay;

import de.mhus.nimbus.world.shared.gameplay.VitalType;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Processes active effects on AdventureData each tick.
 *
 * <p>Tick processing order:</p>
 * <ol>
 *   <li>Remove expired effects</li>
 *   <li>Reset all buff accumulators</li>
 *   <li>Accumulate buffs from active effects (max, maxPercent, regen)</li>
 *   <li>Recalculate effective values</li>
 *   <li>Process periodic/DoT effects</li>
 *   <li>Apply hunger/thirst penalties</li>
 *   <li>Apply adrenaline decay</li>
 *   <li>Apply regen/degen</li>
 *   <li>Clamp all values</li>
 * </ol>
 */
@Slf4j
public class EffectProcessor {

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

    /**
     * Process one tick of effects on the adventure data.
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

        // 1. Remove expired effects
        removeExpiredEffects(data);

        // 2. Reset all buff accumulators
        for (var vital : data.getVitals().values()) {
            vital.resetBuffs();
        }
        for (var stat : data.getCombatStats().values()) {
            stat.resetBuffs();
        }

        // 3a. Apply passive stats from equipment + skills
        if (data.getPassiveStats() != null) {
            data.getPassiveStats().applyTo(data);
        }

        // 3b. Accumulate buffs from active effects (skip remote effects)
        for (var effect : data.getActiveEffects()) {
            if (effect.isRemote()) continue; // remote effects don't modify own vitals
            accumulateEffect(data, effect);
        }

        // 4. Apply hunger/thirst penalties on regen rates
        applyVitalPenalties(data);

        // 5. Apply underwater air depletion
        applyUnderwaterAir(data);

        // 6. Apply adrenaline combat idle decay
        applyAdrenalineDecay(data, deltaSeconds);

        // 7. Recalculate effective values
        for (var vital : data.getVitals().values()) {
            vital.recalculate();
        }
        for (var stat : data.getCombatStats().values()) {
            stat.recalculate();
        }

        // 8. Process periodic/DoT effects (routes remote effects to outgoingDeltas)
        processPeriodicEffects(data, deltaSeconds, outgoingDeltas, worldId, sourceEntityId);

        // 9. Apply regen/degen (only for local effects, remote regen handled below)
        for (var vital : data.getVitals().values()) {
            vital.applyRegen(deltaSeconds);
        }

        // 10. Process remote regen effects (non-periodic, continuous regen on remote targets)
        processRemoteRegenEffects(data, deltaSeconds, outgoingDeltas, worldId, sourceEntityId);

        // 11. Reduce durations
        reduceDurations(data, deltaSeconds);

        // 12. Death check
        var health = data.getVital("health");
        return health != null && health.isDepleted();
    }

    /**
     * Remove expired effects from the active effects list.
     */
    private void removeExpiredEffects(AdventureData data) {
        Iterator<ActiveEffect> it = data.getActiveEffects().iterator();
        while (it.hasNext()) {
            ActiveEffect effect = it.next();
            if (effect.isExpired()) {
                log.debug("Effect expired: {} from {}", effect.getStat(), effect.getSource());
                it.remove();
            }
        }
    }

    /**
     * Accumulate an effect's contributions to the appropriate vital or combat stat buffs.
     */
    private void accumulateEffect(AdventureData data, ActiveEffect effect) {
        String stat = effect.getStat();
        if (stat == null || stat.isEmpty()) return;

        // Skip DoT effects here (handled in processPeriodicEffects)
        if (stat.startsWith("dot.")) return;

        // Skip instant effects (already applied on add)
        if (effect.isInstant()) return;

        String group = effect.getStatGroup();
        String modifier = effect.getModifierType();
        double value = effect.getValue() * effect.getStacks();

        // Check if this targets a vital value
        VitalValue vital = data.getVital(group);
        if (vital != null) {
            switch (modifier) {
                case "max" -> vital.setBuffFlat(vital.getBuffFlat() + value);
                case "maxPercent" -> vital.setBuffPercent(vital.getBuffPercent() + value);
                case "regen" -> {
                    if (effect.getProbability() >= 1.0 || ThreadLocalRandom.current().nextDouble() < effect.getProbability()) {
                        vital.setEffectiveRegenRate(vital.getEffectiveRegenRate() + value);
                    }
                }
                default -> log.trace("Unknown vital modifier: {}.{}", group, modifier);
            }
            return;
        }

        // Check if this targets a combat stat
        // Combat stats can be addressed as "physical.damage" (flat) or "physical.damagePercent" (percent)
        if (modifier.endsWith("Percent")) {
            String baseStat = group + "." + modifier.replace("Percent", "");
            CombatStat combatStat = data.getCombatStat(baseStat);
            if (combatStat != null) {
                combatStat.setBuffPercent(combatStat.getBuffPercent() + value);
                return;
            }
        }

        CombatStat combatStat = data.getCombatStat(stat);
        if (combatStat != null) {
            combatStat.setBuffFlat(combatStat.getBuffFlat() + value);
            return;
        }

        // Simple stats without dot notation (attackSpeed, critChance, critMultiplier)
        combatStat = data.getCombatStat(stat);
        if (combatStat != null) {
            combatStat.setBuffFlat(combatStat.getBuffFlat() + value);
        }
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
                // Starving (hunger at max): health degenerates
                health.setEffectiveRegenRate(health.getEffectiveRegenRate() + HUNGER_DEGEN_ON_MAX);
            } else if (hunger.getCurrent() > HUNGER_HIGH_THRESHOLD) {
                // Very hungry: health regen reduced
                double currentRegen = health.getEffectiveRegenRate();
                if (currentRegen > 0) {
                    health.setEffectiveRegenRate(currentRegen * HUNGER_HIGH_HEALTH_REGEN_FACTOR);
                }
            }
        }

        if (thirst != null) {
            if (thirst.getCurrent() >= thirst.getEffectiveMax() && health != null) {
                // Dehydrated (thirst at max): health degenerates faster
                health.setEffectiveRegenRate(health.getEffectiveRegenRate() + THIRST_DEGEN_ON_MAX);
            } else if (thirst.getCurrent() > THIRST_HIGH_THRESHOLD && stamina != null) {
                // Very thirsty: stamina regen reduced
                double currentRegen = stamina.getEffectiveRegenRate();
                if (currentRegen > 0) {
                    stamina.setEffectiveRegenRate(currentRegen * THIRST_HIGH_STAMINA_REGEN_FACTOR);
                }
            }
        }
    }

    /**
     * Apply underwater air depletion. When underwater, air degenerates.
     * When air is depleted, health takes damage.
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
            // Above water: air regenerates
            air.setEffectiveRegenRate(AIR_REGEN_RATE);
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
            // Out of combat: adrenaline decays
            adrenaline.setEffectiveRegenRate(adrenaline.getEffectiveRegenRate() + ADRENALINE_DECAY_RATE);
        }
    }

    /**
     * Process periodic (DoT) effects: check tick timers and apply damage.
     * Remote effects are routed to outgoingDeltas instead of applying locally.
     */
    private void processPeriodicEffects(AdventureData data, double deltaSeconds,
                                        List<VitalDeltaBroadcastMessage> outgoingDeltas,
                                        String worldId, String sourceEntityId) {
        var health = data.getVital("health");

        for (var effect : data.getActiveEffects()) {
            if (!effect.isPeriodic()) continue;

            effect.setTickTimer(effect.getTickTimer() + deltaSeconds);

            if (effect.getTickTimer() >= effect.getTickInterval()) {
                effect.setTickTimer(effect.getTickTimer() - effect.getTickInterval());

                // Probability check
                if (effect.getProbability() < 1.0 && ThreadLocalRandom.current().nextDouble() >= effect.getProbability()) {
                    continue;
                }

                double damage = effect.getValue() * effect.getStacks();

                if (effect.isRemote()) {
                    // Remote periodic effect: collect as outgoing delta
                    if (outgoingDeltas != null && VitalType.isRemoteModifiable(effect.getStat())) {
                        VitalType vitalType = VitalType.fromStat(effect.getStat());
                        if (vitalType != null) {
                            outgoingDeltas.add(VitalDeltaBroadcastMessage.builder()
                                    .targetEntityId(effect.getTargetEntityId())
                                    .vitalType(vitalType.name())
                                    .delta(damage)
                                    .sourceEntityId(sourceEntityId)
                                    .worldId(worldId)
                                    .build());
                        }
                    }
                } else {
                    // Local periodic effect: apply directly to own health
                    if (health != null) {
                        health.setCurrent(health.getCurrent() + damage);
                        log.debug("DoT {} from {}: {} damage, health now {}",
                                effect.getStat(), effect.getSource(), damage, health.getCurrent());
                    }
                }
            }
        }
    }

    /**
     * Process remote regen effects (non-periodic, continuous regen on remote targets).
     * These are effects like "health.regen:5:30" with a targetEntityId.
     * The delta per tick is value * deltaSeconds.
     */
    private void processRemoteRegenEffects(AdventureData data, double deltaSeconds,
                                           List<VitalDeltaBroadcastMessage> outgoingDeltas,
                                           String worldId, String sourceEntityId) {
        if (outgoingDeltas == null) return;

        for (var effect : data.getActiveEffects()) {
            if (!effect.isRemote()) continue;
            if (effect.isPeriodic()) continue; // already handled in processPeriodicEffects
            if (effect.isInstant()) continue;
            if (effect.getStat() == null) continue;

            String modifier = effect.getModifierType();
            if (!"regen".equals(modifier)) continue;
            if (!VitalType.isRemoteModifiable(effect.getStat())) continue;

            // Probability check
            if (effect.getProbability() < 1.0 && ThreadLocalRandom.current().nextDouble() >= effect.getProbability()) {
                continue;
            }

            VitalType vitalType = VitalType.fromStat(effect.getStat());
            if (vitalType == null) continue;

            double delta = effect.getValue() * effect.getStacks() * deltaSeconds;
            if (delta == 0) continue;

            outgoingDeltas.add(VitalDeltaBroadcastMessage.builder()
                    .targetEntityId(effect.getTargetEntityId())
                    .vitalType(vitalType.name())
                    .delta(delta)
                    .sourceEntityId(sourceEntityId)
                    .worldId(worldId)
                    .build());
        }
    }

    /**
     * Reduce durations of all timed effects.
     */
    private void reduceDurations(AdventureData data, double deltaSeconds) {
        for (var effect : data.getActiveEffects()) {
            if (!effect.isPermanent()) {
                effect.setDuration(effect.getDuration() - deltaSeconds);
            }
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
