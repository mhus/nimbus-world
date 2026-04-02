package de.mhus.nimbus.world.player.gameplay;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Survival gameplay mode extending AdventureGameplay.
 * Uses SurvivalEffectProcessor which adds lethal hunger/thirst penalties:
 * when hunger or thirst are maxed, health actively degenerates and can lead to death.
 */
@Service
@Slf4j
public class SurvivalGameplay extends AdventureGameplay {

    private final SurvivalEffectProcessor survivalEffectProcessor = new SurvivalEffectProcessor();

    @Override
    public EffectProcessor getEffectProcessor() {
        return survivalEffectProcessor;
    }
}
