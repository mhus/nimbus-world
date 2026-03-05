package de.mhus.nimbus.world.player.service;

import de.mhus.nimbus.shared.types.PlayerData;

import java.util.HashMap;
import java.util.Map;

public class GameplayUtil {
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
