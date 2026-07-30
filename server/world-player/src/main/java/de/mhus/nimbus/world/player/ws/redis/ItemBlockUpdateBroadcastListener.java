package de.mhus.nimbus.world.player.ws.redis;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.ItemBlockUpdateBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

/**
 * Redis listener for item block update broadcasts.
 * Receives item changes from any module and distributes to connected clients via WebSocket.
 *
 * Redis channel: world:*:b.iu
 * Client message type: "b.iu" (Item Block Update)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemBlockUpdateBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;

    @PostConstruct
    public void subscribe() {
        redisMessaging.subscribeToAllWorlds("b.iu", this::handleItemBlockUpdate);
        log.info("Subscribed to item block update broadcasts on channel: world:*:b.iu");
    }

    private void handleItemBlockUpdate(String topic, String message) {
        try {
            ItemBlockUpdateBroadcastMessage broadcast = objectMapper.readValue(message, ItemBlockUpdateBroadcastMessage.class);

            if (broadcast.getItems() == null || broadcast.getItems().isEmpty()) {
                return;
            }

            // Build b.iu message: data is ItemBlockRef[]
            NetworkMessage networkMessage = NetworkMessage.builder()
                    .t("b.iu")
                    .d(objectMapper.valueToTree(broadcast.getItems()))
                    .build();

            String json = objectMapper.writeValueAsString(networkMessage);
            TextMessage textMessage = new TextMessage(json);

            int sentCount = 0;
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                if (session.isAuthenticated()
                        && session.getWorldId() != null
                        && broadcast.getWorldId().equals(session.getWorldId().getId())
                        && session.isChunkRegistered(broadcast.getCx(), broadcast.getCz())) {

                    session.sendMessage(textMessage);
                    sentCount++;
                }
            }

            log.debug("Broadcast item block update to {} sessions: worldId={}, chunk=({},{}), items={}",
                    sentCount, broadcast.getWorldId(), broadcast.getCx(), broadcast.getCz(),
                    broadcast.getItems().size());

        } catch (Exception e) {
            log.error("Failed to process item block update broadcast: topic={}", topic, e);
        }
    }
}
