package de.mhus.nimbus.world.player.gameplay;

import lombok.extern.slf4j.Slf4j;

/**
 * Survival-specific effect processor.
 * Extends the base EffectProcessor with lethal hunger/thirst penalties:
 * when hunger or thirst are maxed, health actively degenerates, leading to death.
 */
@Slf4j
public class SurvivalEffectProcessor extends EffectProcessor {

    private static final double HUNGER_DEGEN_ON_MAX = -1.0;
    private static final double THIRST_DEGEN_ON_MAX = -2.0;

    @Override
    protected void applyVitalPenalties(AdventureData data) {
        super.applyVitalPenalties(data);

        var hunger = data.getVital("hunger");
        var thirst = data.getVital("thirst");
        var health = data.getVital("health");

        if (hunger != null && health != null && hunger.getCurrent() >= hunger.getEffectiveMax()) {
            health.setEffectiveRegenRate(health.getEffectiveRegenRate() + HUNGER_DEGEN_ON_MAX);
        }
        if (thirst != null && health != null && thirst.getCurrent() >= thirst.getEffectiveMax()) {
            health.setEffectiveRegenRate(health.getEffectiveRegenRate() + THIRST_DEGEN_ON_MAX);
        }
    }
}
