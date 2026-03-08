package de.mhus.nimbus.world.shared.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Message format for block status broadcasts via Redis.
 * Used to broadcast block status changes (e.g., door open/closed) to all world-player pods.
 *
 * Channel: world:{worldId}:b.ps
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockStatusBroadcastMessage {

    private String worldId;

    /** Chunk key (e.g. "1:2") */
    private String chunkKey;

    /** Chunk X coordinate */
    private int cx;

    /** Chunk Z coordinate */
    private int cz;

    /** Block status entries: posKey ("x,y,z") -> status (null = removed) */
    private Map<String, String> statusEntries;
}
