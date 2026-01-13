package de.mhus.nimbus.world.shared.session;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingBoolean;
import de.mhus.nimbus.shared.settings.SettingInteger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Konfiguration der Session Lebensdauern.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class WorldSettings {

    private final SSettingsService settingsService;

    private SettingInteger waitingMinutes;
    private SettingInteger runningHours;
    private SettingInteger deprecatedMinutes;
    private SettingInteger cleanupScanCount;
    private SettingInteger cleanupMaxDeletes;
    private SettingBoolean cleanupEnabled;
    private SettingInteger cleanupIntervalSeconds;

    @PostConstruct
    private void init() {
        waitingMinutes = settingsService.getInteger(
                "session.waitingMinutes",
                5
        );
        runningHours = settingsService.getInteger(
                "session.runningHours",
                12
        );
        deprecatedMinutes = settingsService.getInteger(
                "session.deprecatedMinutes",
                30
        );
        cleanupScanCount = settingsService.getInteger(
                "session.cleanupScanCount",
                500
        );
        cleanupMaxDeletes = settingsService.getInteger(
                "session.cleanupMaxDeletes",
                1000
        );
        cleanupEnabled = settingsService.getBoolean(
                "session.cleanupEnabled",
                true
        );
        cleanupIntervalSeconds = settingsService.getInteger(
                "session.cleanupIntervalSeconds",
                60
        );
    }

    /**
     * Ablaufdauer für neue Sessions im Status WAITING (Minuten).
     * Default: 5
     */
    public long getWaitingMinutes() {
        return waitingMinutes.get();
    }

    /**
     * Ablaufdauer nach Wechsel in Status RUNNING (Stunden).
     * Default: 12
     */
    public long getRunningHours() {
        return runningHours.get();
    }

    /**
     * Ablaufdauer nach Wechsel in Status DEPRECATED (Minuten).
     * Default: 30
     */
    public long getDeprecatedMinutes() {
        return deprecatedMinutes.get();
    }

    /**
     * Anzahl Keys pro SCAN Happen beim Aufräumen.
     * Default: 500
     */
    public int getCleanupScanCount() {
        return cleanupScanCount.get();
    }

    /**
     * Maximale Anzahl Löschungen pro Cleanup-Aufruf.
     * Default: 1000
     */
    public int getCleanupMaxDeletes() {
        return cleanupMaxDeletes.get();
    }

    /**
     * Cleanup aktivieren (wird nur genutzt wenn aufrufender Code Methode nutzt).
     * Default: true
     */
    public boolean isCleanupEnabled() {
        return cleanupEnabled.get();
    }

    /**
     * Intervall für Scheduler (fixed delay).
     * Default: 60
     */
    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds.get();
    }
}
