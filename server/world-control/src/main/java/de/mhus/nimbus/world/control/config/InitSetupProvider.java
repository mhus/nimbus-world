package de.mhus.nimbus.world.control.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.network.ClientType;
import de.mhus.nimbus.shared.types.PlayerData;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.region.RRegion;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.sector.RUserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile({"test","default"})
public class InitSetupProvider {

    @Value("${world.init.region.data.directory:setup/regions}")
    private String regionDataResourceDirectory;

    @Value("${world.init.player.data.directory:setup/players}")
    private String playerDataResourceDirectory;

    @Value("${world.init.always:false}")
    private boolean alwaysRun;

    private final ObjectMapper objectMapper;
    private final RUserService userService;
    private final RCharacterService characterService;
    private final RRegionService regionService;
    private final InitRegionService initRegionService; // be sure InitRegionService is initialized first

    @PostConstruct
    public void init() {
        importRegions();
        importUsers();
        importCharacters();
    }

    private void importRegions() {
        if (!alwaysRun && regionService.getRegionsCount() > 0) {
            log.info("Regions already exist, skipping region import.");
            return;
        }
        for (var regionId : getRegions()) {
            var regionOpt = getRegion(regionId);
            if (regionOpt.isEmpty()) {
                log.warn("No region data found for regionId: {}", regionId);
                continue;
            }
            if (regionService.getByName(regionId).isPresent()) {
                log.info("Region already exists: {}, skipping.", regionId);
                continue;
            }
            var regionData = regionOpt.get();
            regionService.create(regionData.getName(), String.join(",", regionData.getMaintainers()));
        }
    }

    private Optional<RRegion> getRegion(String regionId) {
        String resourcePath = regionDataResourceDirectory + "/" + normalizePath(regionId) + ".json";
        ClassLoader classLoader = getClass().getClassLoader();
        try (var inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                log.debug("Region data resource not found at path: {}", resourcePath);
                return Optional.empty();
            }
            var regionData = objectMapper.readValue(inputStream, RRegion.class);
            return Optional.of(regionData);
        } catch (IOException e) {
            log.warn("Region data not found for id: {} at resource: {}", regionId, resourcePath, e);
            return Optional.empty();
        }
    }

    private String[] getRegions() {
        String resourceDir = regionDataResourceDirectory;
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource(resourceDir);
        if (resource == null) return new String[0];
        try {
            var uri = resource.toURI();
            var path = Paths.get(uri);
            try (var stream = Files.list(path)) {
                return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                    .toArray(String[]::new);
            }
        } catch (Exception e) {
            log.warn("Could not list region data resources in directory: {}", resourceDir, e);
            return new String[0];
        }
    }

    private void importCharacters() {
        if (!alwaysRun && characterService.getCharacterCount() > 0) {
            log.info("Characters already exist, skipping character import.");
            return;
        }
        for (var playerId : getPlayers()) {
            var playerOpt = getPlayer(playerId, ClientType.WEB);
            if (playerOpt.isEmpty()) {
                log.warn("No player data found for playerId: {}", playerId);
                continue;
            }
            var player = playerOpt.get();
            characterService.getCharacter(player.user().getUserId(), "earth616", playerId.getCharacterId())
                    .orElseGet(() -> {
                        var character = characterService.createCharacter(
                                playerId.getUserId(),
                                "earth616",
                                playerId.getCharacterId(),
                                player.character().getPublicData().getTitle()
                        );
                        character.setBackpack(player.character().getBackpack());
                        character.setPublicData(player.character().getPublicData());
                        characterService.updateCharater(character);
                        log.info("Imported character: {} for user: {}", character.getName(), player.user().getUserId());
                        return character;
                    });
        }
    }

    private void importUsers() {
        if (!alwaysRun && userService.getUserCount() > 0) {
            log.info("Users already exist, skipping user import.");
            return;
        }
        for (var playerId : getPlayers()) {
            var playerOpt = getPlayer(playerId, ClientType.WEB);
            if (playerOpt.isEmpty()) {
                log.warn("No player data found for playerId: {}", playerId);
                continue;
            }
            var player = playerOpt.get();
            var userId = player.user().getUserId();
            var user = userService.getByUsername(userId);
            if (user.isEmpty()) {
                userService.createUser(player.user(), player.user().getUserId() + "@local");
                log.info("Imported user: {}", userId);
            } else {
                log.debug("User already exists: {}", userId);
            }
        }
    }

    // Gibt alle PlayerIds aus den relevanten JSON-Dateien zurück (Dateiname: _userId_characterId_clientType.json)
    private PlayerId[] getPlayers() {
        String resourceDir = playerDataResourceDirectory;
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource(resourceDir);
        if (resource == null) return new PlayerId[0];
        try {
            var uri = resource.toURI();
            var path = Paths.get(uri);
            var files = Files.list(path)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .toArray(String[]::new);
            return Arrays.stream(files)
                    .map(name -> name.substring(0, name.length() - 5)) // entferne .json
                    .map(name -> name.split("_"))
                    .filter(parts -> parts.length >= 3)
                    .map(parts -> "@" + parts[0] + ":" + parts[1])
                    .map(PlayerId::of)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toArray(PlayerId[]::new);
        } catch (Exception e) {
            log.warn("Could not list player data resources in directory: {}", resourceDir, e);
            return new PlayerId[0];
        }
    }

    public Optional<PlayerData> getPlayer(PlayerId playerId, ClientType clientType) {
        String resourcePath = playerDataResourceDirectory + "/" + normalizePath(playerId.getRawId() + "_" + clientType.tsString()) + ".json";
        ClassLoader classLoader = getClass().getClassLoader();
        try (var inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                log.debug("Player data resource not found at path: {}", resourcePath);
                return Optional.empty();
            }
            var playerData = objectMapper.readValue(inputStream, PlayerData.class);
            return Optional.of(playerData);
        } catch (IOException e) {
            log.warn("Player data not found for id: {} and client: {} at resource: {}", playerId, clientType, resourcePath, e);
            return Optional.empty();
        }
    }

    private String normalizePath(String path) {
        return path.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    }
}
