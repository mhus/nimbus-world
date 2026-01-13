package de.mhus.nimbus.world.player.commands;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.util.List;
import java.util.Optional;

/**
 * BlockUpdate command - sends "b.u" message to client via WebSocket.
 * Triggered by world-control after block edit operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockUpdateCommand implements Command {

    private final SessionManager sessionManager;
    private final EngineMapper engineMapper;

    @Override
    public String getName() {
        return "BlockUpdate";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        // Args: [blockDataObjectAsString]
        if (args.size() < 1) {
            return CommandResult.error(-3, "Usage: BlockUpdate <blockDataObjectAsString>");
        }

        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CommandResult.error(-2, "Session ID required");
        }

        try {
            String blockData = args.get(0);

            // Find session
            Optional<PlayerSession> sessionOpt = sessionManager.getBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                return CommandResult.error(-4, "Session not found: " + sessionId);
            }

            if (Strings.isEmpty(blockData)) {
                return CommandResult.error(-5, "Block data corrupted");
            }
            blockData = blockData.trim();

            // Parse as single block or array of blocks
            if (blockData.startsWith("{") && blockData.endsWith("}")) {
                blockData = "[" + blockData + "]";
            }
            if (!blockData.startsWith("[") || !blockData.endsWith("]")) {
                return CommandResult.error(-5, "Block data corrupted");
            }

            // Deserialize to Block DTO array for validation and processing
            Block[] blocks = engineMapper.readValue(blockData, Block[].class);

            // Re-serialize validated blocks (includes source field if set)
            var blockJson = engineMapper.valueToTree(blocks);

            PlayerSession session = sessionOpt.get();

            // Build "b.u" message
            NetworkMessage message = NetworkMessage.builder()
                    .t("b.u")
                    .d(blockJson)
                    .build();

            String json = engineMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            // Send via WebSocket
            session.getWebSocketSession().sendMessage(textMessage);

            log.debug("Sent block update to client: session={} blocks={}",
                    sessionId, blocks.length);

            return CommandResult.success("Block update sent to client");

        } catch (NumberFormatException e) {
            return CommandResult.error(-5, "Invalid coordinates: " + e.getMessage());
        } catch (Exception e) {
            log.error("BlockUpdate failed: session={}", sessionId, e);
            return CommandResult.error(-6, "Internal error: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Send block update to client via WebSocket (called by world-control)";
    }

    @Override
    public boolean requiresSession() {
        return false;  // sessionId in context
    }
}
