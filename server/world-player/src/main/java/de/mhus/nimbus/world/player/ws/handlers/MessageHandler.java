package de.mhus.nimbus.world.player.ws.handlers;

import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;

/**
 * Interface for handling specific message types.
 * Each message type (login, ping, chunk request, etc.) implements this interface.
 */
public interface MessageHandler {

    /**
     * Handle incoming message.
     *
     * @param session  Current player session
     * @param message  Parsed network message
     * @throws Exception if handling fails
     */
    void handle(PlayerSession session, NetworkMessage message) throws Exception;

    /**
     * Get message type this handler supports.
     * Examples: "login", "p" (ping), "c.r" (chunk registration)
     *
     * @return message type identifier
     */
    String getMessageType();
}
