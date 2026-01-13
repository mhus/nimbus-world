package de.mhus.nimbus.world.shared.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message format for block update broadcasts via Redis.
 * Used to broadcast block updates from world-control to all world-player pods.
 *
 * Channel: world:{worldId}:b.u
 *
 * This ensures both sender (world-control) and receiver (world-player) use the exact same message structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockUpdateBroadcastMessage {

    /**
     * Block data as JSON string.
     * Contains serialized Block object(s) - can be single block "{...}" or array "[{...},{...}]".
     */
    private String blockJson;

    /**
     * Target audience filter.
     * - "ALL": Send to all authenticated sessions in the world
     * - "EDITOR": Send only to sessions with actor=EDITOR
     */
    private String targetAudience;

    /**
     * Chunk coordinate for filtering (optional).
     * If provided, only sessions that have registered this chunk will receive the update.
     */
    private Integer cx;

    /**
     * Chunk coordinate for filtering (optional).
     * If provided, only sessions that have registered this chunk will receive the update.
     */
    private Integer cz;

    /**
     * Session ID that originated this update (optional).
     * Used to prevent echoing updates back to the originating session.
     * Can be null for server-side updates that should reach all sessions.
     */
    private String originatingSessionId;

    /**
     * World ID (redundant but useful for validation).
     */
    private String worldId;

    /**
     * Source identifier for the block update (optional).
     * Format: "layerDataId:layerName" or "modelId:modelName"
     * Used by modelselector to filter which blocks to display/update.
     * If null, all clients receive the update regardless of source filter.
     */
    private String source;

    /**
     * Target audience: ALL sessions.
     */
    public static final String AUDIENCE_ALL = "ALL";

    /**
     * Target audience: Only EDITOR sessions.
     */
    public static final String AUDIENCE_EDITOR = "EDITOR";
}
