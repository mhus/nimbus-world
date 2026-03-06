package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ShowCoordinatesAction extends AbstractGamplayAction {

    public ShowCoordinatesAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        var pos = session.getLastPosition();
        if (pos == null) {
            basic.getBasicClientService().sendNotification(session, 3, "", "Position unknown", null);
            return true;
        }

        int x = (int) Math.round(pos.getX());
        int y = (int) Math.round(pos.getY());
        int z = (int) Math.round(pos.getZ());

        basic.getBasicClientService().sendNotification(session, 3, "",
                "(" + x + ", " + y + ", " + z + ")", null);
        return true;
    }
}
