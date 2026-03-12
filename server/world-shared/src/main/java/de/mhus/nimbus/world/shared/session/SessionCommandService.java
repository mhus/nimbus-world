package de.mhus.nimbus.world.shared.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Publishes commands to player sessions via Redis.
 * Any service (world-life, world-control, world-player, etc.) can use this
 * to send commands to connected clients across all pods.
 *
 * Redis channel: world:global:s.cmd
 * Message format: { "targetType": "ALL|TEAM|PLAYER|WORLD", "target": "...", "cmd": "...", "args": [...] }
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionCommandService {

    public static final String REDIS_CHANNEL_SESSION_CMD = "s.cmd";

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;

    /**
     * Send a command to sessions matching the given target.
     *
     * @param targetType target type (ALL, TEAM, PLAYER, WORLD)
     * @param target target value (teamId, playerId, worldId, or null for ALL)
     * @param cmd command name (e.g., "notification", "redirect")
     * @param args command arguments
     */
    public void sendCommand(SessionCommandTarget targetType, String target, String cmd, List<String> args) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("targetType", targetType.name());
            if (target != null) {
                node.put("target", target);
            }
            node.put("cmd", cmd);
            ArrayNode argsNode = objectMapper.createArrayNode();
            if (args != null) {
                args.forEach(argsNode::add);
            }
            node.set("args", argsNode);

            redisMessaging.publishGlobal(REDIS_CHANNEL_SESSION_CMD, objectMapper.writeValueAsString(node));
            log.debug("Published session command: targetType={}, target={}, cmd={}", targetType, target, cmd);
        } catch (Exception e) {
            log.error("Failed to publish session command: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a command to all connected sessions.
     */
    public void sendToAll(String cmd, List<String> args) {
        sendCommand(SessionCommandTarget.ALL, null, cmd, args);
    }

    /**
     * Send a command to all members of a team.
     *
     * @param teamId the team ID
     * @param cmd command name
     * @param args command arguments
     */
    public void sendToTeam(String teamId, String cmd, List<String> args) {
        sendCommand(SessionCommandTarget.TEAM, teamId, cmd, args);
    }

    /**
     * Send a command to a specific player.
     *
     * @param playerId the player ID (e.g., "@userId:characterName")
     * @param cmd command name
     * @param args command arguments
     */
    public void sendToPlayer(String playerId, String cmd, List<String> args) {
        sendCommand(SessionCommandTarget.PLAYER, playerId, cmd, args);
    }

    /**
     * Send a command to all sessions in a specific world.
     *
     * @param worldId the world ID
     * @param cmd command name
     * @param args command arguments
     */
    public void sendToWorld(String worldId, String cmd, List<String> args) {
        sendCommand(SessionCommandTarget.WORLD, worldId, cmd, args);
    }

    /**
     * Send a notification to sessions matching the given target.
     * Convenience method for the common notification use case.
     *
     * @param targetType target type
     * @param target target value
     * @param source notification source (0=System, 1=Player, 2=World)
     * @param title notification title
     * @param text notification text
     */
    public void sendNotification(SessionCommandTarget targetType, String target, int source, String title, String text) {
        sendCommand(targetType, target, "notification", List.of(String.valueOf(source), title, text));
    }
}
