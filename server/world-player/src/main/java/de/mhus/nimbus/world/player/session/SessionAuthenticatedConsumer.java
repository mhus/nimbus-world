package de.mhus.nimbus.world.player.session;

/**
 * Consumer interface for session authentication events.
 * Implementations are notified when a session is successfully authenticated.
 */
public interface SessionAuthenticatedConsumer {

    /**
     * Called when a session is authenticated.
     *
     * @param session The session that was authenticated
     */
    void onSessionAuthenticated(PlayerSession session);
}
