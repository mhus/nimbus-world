package de.mhus.nimbus.world.life.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for combat commands from remote servers.
 * Received via Redis channel: world:{worldId}:remote.combat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteCombatCommand {

    private String entityId;
    private String action;
    private String targetEntityId;
    private double physicalDamage;
    private double physicalAccuracy;
    private double magicalDamage;
    private double magicalAccuracy;
    private double critChance;
    private double critMultiplier;
}
