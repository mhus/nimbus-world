package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;

import java.util.Map;

public interface BlockAction {
    void handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, JsonNode params, String blockAction, Map<String, String> serverInfo);
}
