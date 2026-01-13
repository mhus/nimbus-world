package de.mhus.nimbus.world.shared.redis;

import de.mhus.nimbus.generated.types.EntityPathway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Message format for entity pathway broadcasts via Redis.
 * Used by world-life to publish pathways and world-player to receive them.
 *
 * Channel: world:{worldId}:e.p
 *
 * This ensures both sender and receiver use the exact same message structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PathwayBroadcastMessage {

    /**
     * List of pathway containers with metadata.
     */
    private List<PathwayContainer> containers;

    /**
     * Chunks affected by these pathways.
     * Used for efficient filtering on the receiver side.
     */
    private List<ChunkCoordinate> affectedChunks;

    /**
     * Container for a single pathway with metadata.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PathwayContainer {

        /**
         * The entity pathway.
         */
        private EntityPathway pathway;

        /**
         * Session ID that originated this pathway (null for NPC/AI pathways).
         * Used to prevent echoing pathways back to the originating session.
         */
        private String sessionId;

        /**
         * World ID (redundant but useful for validation).
         */
        private String worldId;
    }

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
