package de.mhus.nimbus.world.player.service;

import de.mhus.nimbus.shared.types.PlayerData;

public class GameplayUtil {
    public static String toString(PlayerData player) {
        if (player == null) return "null";
        if (player.character() == null) return "null";
        if (player.character().getPublicData() == null) return "null";
        return player.character().getPublicData().getPlayerId();
    }
}
