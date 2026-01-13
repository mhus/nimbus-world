package de.mhus.nimbus.world.life.scheduled;

import de.mhus.nimbus.world.life.service.EntityOwnershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task to detect and claim orphaned entities.
 *
 * Orphaned entities are those whose owning pod has stopped sending heartbeats
 * (likely crashed or disconnected). This task finds such entities and attempts
 * to claim them for simulation by this pod.
 *
 * Runs every 30 seconds (configurable via world.life.orphan-detection-interval-ms).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrphanDetectionTask {

    private final EntityOwnershipService ownershipService;

    /**
     * Detect orphaned entities and attempt to claim them.
     * Only logs findings - actual claiming is handled by SimulatorService
     * when it encounters orphaned entities in active chunks.
     */
    @Scheduled(fixedDelayString = "#{${world.life.orphan-detection-interval-ms:30000}}")
    public void detectOrphans() {
        try {
            List<String> orphans = ownershipService.getOrphanedEntities();

            if (!orphans.isEmpty()) {
                log.info("Detected {} orphaned entities (stale ownership)", orphans.size());

                // Log sample orphaned entity IDs (max 5 for readability)
                int sampleSize = Math.min(orphans.size(), 5);
                log.debug("Sample orphaned entities: {}{}",
                        orphans.subList(0, sampleSize),
                        orphans.size() > sampleSize ? " ..." : "");
            }

            // Note: Actual claiming happens in SimulatorService.simulationLoop()
            // Only entities in active chunks will be claimed

        } catch (Exception e) {
            log.error("Error during orphan detection", e);
        }
    }
}
