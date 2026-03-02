package de.mhus.nimbus.world.player.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.EditorGameplay;
import de.mhus.nimbus.world.player.gameplay.Gameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionAuthenticatedConsumer;
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
     * Handle session authenticated event. This is called after the player has successfully authenticated and the session is ready.
     * Gameplay implementations can use this to perform any final setup or send initial data to the client.
     *
     * @param session The session that was authenticated
     */
    @Override
    public void onSessionAuthenticated(PlayerSession session) {
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle block interaction", session.getPlayer());
            return;
        }
        gameplay.onSessionAuthenticated(session);
    }

}
