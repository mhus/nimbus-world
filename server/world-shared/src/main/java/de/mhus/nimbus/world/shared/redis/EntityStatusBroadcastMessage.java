package de.mhus.nimbus.world.shared.redis;

import de.mhus.nimbus.generated.types.EntityStatusUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Message format for entity status broadcasts via Redis.
 * Used to broadcast entity status updates (dynamic status fields) to all world-player pods.
 *
 * Channel: world:{worldId}:e.s.u
 *
 * Status fields are dynamic and not predefined - they can be any key-value pairs.
 * Examples: {health, healthMax, death}, {mana, stamina}, etc.
 *
 * This ensures both sender and receiver use the exact same message structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityStatusBroadcastMessage {

    /**
     * List of entity status updates.
     */
    private List<EntityStatusUpdate> statusUpdates;

    /**
     * Chunks affected by these status updates (for filtering).
     * Used for efficient filtering on the receiver side.
     */
    private List<ChunkCoordinate> affectedChunks;

    /**
     * Session ID that originated this update (null for server-side updates).
     * Used to prevent echoing updates back to the originating session.
     */
    private String originatingSessionId;

    /**
     * World ID (redundant but useful for validation).
     */
    private String worldId;

    /**
     * Chunk coordinate for filtering.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkCoordinate {

        /**
         * Chunk X coordinate.
         */
        private int cx;

        /**
         * Chunk Z coordinate.
         */
        private int cz;
    }
}
