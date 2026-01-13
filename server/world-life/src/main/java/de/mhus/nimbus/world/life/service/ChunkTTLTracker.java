package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks timestamps for chunks to implement TTL (Time-To-Live) mechanism.
 * Chunks that haven't been updated within the TTL period are considered stale.
 */
@Service
@Slf4j
public class ChunkTTLTracker {

    /**
     * Maps chunk coordinates to their last update timestamp.
     * Thread-safe for concurrent access from multiple listeners and cleanup tasks.
     */
    private final Map<ChunkCoordinate, Instant> chunkTimestamps = new ConcurrentHashMap<>();

    /**
     * Update the timestamp for a chunk to current time.
     * Called when chunk is registered or refreshed.
     *
     * @param chunk Chunk coordinate to update
     */
    public void touch(ChunkCoordinate chunk) {
        if (chunk != null) {
            chunkTimestamps.put(chunk, Instant.now());
            log.trace("Updated timestamp for chunk: {}", chunk);
        }
    }

    /**
     * Get all chunks that have exceeded the TTL threshold.
     * Stale chunks are chunks whose last update is older than the TTL period.
     *
     * @param ttlMs TTL period in milliseconds
     * @return Set of stale chunk coordinates
     */
    public Set<ChunkCoordinate> getStaleChunks(long ttlMs) {
        Instant threshold = Instant.now().minusMillis(ttlMs);

        Set<ChunkCoordinate> staleChunks = chunkTimestamps.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(threshold))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        log.trace("Found {} stale chunks (TTL: {}ms)", staleChunks.size(), ttlMs);
        return staleChunks;
    }

    /**
     * Remove chunks from TTL tracking.
     * Called when chunks are explicitly unregistered or cleaned up.
     *
     * @param chunks Chunks to remove from tracking
     */
    public void removeChunks(Collection<ChunkCoordinate> chunks) {
        if (chunks != null && !chunks.isEmpty()) {
            chunks.forEach(chunkTimestamps::remove);
            log.trace("Removed {} chunks from TTL tracking", chunks.size());
        }
    }

    /**
     * Get the number of chunks currently being tracked.
     *
     * @return Count of tracked chunks
     */
    public int getTrackedChunkCount() {
        return chunkTimestamps.size();
    }

    /**
     * Clear all tracked chunks.
     * Used for testing or emergency cleanup.
     */
    public void clear() {
        chunkTimestamps.clear();
        log.debug("Cleared all chunk timestamps");
    }
}
