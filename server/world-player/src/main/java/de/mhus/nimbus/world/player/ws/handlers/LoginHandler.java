package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.network.ClientType;
import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.service.PlayerService;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

/**
 * Handles login messages from clients.
 * Message type: "login"
 *
 * Supports:
 * - username/password authentication
 * - token authentication
 * - session resumption with sessionId
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final WWorldService worldService;
    private final PlayerService playerService;
    private final WSessionService wSessionService;

    @Value("${world.development.enabled:false}")
    private boolean applicationDevelopmentEnabled;

    @Override
    public String getMessageType() {
        return "login";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        JsonNode data = message.getD();

        String clientTypeStr = data.has("clientType") ? data.get("clientType").asText() : "web";
        String existingSessionId = data.has("sessionId") ? data.get("sessionId").asText() : null;

        log.info("Login attempt: existingSessionId={}", existingSessionId);

        var wSessionOpt = wSessionService.get(existingSessionId);
        if (wSessionOpt.isEmpty()) {
            log.debug("Invalid or expired session ID: {}, login failed", existingSessionId);
            sendLoginResponse(session, message.getI(), false, "Invalid or expired session ID", null, null);
            return;
        }
        var wSession = wSessionOpt.get();
        var worldIdOpt = WorldId.of(wSession.getWorldId());
        var playerId = PlayerId.of(wSession.getPlayerId());
        var actor = wSession.getActor();

        var webClientType = ClientType.valueOf(clientTypeStr.trim().toUpperCase());

        if (playerId.isEmpty()) {
            log.warn("Invalid player ID, login failed");
            sendLoginResponse(session, message.getI(), false, "Invalid player", null, null);
            return;
        }
        var player = playerService.getPlayer(playerId.get(), webClientType, worldIdOpt.get().getRegionId());
        if (player.isEmpty()) {
            log.warn("Player not found: {}, login failed", playerId.get());
            sendLoginResponse(session, message.getI(), false, "Player not found", null, null);
            return;
        }

        var worldOpt = worldService.getByWorldId(worldIdOpt.get());
        if (worldOpt.isEmpty()) {
            log.warn("World not found: {}, login failed", worldIdOpt.get());
            sendLoginResponse(session, message.getI(), false, "World not found", null, null);
            return;
        }
        var world = worldOpt.get();

        sessionManager.authenticateSession(session, existingSessionId, worldIdOpt.get(), player.get(), webClientType, actor);

        if (!session.isAuthenticated()) {
            sendLoginResponse(session, message.getI(), false, "Invalid credentials", null, null);
            return;
        }

        // Use the actual session ID (may have changed for username/password login)
        String actualSessionId = session.getSessionId();

        // Send success response with world data
        sendLoginResponse(session, message.getI(), true, null, actualSessionId, world);

        log.info("Login successful: user={}, sessionId={}, worldId={}",
                playerId.get(), actualSessionId, worldIdOpt.get());
    }

    private void sendLoginResponse(PlayerSession session, String requestId, boolean success,
                                   String errorMessage, String sessionId, WWorld world) throws Exception {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("success", success);

        if (success) {
            data.put("userId", session.getPlayer().user().getUserId());
            data.put("title", session.getTitle());
            data.put("sessionId", sessionId);

            // Use world data passed from caller
            if (world != null && world.getPublicData() != null) {
                WorldInfo worldInfo = world.getPublicData();

                // Convert WorldInfo to JSON and add settings
                ObjectNode worldInfoNode = objectMapper.valueToTree(worldInfo);

                // Add settings object with pingInterval (not part of WorldInfo)
                ObjectNode settings = objectMapper.createObjectNode();
                settings.put("pingInterval", session.getPingInterval());
                settings.put("maxPlayers", 100);
                settings.put("allowGuests", true);
                worldInfoNode.set("settings", settings);

                data.set("worldInfo", worldInfoNode);
            }
        } else {
            data.put("errorCode", 401);
            data.put("errorMessage", errorMessage != null ? errorMessage : "Authentication failed");
        }

        NetworkMessage response = NetworkMessage.builder()
                .r(requestId)
                .t("loginResponse")
                .d(data)
                .build();

        String json = objectMapper.writeValueAsString(response);
        session.getWebSocketSession().sendMessage(new TextMessage(json));
    }
}
