package de.mhus.nimbus.world.player.session;

public interface SessionPingConsumer {
    enum ACTION {
        NONE,
        DISCONNECT
    }

    ACTION onSessionPing(PlayerSession session);

}
