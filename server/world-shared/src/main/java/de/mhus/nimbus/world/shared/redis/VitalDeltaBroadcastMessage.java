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
 *
 * Two message types:
 * - "DELTA": Direct vital modification (heal, DoT). Uses vitalType + delta.
 * - "ATTACK": Attack broadcast. Receiver calculates damage using own defense stats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalDeltaBroadcastMessage {

    public static final String TYPE_DELTA = "DELTA";
    public static final String TYPE_ATTACK = "ATTACK";
    public static final String TYPE_ATTACK_RESULT = "ATTACK_RESULT";

    /** Target entity ID, e.g. "@user:char" (player) or "cow2" (NPC) */
    private String targetEntityId;

    /** Message type: "DELTA" (direct vital change) or "ATTACK" (receiver calculates damage) */
    @Builder.Default
    private String type = TYPE_DELTA;

    /** Vital type: "HEALTH", "MANA", "STAMINA" — used for DELTA messages */
    private String vitalType;

    /** Delta value to apply: negative = damage, positive = heal — used for DELTA messages */
    private double delta;

    /** Source entity ID that initiated this effect */
    private String sourceEntityId;

    /** World ID */
    private String worldId;

    // --- ATTACK fields (only used when type = "ATTACK") ---

    /** Physical raw damage */
    private double physicalDamage;

    /** Physical hit chance (0-1) */
    private double physicalAccuracy;

    /** Magical raw damage */
    private double magicalDamage;

    /** Magical hit chance (0-1) */
    private double magicalAccuracy;

    /** Critical hit chance (0-1) */
    private double critChance;

    /** Critical hit multiplier (e.g. 1.5) */
    private double critMultiplier;

    /** Source session ID (for looking up attacker position in Redis) */
    private String sourceSessionId;
}
