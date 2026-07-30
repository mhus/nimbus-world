package de.mhus.nimbus.world.player.ws.redis;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.ws.BlockStatusSenderService;
import de.mhus.nimbus.world.shared.redis.BlockStatusBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Redis listener for block status broadcasts.
 * Receives block status changes from any module and distributes to connected clients via WebSocket.
 *
 * Redis channel: world:*:b.ps
 * Client message type: "b.ps" (Block Progress Status)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockStatusBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;
    private final BlockStatusSenderService blockStatusSenderService;

    @PostConstruct
    public void subscribe() {
        redisMessaging.subscribeToAllWorlds("b.ps", this::handleBlockStatusUpdate);
        log.info("Subscribed to block status broadcasts on channel: world:*:b.ps");
    }

    private void handleBlockStatusUpdate(String topic, String message) {
        try {
            BlockStatusBroadcastMessage broadcast = objectMapper.readValue(message, BlockStatusBroadcastMessage.class);

            if (broadcast.getStatusEntries() == null || broadcast.getStatusEntries().isEmpty()) {
                return;
            }

            blockStatusSenderService.broadcastStatusUpdate(
                    broadcast.getWorldId(),
                    broadcast.getCx(),
                    broadcast.getCz(),
                    broadcast.getStatusEntries()
            );

            log.debug("Processed block status broadcast: worldId={}, chunk=({},{}), entries={}",
                    broadcast.getWorldId(), broadcast.getCx(), broadcast.getCz(),
                    broadcast.getStatusEntries().size());

        } catch (Exception e) {
            log.error("Failed to process block status broadcast: topic={}", topic, e);
        }
    }
}
