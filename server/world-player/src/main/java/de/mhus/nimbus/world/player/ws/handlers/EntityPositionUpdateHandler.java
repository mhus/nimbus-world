package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.types.ENTITY_POSES;
import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles entity position update messages from clients.
 * Message type: "e.p.u" (Entity Position Update, Client → Server)
 *
 * This handler updates the PlayerSession state with the latest position data.
 * Broadcasting is handled by PathwayBroadcastService (@Scheduled task) to throttle updates.
 *
 * Expected data (array of updates):
 * [
 *   {
 *     "pl": "player",  // local entity id (not unique id)
 *     "p": {"x": 10.5, "y": 64.0, "z": 20.3},  // position (optional)
 *     "r": {"y": 90.0, "p": 0.0},  // rotation: yaw, pitch (optional)
 *     "v": {"x": 0.1, "y": 0, "z": 0.2},  // velocity (optional)
 *     "po": 1,  // pose id (optional)
 *     "ts": 1697045600000,  // timestamp
 *     "ta": {"x": 10.5, "y": 64.0, "z": 20.3, "ts": 1697045800000}  // target position (optional)
 *   }
 * ]
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntityPositionUpdateHandler implements MessageHandler {

    private final EngineMapper engineMapper;
    private final WSessionService wSessionService;

    @Override
    public String getMessageType() {
        return "e.p.u";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Entity position update from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        // Data is an array of entity updates
        if (!data.isArray()) {
            log.warn("Entity position update data is not an array");
            return;
        }

        // Process each entity update
        for (JsonNode entityUpdate : data) {
            processEntityUpdate(session, entityUpdate);
        }
    }

    private void processEntityUpdate(PlayerSession session, JsonNode update) {
        try {
            // Extract compressed field names
            String playerId = update.has("pl") ? update.get("pl").asText() : null;
            JsonNode posNode = update.has("p") ? update.get("p") : null;
            JsonNode rotNode = update.has("r") ? update.get("r") : null;
            JsonNode velNode = update.has("v") ? update.get("v") : null;
            Integer poseId = update.has("po") ? update.get("po").asInt() : null;
            Long timestamp = update.has("ts") ? update.get("ts").asLong() : null;

            // Only process player entity (not other entities)
            if (playerId == null || !playerId.equals("player")) {
                return;
            }

            // Convert to typed objects
            Vector3 position = posNode != null ?
                    engineMapper.treeToValue(posNode, Vector3.class) : null;
            Rotation rotation = rotNode != null ?
                    engineMapper.treeToValue(rotNode, Rotation.class) : null;
            Vector3 velocity = velNode != null ?
                    engineMapper.treeToValue(velNode, Vector3.class) : null;
            ENTITY_POSES pose = poseId != null && poseId >= 0 && poseId < ENTITY_POSES.values().length ?
                    ENTITY_POSES.values()[poseId] : null;

            // Update session state (no immediate broadcasting)
            session.updatePosition(position, rotation, velocity, pose);

            // Store position in Redis for access by other services (e.g., world-generator)
            if (position != null) {
                try {
                    Double x = position.getX();
                    Double y = position.getY();
                    Double z = position.getZ();
                    Integer cx = session.getCurrentChunkX();
                    Integer cz = session.getCurrentChunkZ();
                    Double yaw = rotation != null ? rotation.getY() : null;
                    Double pitch = rotation != null ? rotation.getP() : null;

                    wSessionService.updatePosition(session.getSessionId(), x, y, z, cx, cz, yaw, pitch);

                    log.debug("Stored position in Redis: sessionId={}, pos=({}, {}, {}), chunk=({}, {})",
                        session.getSessionId(), x, y, z, cx, cz);
                } catch (Exception e) {
                    log.error("Failed to store position in Redis for session {}", session.getSessionId(), e);
                }
            }

            // Throttling is handled by PathwayBroadcastService (scheduled task)

            log.trace("Updated position for session {}: pos={}, chunk=({}, {})",
                    session.getSessionId(),
                    position != null ? String.format("%.2f,%.2f,%.2f",
                            position.getX(), position.getY(), position.getZ()) : "null",
                    session.getCurrentChunkX(),
                    session.getCurrentChunkZ());

        } catch (Exception e) {
            log.error("Failed to process entity update", e);
        }
    }
}
