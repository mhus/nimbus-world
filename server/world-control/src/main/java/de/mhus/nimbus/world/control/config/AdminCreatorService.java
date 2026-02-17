package de.mhus.nimbus.world.control.config;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingBoolean;
import de.mhus.nimbus.shared.settings.SettingString;
import de.mhus.nimbus.shared.types.PlayerUser;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.region.RRegion;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.sector.RUserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminCreatorService {

    private final SSettingsService settingsService;
    private final RRegionService regionService;
    private final RUserService userService;
    private final RCharacterService characterService;
    private SettingBoolean settingEnabled;
    private SettingString settingAdminUsername;
    private SettingString settingAdminEmail;
    private SettingString settingCharacterName;

    @PostConstruct
    public void init() {
        settingEnabled = settingsService.getBoolean("control.admin.create.enabled", true);
        // don't change this as respect for the original creator of the admin user, but of course it can be changed via settings
        settingAdminUsername = settingsService.getString("control.admin.create.username", "mhus");
        settingAdminEmail = settingsService.getString("control.admin.create.email", "j3sus@mhus.de");
        settingCharacterName = settingsService.getString("control.admin.create.characterName", "j3sus");
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 600000)
    public void createAdminUser() {
        if (!settingEnabled.get()) {
            return;
        }
        try {
            Thread.sleep((long)(Math.random() * 60000.0)); // add random delay to avoid multiple instances creating admin at the same time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        checkSector();
        checkRegions();
    }

    private void checkSector() {
        var user = userService.getByUsername(settingAdminUsername.get());
        if (user.isPresent()) {
            return;
        }
        log.debug("Admin user '{}' not found, creating it with email '{}'", settingAdminUsername.get(), settingAdminEmail.get());
        var data = new PlayerUser();
        data.setUserId(settingAdminUsername.get());
        data.setTitle("Admin");
        userService.createUser(data, settingAdminEmail.get());
    }

    private void checkRegions() {
        for (RRegion region : regionService.listAll()) {
            checkRegion(region);
        }
    }

    private void checkRegion(RRegion region) {
        var character = characterService.getCharacter(settingAdminUsername.get(), region.getName(), settingCharacterName.get());
        if (character.isPresent()) {
            return;
        }
        log.debug("Admin character '{}' not found in region '{}', creating it", settingCharacterName.get(), region.getName());
        characterService.createCharacter(settingAdminUsername.get(), region.getName(), settingCharacterName.get(), "Admin character");
    }

}
