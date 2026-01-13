package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingString;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Konfiguration für den Zugriff vom Region-Modul auf den Universe-Server.
 */
@Component
@AllArgsConstructor
@RequiredArgsConstructor
public class RegionSettings {

    @Autowired
    private SSettingsService settingsService;

    @PostConstruct
    private void init() {
        universeBaseUrl = settingsService.getString(
                "universeBaseUrl",
                "http://localhost:9040"
        );
        sectorServerUrl = settingsService.getString(
                "sectorServerUrl",
                "http://localhost:9041"
        );
        sectorServerId = settingsService.getString(
                "sectorServerId",
                "default-sector"
        );
    }

    private SettingString universeBaseUrl;

    private SettingString sectorServerUrl;

    private SettingString sectorServerId;

    public String getUniverseBaseUrl() {
        return universeBaseUrl.get();
    }

    public String getSectorServerUrl() {
        return sectorServerUrl.get();
    }

    public String getSectorServerId() {
        return sectorServerId.get();
    }

}
