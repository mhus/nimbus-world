package de.mhus.nimbus.world.player.ws;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base network message structure for WebSocket communication.
 * Follows the network-model-2.0 specification.
 *
 * Message structure:
 * - i: request id (client -> server)
 * - r: response id (server -> client, echoes client's i)
 * - t: message type (e.g., "login", "p" for ping, "c.r" for chunk registration)
 * - d: data payload (type-specific)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkMessage {

    /**
     * Request ID (client -> server messages).
     * Used to correlate responses.
     */
    private String i;

    /**
     * Response ID (server -> client messages).
     * Echoes the client's request ID.
     */
    private String r;

    /**
     * Message type identifier.
     * Examples: "login", "p" (ping), "c.r" (chunk registration), "c.q" (chunk query)
     */
    private String t;

    /**
     * Data payload.
     * Type-specific JSON object.
     */
    private JsonNode d;
}
