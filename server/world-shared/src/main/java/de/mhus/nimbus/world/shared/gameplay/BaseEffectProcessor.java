package de.mhus.nimbus.world.shared.gameplay;

import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared effect processor with generic tick logic for any entity (players and NPCs).
 *
 * <p>Tick processing order:</p>
 * <ol>
 *   <li>Remove expired effects</li>
 *   <li>Reset all buff accumulators</li>
 *   <li>Apply passive stats from equipment/skills</li>
 *   <li>Accumulate buffs from active effects</li>
 *   <li>Hook: {@link #afterAccumulate(EntityCombatData)} — for subclass-specific penalties</li>
 *   <li>Recalculate effective values</li>
 *   <li>Process periodic/DoT effects</li>
 *   <li>Apply regen/degen</li>
 *   <li>Process remote regen effects</li>
 *   <li>Reduce durations</li>
 *   <li>Death check (health <= 0)</li>
 * </ol>
 *
 * <p>Subclasses can override {@link #afterAccumulate(EntityCombatData)} to add
 * adventure-specific penalties (hunger/thirst, adrenaline decay, underwater air).</p>
 */
@Slf4j
public class BaseEffectProcessor {

    /**
     * Process one tick of effects on entity combat data.
     *
     * @param data           The entity combat data to process
     * @param deltaSeconds   Time elapsed since last tick (usually ~1.0)
     * @param outgoingDeltas Collects VitalDelta messages for remote effects (may be null)
     * @param worldId        World ID for outgoing delta messages
     * @param sourceEntityId Entity ID owning these effects (source of remote deltas)
     * @return true if the entity died (health <= 0)
     */
    public boolean processTick(EntityCombatData data, double deltaSeconds,
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

        // 3. Apply passive stats from equipment + skills
        if (data.getPassiveStats() != null) {
            data.getPassiveStats().applyTo(data);
        }

        // 4. Accumulate buffs from active effects (skip remote effects)
        for (var effect : data.getActiveEffects()) {
            if (effect.isRemote()) continue;
            accumulateEffect(data, effect);
        }

        // 5. Hook for subclass-specific modifications (penalties, etc.)
        afterAccumulate(data, deltaSeconds);

        // 6. Recalculate effective values
        for (var vital : data.getVitals().values()) {
            vital.recalculate();
        }
        for (var stat : data.getCombatStats().values()) {
            stat.recalculate();
        }

        // 7. Early death check — if health is already depleted, skip regen and die
        var health = data.getVital("health");
        if (health != null && health.isDepleted()) {
            return true;
        }

        // 8. Process periodic/DoT effects
        processPeriodicEffects(data, deltaSeconds, outgoingDeltas, worldId, sourceEntityId);

        // 9. Apply regen/degen
        for (var vital : data.getVitals().values()) {
            vital.applyRegen(deltaSeconds);
        }

        // 10. Process remote regen effects
        processRemoteRegenEffects(data, deltaSeconds, outgoingDeltas, worldId, sourceEntityId);

        // 11. Reduce durations
        reduceDurations(data, deltaSeconds);

        // 12. Death check after DoTs
        return health != null && health.isDepleted();
    }

    /**
     * Hook called after buff accumulation and before recalculation.
     * Override in subclasses to add entity-type-specific penalties.
     *
     * @param data         The entity combat data
     * @param deltaSeconds Time elapsed since last tick
     */
    protected void afterAccumulate(EntityCombatData data, double deltaSeconds) {
        // Default: no additional modifications
    }

    /**
     * Remove expired effects from the active effects list.
     */
    protected void removeExpiredEffects(EntityCombatData data) {
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
    protected void accumulateEffect(EntityCombatData data, ActiveEffect effect) {
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
        }
    }

    /**
     * Process periodic (DoT) effects: check tick timers and apply damage.
     */
    protected void processPeriodicEffects(EntityCombatData data, double deltaSeconds,
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
     */
    protected void processRemoteRegenEffects(EntityCombatData data, double deltaSeconds,
                                              List<VitalDeltaBroadcastMessage> outgoingDeltas,
                                              String worldId, String sourceEntityId) {
        if (outgoingDeltas == null) return;

        for (var effect : data.getActiveEffects()) {
            if (!effect.isRemote()) continue;
            if (effect.isPeriodic()) continue;
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
    protected void reduceDurations(EntityCombatData data, double deltaSeconds) {
        for (var effect : data.getActiveEffects()) {
            if (!effect.isPermanent()) {
                effect.setDuration(effect.getDuration() - deltaSeconds);
            }
        }
    }
}
