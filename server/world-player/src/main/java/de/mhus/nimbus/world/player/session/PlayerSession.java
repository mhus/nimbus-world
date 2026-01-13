package de.mhus.nimbus.world.player.session;

import de.mhus.nimbus.generated.network.ClientType;
import de.mhus.nimbus.generated.types.ENTITY_POSES;
import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.PlayerData;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Stateful player session for WebSocket connection.
 * Maintains connection state, authentication, and chunk subscriptions.
 */
@Data
public class PlayerSession {

    private final WebSocketSession webSocketSession;

    private String sessionId;
    private PlayerData player;
    private String title;
    private WorldId worldId;
    private ClientType clientType;
    private String actor;

    private SessionStatus status = SessionStatus.CONNECTED;

    private Instant connectedAt;
    private Instant lastPingAt;
    private Instant authenticatedAt;


    public boolean isAuthenticated() {
        return status == SessionStatus.AUTHENTICATED;
    }

    /**
     * Check if session has EDITOR actor.
     * EDITOR sessions receive WEditCache overlays in chunks.
     */
    public boolean isEditActor() {
        return "EDITOR".equals(actor);
    }

    /**
     * Registered chunks (cx, cz coordinates as "cx:cz" format).
     * Client receives updates only for these chunks.
     */
    private final Set<String> registeredChunks = new HashSet<>();

    /**
     * Ping interval in seconds (from world settings).
     */
    private int pingInterval = 30;

    /**
     * Entity position tracking for pathway generation.
     */
    private Vector3 lastPosition;
    private Rotation lastRotation;
    private Vector3 lastVelocity;
    private ENTITY_POSES lastPose;
    private Instant lastPositionUpdateAt;
    private boolean positionChanged;
    private Integer currentChunkX;
    private Integer currentChunkZ;

    public PlayerSession(WebSocketSession webSocketSession) {
        this.webSocketSession = webSocketSession;
        this.connectedAt = Instant.now();
        this.lastPingAt = Instant.now();
    }


    /**
     * Check if session is still alive based on ping timeout.
     * deadline = lastPingAt + pingInterval*1000 + 10000 (10s buffer)
     */
    public boolean isAlive() {
        if (lastPingAt == null) return false;
        long deadlineMs = lastPingAt.toEpochMilli() + (pingInterval * 1000L) + 10000L;
        return Instant.now().toEpochMilli() < deadlineMs;
    }

    /**
     * Update last ping timestamp.
     */
    public void touch() {
        this.lastPingAt = Instant.now();
    }

    /**
     * Register chunk for updates.
     * @param cx chunk x coordinate
     * @param cz chunk z coordinate
     */
    public void registerChunk(int cx, int cz) {
        registeredChunks.add(chunkKey(cx, cz));
    }

    /**
     * Unregister chunk.
     * @param cx chunk x coordinate
     * @param cz chunk z coordinate
     */
    public void unregisterChunk(int cx, int cz) {
        registeredChunks.remove(chunkKey(cx, cz));
    }

    /**
     * Clear all registered chunks.
     */
    public void clearChunks() {
        registeredChunks.clear();
    }

    /**
     * Check if chunk is registered.
     */
    public boolean isChunkRegistered(int cx, int cz) {
        return registeredChunks.contains(chunkKey(cx, cz));
    }

    private String chunkKey(int cx, int cz) {
        return cx + ":" + cz;
    }

    /**
     * Update entity position tracking.
     * Detects ANY changes (position, rotation, velocity, pose) and updates chunk coordinates.
     *
     * @param position new position (can be null)
     * @param rotation new rotation (can be null)
     * @param velocity new velocity (can be null)
     * @param pose new pose (can be null)
     */
    public void updatePosition(Vector3 position, Rotation rotation, Vector3 velocity, ENTITY_POSES pose) {
        // Detect ANY change (position, rotation, velocity, pose)
        boolean changed = this.lastPosition == null ||
                         (position != null && !positionsEqual(this.lastPosition, position)) ||
                         (rotation != null && !rotationsEqual(this.lastRotation, rotation)) ||
                         (velocity != null && !velocitiesEqual(this.lastVelocity, velocity)) ||
                         (pose != null && !pose.equals(this.lastPose));

        this.positionChanged = changed;
        this.lastPosition = position;
        this.lastRotation = rotation;
        this.lastVelocity = velocity;
        this.lastPose = pose;
        this.lastPositionUpdateAt = Instant.now();

        // Update chunk coordinates
        if (position != null) {
            this.currentChunkX = (int) Math.floor(position.getX() / 16);
            this.currentChunkZ = (int) Math.floor(position.getZ() / 16);
        }
    }

    /**
     * Check if position update is stale (older than timeout).
     *
     * @param timeoutMs timeout in milliseconds
     * @return true if update is stale or no update exists
     */
    public boolean isUpdateStale(long timeoutMs) {
        if (lastPositionUpdateAt == null) return true;
        return Instant.now().toEpochMilli() - lastPositionUpdateAt.toEpochMilli() > timeoutMs;
    }

    /**
     * Get entity ID in PlayerId format: "@userId:characterId"
     *
     * @return entity ID or null if player not set
     */
    public String getEntityId() {
        if (player == null || player.character() == null || player.character().getPublicData() == null) {
            return null;
        }
        // PlayerId is stored in character.publicData.playerId
        String playerId = player.character().getPublicData().getPlayerId();
        if (playerId == null) {
            return null;
        }

        // Ensure @ prefix is present
        if (!playerId.startsWith("@")) {
            return "@" + playerId;
        }
        return playerId;
    }

    /**
     * Compare two positions with tolerance.
     *
     * @param a first position
     * @param b second position
     * @return true if positions are equal within tolerance
     */
    private boolean positionsEqual(Vector3 a, Vector3 b) {
        if (a == null || b == null) return false;
        double threshold = 0.001; // 1mm tolerance
        return Math.abs(a.getX() - b.getX()) < threshold &&
               Math.abs(a.getY() - b.getY()) < threshold &&
               Math.abs(a.getZ() - b.getZ()) < threshold;
    }

    /**
     * Compare two rotations with tolerance.
     *
     * @param a first rotation
     * @param b second rotation
     * @return true if rotations are equal within tolerance
     */
    private boolean rotationsEqual(Rotation a, Rotation b) {
        if (a == null || b == null) return false;
        double threshold = 0.01; // ~0.6 degree tolerance
        return Math.abs(a.getY() - b.getY()) < threshold &&
               Math.abs(a.getP() - b.getP()) < threshold;
    }

    /**
     * Compare two velocities with tolerance.
     *
     * @param a first velocity
     * @param b second velocity
     * @return true if velocities are equal within tolerance
     */
    private boolean velocitiesEqual(Vector3 a, Vector3 b) {
        if (a == null || b == null) return false;
        double threshold = 0.001; // 1mm/s tolerance
        return Math.abs(a.getX() - b.getX()) < threshold &&
               Math.abs(a.getY() - b.getY()) < threshold &&
               Math.abs(a.getZ() - b.getZ()) < threshold;
    }

    public enum SessionStatus {
        CONNECTED,      // Connection established, not yet authenticated
        AUTHENTICATED,  // Successfully authenticated
        DEPRECATED,     // Connection lost or closing
        CLOSED          // Connection closed
    }
}
