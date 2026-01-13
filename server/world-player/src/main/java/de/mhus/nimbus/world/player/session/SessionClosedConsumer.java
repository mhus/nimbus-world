package de.mhus.nimbus.world.player.session;

/**
 * Consumer interface for session close events.
 * Implementations are notified when a session is closed or removed.
 */
public interface SessionClosedConsumer {

    /**
     * Called when a session is closed.
     *
     * @param session The session that was closed
     */
    void onSessionClosed(PlayerSession session);
}
