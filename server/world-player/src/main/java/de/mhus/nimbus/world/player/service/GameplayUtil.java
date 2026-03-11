package de.mhus.nimbus.world.player.service;

import de.mhus.nimbus.shared.types.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class GameplayUtil {

    // Sound defaults
    public static final String SOUND_DOOR_OPEN = "n:audio/actions/door_open1.ogg,n:audio/actions/door_open2.ogg";
    public static final String SOUND_DOOR_CLOSE = "n:audio/actions/door_close1.ogg,n:audio/actions/door_close2.ogg";
    public static final String SOUND_WINDOW_OPEN = "n:audio/actions/window_open1.ogg,n:audio/actions/window_open2.ogg";
    public static final String SOUND_WINDOW_CLOSE = "n:audio/actions/window_close1.ogg,n:audio/actions/window_close2.ogg";
    public static final String SOUND_TOGGLE = "n:audio/actions/toggle1.ogg,n:audio/actions/toggle2.ogg";
    public static final String SOUND_ITEM_DROP = "n:audio/actions/item_drop1.ogg,n:audio/actions/item_drop2.ogg";
    public static final String SOUND_ITEM_COLLECT = "n:audio/actions/item_collect1.ogg,n:audio/actions/item_collect2.ogg";
    public static final String SOUND_CHEST_OPEN = "n:audio/actions/chest_open1.ogg,n:audio/actions/chest_open2.ogg";
    public static final String SOUND_NPC_HIT = "n:audio/actions/npc_hit1.ogg,n:audio/actions/npc_hit2.ogg";
    public static final String SOUND_ATTACK = "n:audio/actions/attack1.ogg,n:audio/actions/attack2.ogg";
    public static final String SOUND_ATTACK_HIT = "n:audio/actions/attack_hit1.ogg,n:audio/actions/attack_hit2.ogg";
    public static final String SOUND_ATTACK_BLOCKED = "n:audio/actions/attack_blocked1.ogg,n:audio/actions/attack_blocked2.ogg";

    /**
     * Resolve a sound from a comma-separated list. If the value contains commas,
     * a random entry is picked. Blank values fall back to the default.
     */
    public static String resolveSound(String soundValue, String defaultValue) {
        String value = (soundValue != null && !soundValue.isBlank()) ? soundValue.trim() : defaultValue;
        if (value == null || value.isBlank()) return "";
        if (!value.contains(",")) return value;
        String[] parts = value.split(",");
        return parts[ThreadLocalRandom.current().nextInt(parts.length)].trim();
    }
    public static String toString(PlayerData player) {
        if (player == null) return "null";
        if (player.character() == null) return "null";
        if (player.character().getPublicData() == null) return "null";
        return player.character().getPublicData().getPlayerId();
    }

    public static Map<String, String> extractParams(String prefix, Map<String, String> nr1, Map<String, String> nr2) {
        int prefixLen = prefix.length();
        HashMap<String, String> res = new HashMap<>();
        if (nr1 != null) {
            nr1.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
                    .forEach(e -> res.put(e.getKey().substring(prefixLen), e.getValue()));
        }
        if (nr2 != null) {
            nr2.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
                    .forEach(e -> res.put(e.getKey().substring(prefixLen), e.getValue()));
        }
        return res;
    }
}
