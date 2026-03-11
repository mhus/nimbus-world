package de.mhus.nimbus.world.life.service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sound utility for world-life NPC sounds.
 * Mirrors GameplayUtil.resolveSound() logic from world-player.
 */
public class LifeSoundUtil {

    public static final String SOUND_NPC_HIT = "n:audio/actions/npc_hit1.ogg,n:audio/actions/npc_hit2.ogg";

    /**
     * Resolve a sound from a comma-separated list. If the value contains commas,
     * a random entry is picked. Blank values fall back to the default.
     */
    public static String resolveSound(String soundValue, String defaultValue) {
        String value = (soundValue != null && !soundValue.isBlank()) ? soundValue.trim() : defaultValue;
        if (value == null || value.isBlank()) return null;
        if (!value.contains(",")) return value;
        String[] parts = value.split(",");
        return parts[ThreadLocalRandom.current().nextInt(parts.length)].trim();
    }
}
