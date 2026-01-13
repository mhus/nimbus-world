package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingInteger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionCharacterSettings {

    private final SSettingsService settingsService;

    private SettingInteger maxPerRegion;

    @PostConstruct
    private void init() {
        maxPerRegion = settingsService.getInteger(
                "region.character.maxPerRegion",
                10
        );
    }

    /**
     * Fallback max characters per region if user-specific limit is absent.
     * Default: 10
     */
    public int getMaxPerRegion() {
        return maxPerRegion.get();
    }
}

