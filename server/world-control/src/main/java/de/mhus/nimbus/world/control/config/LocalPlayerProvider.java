package de.mhus.nimbus.world.control.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.network.ClientType;
import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.types.PlayerData;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.shared.region.RCharacterService;
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
public class LocalPlayerProvider {

    @Value("${world.player.data.directory:./data/players}")
    private String playerDataDirectory;

    private final ObjectMapper objectMapper;
    private final SSettingsService settingsService;
    private final RUserService userService;
    private final RCharacterService characterService;

    @PostConstruct
    public void init() {
        importUsers();
        importCharacters();
    }

    private void importCharacters() {
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
        File dir = Paths.get(playerDataDirectory).toFile();
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".json"));
        if (files == null) return new PlayerId[0];
        return Arrays.stream(files)
                .map(File::getName)
                .map(name -> name.substring(0, name.length() - 5)) // entferne .json
                .map(name -> name.split("_"))
                .filter(parts -> parts.length >= 3)
                .map(parts -> "@" + parts[0] + ":" + parts[1])
                .map(PlayerId::of)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toArray(PlayerId[]::new);
    }

    public Optional<PlayerData> getPlayer(PlayerId playerId, ClientType clientType) {

        String pathStr = playerDataDirectory + "/" + normalizePath(playerId.getRawId() + "_" + clientType.tsString()) + ".json";
        var path = Paths.get(pathStr);
        try {
            if (!Files.exists(path)) {
                log.debug("Player data file does not exist at path: {}", pathStr);
                return Optional.empty();
            }
            var content = Files.readString(path);
            var playerData = objectMapper.readValue(content, PlayerData.class);
            return Optional.of(playerData);
        } catch (IOException e) {
            log.warn("Player data not found for id: {} and client: {} at path: {}", playerId, clientType, pathStr, e);
            return Optional.empty();
        }
    }

    private String normalizePath(String path) {
        return path.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    }
}
