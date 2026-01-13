package de.mhus.nimbus.world.shared.job;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingBoolean;
import de.mhus.nimbus.shared.settings.SettingInteger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Job System.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class JobSettings {

    private final SSettingsService settingsService;

    private SettingBoolean processingEnabled;
    private SettingInteger processingIntervalMs;
    private SettingInteger maxJobsPerCycle;
    private SettingBoolean cleanupEnabled;
    private SettingInteger cleanupIntervalMs;
    private SettingInteger retentionHours;
    private SettingBoolean hardDelete;

    @PostConstruct
    private void init() {
        processingEnabled = settingsService.getBoolean(
                "job.processingEnabled",
                true
        );
        processingIntervalMs = settingsService.getInteger(
                "job.processingIntervalMs",
                5000
        );
        maxJobsPerCycle = settingsService.getInteger(
                "job.maxJobsPerCycle",
                10
        );
        cleanupEnabled = settingsService.getBoolean(
                "job.cleanupEnabled",
                true
        );
        cleanupIntervalMs = settingsService.getInteger(
                "job.cleanupIntervalMs",
                3600000
        );
        retentionHours = settingsService.getInteger(
                "job.retentionHours",
                24
        );
        hardDelete = settingsService.getBoolean(
                "job.hardDelete",
                false
        );
    }

    /**
     * Enable/disable job processing scheduler.
     * Default: true
     */
    public boolean isProcessingEnabled() {
        return processingEnabled.get();
    }

    /**
     * Job processing interval in milliseconds.
     * Default: 5000ms (5 seconds)
     */
    public long getProcessingIntervalMs() {
        return processingIntervalMs.get();
    }

    /**
     * Maximum jobs to process per scheduler cycle.
     * Prevents overload if many jobs are pending.
     * Default: 10
     */
    public int getMaxJobsPerCycle() {
        return maxJobsPerCycle.get();
    }

    /**
     * Enable/disable job cleanup scheduler.
     * Default: true
     */
    public boolean isCleanupEnabled() {
        return cleanupEnabled.get();
    }

    /**
     * Job cleanup interval in milliseconds.
     * Default: 3600000ms (1 hour)
     */
    public long getCleanupIntervalMs() {
        return cleanupIntervalMs.get();
    }

    /**
     * Retention time for completed/failed jobs in hours.
     * Jobs older than this are deleted by cleanup scheduler.
     * Default: 24 hours
     */
    public long getRetentionHours() {
        return retentionHours.get();
    }

    /**
     * Use hard delete (remove from DB) vs soft delete (set enabled=false).
     * Default: false (soft delete)
     */
    public boolean isHardDelete() {
        return hardDelete.get();
    }
}
