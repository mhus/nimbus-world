package de.mhus.nimbus.world.player.ws;

import de.mhus.nimbus.generated.types.EntityStatusUpdate;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionClosedConsumer;
import de.mhus.nimbus.world.shared.redis.EntityStatusPublisher;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import static de.mhus.nimbus.world.shared.redis.EntityStatusPublisher.GONE;

/**
 * Service that broadcasts entity death status when a session is closed.
 *
 * Responsibilities:
 * - SessionClosedConsumer: Broadcast death status (death: 1) to last known chunk
 *
 * This ensures that all other sessions in the same chunk are notified that the entity
 * has disconnected and should be removed from their entity lists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityGoneBroadcastService implements SessionClosedConsumer {

    private final EntityStatusPublisher entityStatusPublisher;
    private final WWorldService worldService;

    /**
     * SessionClosedConsumer implementation.
     * Called when session is closed to broadcast death status.
     */
    @Override
    public void onSessionClosed(PlayerSession session) {
        if (session.getWorldId() == null || session.getEntityId() == null) {
            log.debug("Session closed without world or entity ID, skipping death broadcast");
            return;
        }

        if (session.getLastPosition() == null) {
            log.debug("Session closed without last position, skipping death broadcast for entity {}",
                session.getEntityId());
            return;
        }

        try {
            String worldId = session.getWorldId().getId();
            String entityId = session.getEntityId();
            var world = worldService.getByWorldId(worldId).get();

            // Calculate chunk from last known position
            double x = session.getLastPosition().getX();
            double z = session.getLastPosition().getZ();
            int cx = world.getChunkX(x);
            int cz = world.getChunkZ(z);

            // Create death status map
            Map<String, Object> goneStatus = Map.of(GONE, 1);

            // Broadcast death status to chunk
            entityStatusPublisher.publishStatusUpdateToChunk(
                worldId,
                entityId,
                goneStatus,
                cx,
                cz,
                session.getSessionId() // Originating session (will be filtered out, but that's OK)
            );

            log.info("Broadcasted death status for entity {} at chunk ({}, {}) in world {}",
                entityId, cx, cz, worldId);

        } catch (Exception e) {
            log.error("Failed to broadcast death status for session {}",
                session.getSessionId(), e);
        }
    }
}

