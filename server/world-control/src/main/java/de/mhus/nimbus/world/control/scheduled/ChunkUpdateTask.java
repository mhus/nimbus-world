package de.mhus.nimbus.world.control.scheduled;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.world.shared.edit.ChunkUpdateService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to process dirty chunks.
 * Runs at fixed intervals to regenerate chunks affected by layer changes.
 */
@Component
@ConditionalOnExpression("'WorldEditor'.equals('${spring.application.name}')")
@RequiredArgsConstructor
@Slf4j
public class ChunkUpdateTask {

    private final ChunkUpdateService chunkUpdateService;
    private final SSettingsService settingsService;

    private SettingInteger batchSize;

    @PostConstruct
    public void init() {
        batchSize = settingsService.getInteger(
                "control.chunk-update-batch-size",
                50
        );
        log.info("Chunk update task initialized");
    }

    /**
     * Scheduled task to process dirty chunks.
     * Runs at fixed intervals to regenerate chunks affected by layer changes.
     */
    @Scheduled(fixedDelayString = "#{${world.control.chunk-update-interval-ms:5000}}")
    public void processChunkUpdates() {
        try {
            int processed = chunkUpdateService.processDirtyChunks(batchSize.get());

            if (processed > 0) {
                log.info("Chunk update task: processed {} dirty chunks", processed);
            } else {
                log.trace("Chunk update task: no dirty chunks to process");
            }

        } catch (Exception e) {
            log.error("Error during chunk update task", e);
        }
    }
}
