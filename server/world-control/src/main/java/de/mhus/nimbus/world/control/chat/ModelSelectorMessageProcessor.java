package de.mhus.nimbus.world.control.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatMessageProcessor;
import de.mhus.nimbus.world.shared.client.WorldClientService;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Processes model-selector command messages from chat agents.
 * Extracts ModelSelector data, stores it in Redis, and sends
 * a ShowModelSelector command to the player.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModelSelectorMessageProcessor implements WChatMessageProcessor {

    private final WSessionService wSessionService;
    private final ObjectMapper objectMapper;
    private final WorldClientService worldClientService;

    @Override
    public boolean canProcess(WChatMessage message) {
        return "model-selector".equals(message.getType()) && message.isCommand();
    }

    @Override
    public void process(WorldId worldId, String sessionId, WChatMessage message) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Cannot process model-selector without sessionId");
            return;
        }

        var lookupWorld = worldId.toBaseWorldId();

        try {
            List<String> modelSelectorData = objectMapper.readValue(
                    message.getMessage(),
                    new TypeReference<List<String>>() {}
            );

            wSessionService.updateModelSelector(sessionId, modelSelectorData);

            log.info("Stored ModelSelector in Redis: sessionId={}, blocks={}",
                    sessionId, modelSelectorData.size());

            sendShowModelSelectorCommand(lookupWorld.getId(), sessionId);

        } catch (Exception e) {
            log.error("Failed to process model-selector for sessionId: {}", sessionId, e);
        }
    }

    private void sendShowModelSelectorCommand(String worldId, String sessionId) {
        try {
            Optional<WSession> wSessionOpt = wSessionService.getWithPlayerUrl(sessionId);

            if (wSessionOpt.isEmpty()) {
                log.warn("No WSession found for sessionId: {}, cannot send ShowModelSelector command", sessionId);
                return;
            }

            WSession wSession = wSessionOpt.get();
            String playerUrl = wSession.getPlayerUrl();

            if (playerUrl == null || playerUrl.isBlank()) {
                log.warn("No player URL available for session {}, cannot send ShowModelSelector command", sessionId);
                return;
            }

            CommandContext ctx = CommandContext.builder()
                    .worldId(worldId)
                    .sessionId(sessionId)
                    .originServer("world-control")
                    .build();

            worldClientService.sendPlayerCommand(
                    worldId,
                    sessionId,
                    playerUrl,
                    "client.ShowModelSelector",
                    List.of(),
                    ctx
            );

            log.info("Sent ShowModelSelector command to player: sessionId={}, playerUrl={}", sessionId, playerUrl);

        } catch (Exception e) {
            log.error("Failed to send ShowModelSelector command for sessionId: {}", sessionId, e);
        }
    }
}
