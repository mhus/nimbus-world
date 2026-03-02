package de.mhus.nimbus.world.player.gameplay;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AdventureData extends GameplayData {

    private double health = 100;
    private double hunger = 100;
    private double thirst = 100;
    private double stamina = 100;

    private double maxHealth = 100;
    private double maxHunger = 100;
    private double maxThirst = 100;
    private double maxStamina = 100;

}
