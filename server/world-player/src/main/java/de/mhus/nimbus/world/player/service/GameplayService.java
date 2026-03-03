package de.mhus.nimbus.world.player.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.EditorGameplay;
import de.mhus.nimbus.world.player.gameplay.Gameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionAuthenticatedConsumer;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.world.WWorld;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameplayService implements SessionAuthenticatedConsumer {

    private final List<Gameplay> gameplays;
    private final WPlayerSessionService playerSessionService;
    private final ClientService clientService;
    private Map<String, Gameplay> gameplayMap;

    @PostConstruct
    public void init() {
        gameplayMap = gameplays.stream().collect(Collectors.toMap(Gameplay::getName, g -> g));
        log.info("Initialized GameplayService with gameplays: {}", gameplayMap.keySet());
    }

    public void onPlayerPlayerInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params) {
        log.info("Player {} interacted with player {}: action={}, timestamp={}, params={}",
                GameplayUtil.toString(session.getPlayer()), entityId, action, timestamp, params);
        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle entity interaction", session.getPlayer());
            return;
        }
        gameplay.onPlayerInteraction(session, entityId, action, timestamp, params);
    }

    public void onPlayerEntityInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params) {
        log.info("Player {} interacted with entity {}: action={}, timestamp={}, params={}",
                GameplayUtil.toString(session.getPlayer()), entityId, action, timestamp, params);
        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle entity interaction", session.getPlayer());
            return;
        }
        gameplay.onEntityInteraction(session, entityId, action, timestamp, params);
    }


    public void onSimpleInteraction(PlayerSession session, String action, String shortcutKey) {
        log.info("Player {} simple interaction: action={}, shortcutKey={}",
                GameplayUtil.toString(session.getPlayer()), action, shortcutKey);
        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle simple interaction", session.getPlayer());
            return;
        }
        gameplay.onSimpleInteraction(session, action, shortcutKey);
    }

    public void onPlayerBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, JsonNode params) {
        log.info("Player {} interacted with block at ({}, {}, {}): blockId={}, groupId={}, userAction={}, params={}",
                GameplayUtil.toString(session.getPlayer()), x, y, z, blockId, groupId, userAction, params);

        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle block interaction", session.getPlayer());
            return;
        }
        gameplay.onBlockInteraction(session, x, y, z, blockId, groupId, userAction, params);
    }

    /**
     * Initialize gameplay for a player session based on world settings.
     * If session is in edit mode, CreativeGameplay is used regardless of world settings.
     * If world has no gameplay set or gameplay not found, defaults to AdventureGameplay.
     *
     * This wil be done before session is registered and ready. Do not send any messages to the client from here,
     * just set up the session state. Gameplay implementations will handle sending initial data to the client when
     * the session is ready.
     *
     * @param session PlayerSession to initialize
     * @param world   WWorld whose gameplay settings to use
     */
    public void initSessionGameplay(PlayerSession session, WWorld world) {
        String gameplay = null;
        if (session.isEditActor()) {
            gameplay = EditorGameplay.class.getSimpleName();
        } else {
            gameplay = world.getGameplay();
        }
        if (gameplay == null || !gameplayMap.containsKey(gameplay)) {
            log.warn("Gameplay {} not found for world {}, defaulting to AdventureGameplay",
                    gameplay, world.getId());
            gameplay = AdventureGameplay.class.getSimpleName();
        }
        session.setGameplay(gameplayMap.get(gameplay));
    }

    /**
     * Called when a player's shortcuts have been modified via the shortcut panel.
     * Implementation will be added later.
     */
    public void onShortcutModified(PlayerSession session) {
        log.info("Shortcut modified for player {}", GameplayUtil.toString(session.getPlayer()));
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle shortcut modification", session.getPlayer());
            return;
        }
        clientService.sendCommand(session, "ShortcutModified", List.of());
        gameplay.onShortcutModified(session);
    }

    /**
     * Called when a player's wearing items have been modified via the wearing panel.
     * Implementation will be added later.
     */
    public void onWearingModified(PlayerSession session) {
        log.info("Wearing modified for player {}", GameplayUtil.toString(session.getPlayer()));
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle wearing modification", session.getPlayer());
            return;
        }
        clientService.sendCommand(session, "WearingModified", List.of());
        gameplay.onWearingModified(session);
    }

    /**
     * Handle session authenticated event. This is called after the player has successfully authenticated and the session is ready.
     * Loads saved gameplay data from WPlayerSession (MongoDB) and passes it to the gameplay implementation for restoration.
     *
     * @param session The session that was authenticated
     */
    @Override
    public void onSessionAuthenticated(PlayerSession session) {
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle session authenticated", session.getPlayer());
            return;
        }

        // Load saved gameplay data from WPlayerSession
        Map<String, Object> savedGameplayData = null;
        try {
            String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
            String playerId = session.getEntityId();
            if (worldId != null && playerId != null && !playerId.isBlank()) {
                var playerSessionOpt = playerSessionService.loadSession(worldId, playerId);
                if (playerSessionOpt.isPresent()) {
                    savedGameplayData = playerSessionOpt.get().getGameplayData();
                    log.info("Loaded saved gameplay data for session {}: worldId={}, playerId={}, hasData={}",
                            session.getSessionId(), worldId, playerId, savedGameplayData != null && !savedGameplayData.isEmpty());
                } else {
                    log.info("No saved player session found for worldId={}, playerId={}", worldId, playerId);
                }
            } else {
                log.debug("Cannot load saved gameplay data: worldId={}, playerId={}", worldId, playerId);
            }
        } catch (Exception e) {
            log.error("Failed to load saved gameplay data for session {}: {}", session.getSessionId(), e.getMessage(), e);
        }

        gameplay.onSessionAuthenticated(session, savedGameplayData);
    }

}
