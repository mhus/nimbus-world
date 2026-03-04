package de.mhus.nimbus.world.shared.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message for broadcasting vital deltas to remote entities.
 *
 * Routing by targetEntityId:
 * - "@" prefix -> Channel "v.d.p" (player, handled by world-player)
 * - No "@" prefix -> Channel "v.d.e" (entity, handled by world-life)
 *
 * Redis channel: world:{worldId}:v.d.p or world:{worldId}:v.d.e
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalDeltaBroadcastMessage {

    /** Target entity ID, e.g. "@user:char" (player) or "cow2" (NPC) */
    private String targetEntityId;

    /** Vital type: "HEALTH", "MANA", "STAMINA" */
    private String vitalType;

    /** Delta value to apply: negative = damage, positive = heal */
    private double delta;

    /** Source entity ID that initiated this effect */
    private String sourceEntityId;

    /** World ID */
    private String worldId;
}
