package de.mhus.nimbus.world.player.gameplay;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ShowTimeAction extends AbstractGamplayAction {

    public ShowTimeAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        if (session.getWorldId() == null) return false;

        String timeStr = basic.getWorldService().getFormattedWorldTime(session.getWorldId());
        if (timeStr == null) {
            basic.getBasicClientService().sendNotification(session, 3, "", "Time system not active", null);
            return true;
        }

        basic.getBasicClientService().sendNotification(session, 3, "", timeStr, null);
        return true;
    }
}
