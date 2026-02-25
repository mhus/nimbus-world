package de.mhus.nimbus.world.life.scheduled;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.config.WorldLifeSettings;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import de.mhus.nimbus.world.life.service.ChunkTTLTracker;
import de.mhus.nimbus.world.life.service.MultiWorldChunkService;
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
 * Iterates over all tracked worlds via MultiWorldChunkService.
 * This ensures that chunks from disconnected sessions or dead pods
 * are eventually removed from the active chunk set.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkTTLCleanupTask {

    private final MultiWorldChunkService multiWorldChunkService;
    private final WorldLifeSettings properties;

    /**
     * Clean up chunks that have exceeded the TTL threshold.
     * Runs at fixed intervals to prevent stale chunks from accumulating.
     */
    @Scheduled(fixedDelayString = "#{${world.life.chunk-ttl-cleanup-interval-ms:60000}}")
    public void cleanupStaleChunks() {
        try {
            long ttlMs = properties.getChunkTtlMs();

            for (String worldIdStr : multiWorldChunkService.getTrackedWorldIds()) {
                WorldId worldId = WorldId.unchecked(worldIdStr);
                var aliveService = multiWorldChunkService.getChunkAliveService(worldId);
                ChunkTTLTracker ttlTracker = multiWorldChunkService.getTTLTracker(worldId);

                Set<ChunkCoordinate> staleChunks = ttlTracker.getStaleChunks(ttlMs);

                if (!staleChunks.isEmpty()) {
                    aliveService.removeChunks(new ArrayList<>(staleChunks));
                    ttlTracker.removeChunks(staleChunks);

                    log.info("World {}: TTL cleanup removed {} stale chunks (TTL: {}ms), {} active remain",
                            worldId, staleChunks.size(), ttlMs, aliveService.getActiveChunkCount());
                } else {
                    log.trace("World {}: TTL cleanup: no stale chunks, {} active",
                            worldId, aliveService.getActiveChunkCount());
                }
            }

        } catch (Exception e) {
            log.error("Error during chunk TTL cleanup", e);
        }
    }
}
