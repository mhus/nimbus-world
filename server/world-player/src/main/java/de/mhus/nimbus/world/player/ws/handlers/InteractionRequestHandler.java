package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

/**
 * Handles generic interaction request messages from clients.
 * Message type: "int.r" (Interaction Request, Client → Server)
 *
 * Client sends interaction requests for blocks/objects.
 * Server processes and may send response "int.rs".
 *
 * Expected data:
 * {
 *   "x": 10,
 *   "y": 64,
 *   "z": 10,
 *   "g": "123"  // optional, groupId of block
 * }
 *
 * Response (if needed):
 * {
 *   "success": false,
 *   "errorCode": 403,
 *   "errorMessage": "You do not have permission..."
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InteractionRequestHandler implements MessageHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String getMessageType() {
        return "int.r";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Interaction request from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();
        String requestId = message.getI();

        // Extract position
        Integer x = data.has("x") ? data.get("x").asInt() : null;
        Integer y = data.has("y") ? data.get("y").asInt() : null;
        Integer z = data.has("z") ? data.get("z").asInt() : null;
        String groupId = data.has("g") ? data.get("g").asText() : null;

        if (x == null || y == null || z == null) {
            log.warn("Interaction request without position");
            sendFailureResponse(session, requestId, 400, "Missing position");
            return;
        }

        // TODO: Load block at position from database
        // TODO: Check if block is interactive
        // TODO: Validate player has permission to interact
        // TODO: Execute interaction logic
        // TODO: Send success response if needed

        // For now, just log the interaction
        log.info("Interaction request: pos=({}, {}, {}), groupId={}, user={}, session={}",
                x, y, z, groupId, session.getTitle(), session.getSessionId());

        // Example: Send success response (in future, based on actual interaction result)
        // sendSuccessResponse(session, requestId);
    }

    /**
     * Send failure response to client.
     */
    private void sendFailureResponse(PlayerSession session, String requestId, int errorCode, String errorMessage) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode responseData = objectMapper.createObjectNode();
            responseData.put("success", false);
            responseData.put("errorCode", errorCode);
            responseData.put("errorMessage", errorMessage);

            NetworkMessage response = NetworkMessage.builder()
                    .r(requestId)
                    .t("int.rs")
                    .d(responseData)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            session.getWebSocketSession().sendMessage(new TextMessage(json));

            log.debug("Sent interaction failure response: requestId={}, errorCode={}", requestId, errorCode);

        } catch (Exception e) {
            log.error("Failed to send interaction failure response", e);
        }
    }

    /**
     * Send success response to client (currently unused, example for future).
     */
    @SuppressWarnings("unused")
    private void sendSuccessResponse(PlayerSession session, String requestId) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode responseData = objectMapper.createObjectNode();
            responseData.put("success", true);

            NetworkMessage response = NetworkMessage.builder()
                    .r(requestId)
                    .t("int.rs")
                    .d(responseData)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            session.getWebSocketSession().sendMessage(new TextMessage(json));

            log.debug("Sent interaction success response: requestId={}", requestId);

        } catch (Exception e) {
            log.error("Failed to send interaction success response", e);
        }
    }
}
