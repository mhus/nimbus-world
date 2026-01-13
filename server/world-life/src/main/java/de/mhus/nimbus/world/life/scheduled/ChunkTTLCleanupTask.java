package de.mhus.nimbus.world.life.scheduled;

import de.mhus.nimbus.world.life.config.WorldLifeSettings;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import de.mhus.nimbus.world.life.service.ChunkAliveService;
import de.mhus.nimbus.world.life.service.ChunkTTLTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Set;

/**
 * Scheduled task that removes chunks with expired TTL.
 * Runs periodically (default: every 60 seconds) to clean up chunks
 * that haven't received updates within the TTL period (default: 5 minutes).
 *
 * This ensures that chunks from disconnected sessions or dead pods
 * are eventually removed from the active chunk set.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkTTLCleanupTask {

    private final ChunkAliveService chunkAliveService;
    private final ChunkTTLTracker ttlTracker;
    private final WorldLifeSettings properties;

    /**
     * Clean up chunks that have exceeded the TTL threshold.
     * Runs at fixed intervals to prevent stale chunks from accumulating.
     */
    @Scheduled(fixedDelayString = "#{${world.life.chunk-ttl-cleanup-interval-ms:60000}}")
    public void cleanupStaleChunks() {
        try {
            long ttlMs = properties.getChunkTtlMs();

            // Find chunks that haven't been updated within TTL period
            Set<ChunkCoordinate> staleChunks = ttlTracker.getStaleChunks(ttlMs);

            if (!staleChunks.isEmpty()) {
                // Remove from active chunk service
                chunkAliveService.removeChunks(new ArrayList<>(staleChunks));

                // Remove from TTL tracking
                ttlTracker.removeChunks(staleChunks);

                log.info("TTL cleanup: removed {} stale chunks (TTL: {}ms), {} active chunks remain",
                        staleChunks.size(), ttlMs, chunkAliveService.getActiveChunkCount());
            } else {
                log.trace("TTL cleanup: no stale chunks found, {} active chunks",
                        chunkAliveService.getActiveChunkCount());
            }

        } catch (Exception e) {
            log.error("Error during chunk TTL cleanup", e);
        }
    }
}
