package de.mhus.nimbus.shared.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Scheduled task for cleaning up soft-deleted storage data.
 *
 * Runs periodically (default: every 15 minutes) to process StorageDelete entries.
 * Deletes all StorageData chunks for entries where deletedAt <= current time,
 * then removes the StorageDelete entries themselves.
 *
 * This delayed cleanup strategy ensures that ongoing read operations can complete
 * safely before data is permanently removed.
 */
@Component
@ConditionalOnExpression("'WorldEditor'.equals('${spring.application.name}')")
@RequiredArgsConstructor
@Slf4j
public class StorageCleanupScheduler {

    private final StorageDataRepository storageDataRepository;
    private final StorageDeleteRepository storageDeleteRepository;

    /**
     * Cleanup scheduled deletions.
     * Default interval: every 15 minutes (900000ms).
     * Configurable via nimbus.storage.cleanup-interval-ms property.
     */
    @Scheduled(fixedDelayString = "#{${nimbus.storage.cleanup-interval-ms:900000}}")
    @Transactional
    public void cleanupDeletedStorage() {
        log.debug("Starting storage cleanup task");

        try {
            Date now = new Date();
            List<StorageDelete> toDelete = storageDeleteRepository
                    .findByDeletedAtLessThanEqual(now);

            if (toDelete.isEmpty()) {
                log.debug("No storage deletions scheduled");
                return;
            }

            int deletedCount = 0;
            int errorCount = 0;

            for (StorageDelete entry : toDelete) {
                try {
                    String storageId = entry.getStorageId();
                    long chunkCount = storageDataRepository.countByUuid(storageId);

                    // Delete all chunks for this UUID
                    storageDataRepository.deleteByUuid(storageId);

                    // Remove the deletion entry
                    storageDeleteRepository.delete(entry);

                    deletedCount++;
                    log.debug("Deleted storage: storageId={} chunks={}", storageId, chunkCount);

                } catch (Exception e) {
                    errorCount++;
                    log.error("Error deleting storage entry: id={}", entry.getId(), e);
                    // Continue with next entry - will retry on next scheduled run
                }
            }

            log.info("Storage cleanup completed: deleted={} errors={} total={}",
                    deletedCount, errorCount, toDelete.size());

        } catch (Exception e) {
            log.error("Error during storage cleanup task", e);
        }
    }
}
