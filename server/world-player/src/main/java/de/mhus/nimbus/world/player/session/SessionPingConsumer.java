package de.mhus.nimbus.world.player.session;

@FunctionalInterface
public interface SessionPingConsumer {
    enum ACTION {
        NONE,
        DISCONNECT
    }

    ACTION onSessionPing(PlayerSession session);
}
