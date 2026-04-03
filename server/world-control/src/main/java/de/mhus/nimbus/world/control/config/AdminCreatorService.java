package de.mhus.nimbus.world.control.config;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingBoolean;
import de.mhus.nimbus.shared.settings.SettingString;
import de.mhus.nimbus.shared.types.PlayerUser;
import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.region.RRegion;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminCreatorService {

    private final SSettingsService settingsService;
    private final RRegionService regionService;
    private final RUserService userService;
    private final RCharacterService characterService;
    private final WWorldService worldService;
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
        checkWorldOwnership();
    }

    private void checkSector() {
        var userOpt = userService.getByUsername(settingAdminUsername.get());
        if (userOpt.isPresent()) {
            // Ensure existing admin user has ADMIN role
            var user = userOpt.get();
            if (!user.getSectorRoles().contains(SectorRoles.ADMIN)) {
                user.addSectorRole(SectorRoles.ADMIN);
                userService.save(user);
                log.info("Added ADMIN role to existing admin user '{}'", settingAdminUsername.get());
            }
            return;
        }
        log.debug("Admin user '{}' not found, creating it with email '{}'", settingAdminUsername.get(), settingAdminEmail.get());
        var data = new PlayerUser();
        data.setName(settingAdminUsername.get());
        data.setTitle("Admin");
        var newUser = userService.createUser(data, settingAdminEmail.get());
        newUser.addSectorRole(SectorRoles.ADMIN);
        newUser.addSectorRole(SectorRoles.PLAYER);
        userService.save(newUser);
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
        var created = characterService.createCharacter(settingAdminUsername.get(), region.getName(), settingCharacterName.get(), "Admin character");
        created.getPublicData().setThirdPersonModelId("n:wizard");
        characterService.updateCharater(created);
    }

    private void checkWorldOwnership() {
        String adminUsername = settingAdminUsername.get();
        for (WWorld world : worldService.findAll()) {
            if (world.getOwner() != null && world.getOwner().contains(adminUsername)) {
                continue;
            }
            Set<String> owners = world.getOwner() != null ? new HashSet<>(world.getOwner()) : new HashSet<>();
            owners.add(adminUsername);
            world.setOwner(owners);
            worldService.save(world);
            log.debug("Added admin '{}' as owner to world '{}'", adminUsername, world.getWorldId());
        }
    }

    /**
     * Returns the configured admin username.
     * Used by AccessValidator for sector admin checks.
     */
    public String getAdminUsername() {
        return settingAdminUsername.get();
    }

}
