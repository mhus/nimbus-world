package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.job.WJobService;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Job executor that waits until all DirtyChunk entries for a world have been cleared.
 *
 * If no dirty chunks exist at start, completes immediately.
 * Otherwise, returns async and polls until all dirty chunks are gone.
 * The poll interval scales with the number of remaining dirty chunks:
 * 1-10: 10s, 11-100: 30s, 101-500: 60s, 500+: 120s.
 *
 * WorldId is taken from job.getWorldId().
 *
 * Optional parameters:
 * - timeoutMinutes: Maximum wait time in minutes (default: 60)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WaitForDirtyChunksJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "wait-for-dirty-chunks";
    private static final int DEFAULT_TIMEOUT_MINUTES = 60;

    private final WDirtyChunkService dirtyChunkService;
    private final WJobService jobService;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        String worldId = job.getWorldId();
        int timeoutMinutes = getOptionalIntParameter(job, "timeoutMinutes", DEFAULT_TIMEOUT_MINUTES);

        long dirtyCount = dirtyChunkService.countDirtyChunks(worldId);

        if (dirtyCount == 0) {
            log.info("No dirty chunks found for world={}, completing immediately", worldId);
            return JobResult.success("No dirty chunks found");
        }

        log.info("Found {} dirty chunks for world={}, waiting (timeout={}min)", dirtyCount, worldId, timeoutMinutes);

        String jobId = job.getId();
        virtualThreadExecutor.execute(() -> pollUntilClean(jobId, worldId, timeoutMinutes));

        return JobResult.async("Waiting for " + dirtyCount + " dirty chunks to be cleared");
    }

    private void pollUntilClean(String jobId, String worldId, int timeoutMinutes) {
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        long startTime = System.currentTimeMillis();

        try {
            while (true) {
                long dirtyCount = dirtyChunkService.countDirtyChunks(worldId);
                if (dirtyCount == 0) {
                    long durationSec = (System.currentTimeMillis() - startTime) / 1000;
                    String result = String.format("All dirty chunks cleared for world=%s after %ds", worldId, durationSec);
                    log.info(result);
                    jobService.markJobCompleted(jobId, result);
                    return;
                }

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > timeoutMs) {
                    String errorMsg = String.format(
                            "Timeout after %d minutes waiting for dirty chunks. Still %d dirty chunks remaining for world=%s",
                            timeoutMinutes, dirtyCount, worldId);
                    log.error(errorMsg);
                    jobService.markJobFailed(jobId, errorMsg);
                    return;
                }

                long pollInterval = calculatePollInterval(dirtyCount);
                log.debug("Still {} dirty chunks for world={}, elapsed={}s, next poll in {}s",
                        dirtyCount, worldId, elapsed / 1000, pollInterval / 1000);
                Thread.sleep(pollInterval);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jobService.markJobFailed(jobId, "Interrupted while waiting for dirty chunks");
        } catch (Exception e) {
            log.error("Error while waiting for dirty chunks: world={}", worldId, e);
            jobService.markJobFailed(jobId, "Error: " + e.getMessage());
        }
    }

    /**
     * Scale poll interval based on remaining dirty chunk count.
     * More chunks = longer interval since it will obviously take more time.
     */
    private long calculatePollInterval(long dirtyCount) {
        if (dirtyCount <= 10) return 10_000;   // 10s
        if (dirtyCount <= 100) return 30_000;   // 30s
        if (dirtyCount <= 500) return 60_000;   // 60s
        return 120_000;                          // 120s
    }

    private int getOptionalIntParameter(WJob job, String paramName, int defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}", paramName, value, defaultValue);
            return defaultValue;
        }
    }

    @PreDestroy
    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }
}
