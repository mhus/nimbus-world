package de.mhus.nimbus.world.control.commands;

import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.session.SessionCommandService;
import de.mhus.nimbus.world.shared.session.SessionCommandTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Send commands or notifications to connected player sessions via Redis broadcast.
 *
 * Usage:
 *   SessionCommand notify &lt;targetType&gt; [target] &lt;source&gt; &lt;title&gt; &lt;text&gt;
 *   SessionCommand cmd &lt;targetType&gt; [target] &lt;cmd&gt; [args...]
 *
 * Target types: ALL, TEAM, PLAYER, WORLD
 * - ALL: all connected sessions (no target needed)
 * - TEAM: target = teamId
 * - PLAYER: target = playerId (e.g., @mhus:j3sus)
 * - WORLD: target = worldId prefix (e.g., "earth616:" for whole region)
 *
 * Examples:
 *   SessionCommand notify ALL 0 "System" "Server fährt runter in 5 Minuten"
 *   SessionCommand notify TEAM myteam-123 0 "Team" "Euer Team hat gewonnen!"
 *   SessionCommand notify WORLD earth616: 2 "World" "Event startet!"
 *   SessionCommand cmd ALL notification 0 System "Wartung in 5 Min"
 *   SessionCommand cmd PLAYER @mhus:j3sus redirect /lobby
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCommandCommand implements Command {

    private final SessionCommandService sessionCommandService;

    @Override
    public String getName() {
        return "SessionCommand";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            return CommandResult.error(-3, "Usage: SessionCommand <notify|cmd> <targetType> [target] <args...>\n" + getHelp());
        }

        String subCommand = args.get(0);

        return switch (subCommand) {
            case "notify" -> handleNotify(args);
            case "cmd" -> handleCmd(args);
            default -> CommandResult.error(-3, "Unknown sub-command: " + subCommand + ". Use 'notify' or 'cmd'");
        };
    }

    private CommandResult handleNotify(List<String> args) {
        // notify <targetType> [target] <source> <title> <text>
        if (args.size() < 4) {
            return CommandResult.error(-3, "Usage: SessionCommand notify <targetType> [target] <source> <title> <text>");
        }

        SessionCommandTarget targetType;
        try {
            targetType = SessionCommandTarget.valueOf(args.get(1));
        } catch (IllegalArgumentException e) {
            return CommandResult.error(-4, "Invalid targetType: " + args.get(1) + ". Use ALL, TEAM, PLAYER, WORLD");
        }

        int idx = 2;
        String target = null;
        if (targetType != SessionCommandTarget.ALL) {
            if (args.size() < 5) {
                return CommandResult.error(-3, "Target required for " + targetType);
            }
            target = args.get(idx++);
        }

        if (args.size() < idx + 3) {
            return CommandResult.error(-3, "Usage: SessionCommand notify <targetType> [target] <source> <title> <text>");
        }

        int source;
        try {
            source = Integer.parseInt(args.get(idx));
        } catch (NumberFormatException e) {
            return CommandResult.error(-4, "Invalid source (0=System, 1=Player, 2=World): " + args.get(idx));
        }
        String title = args.get(idx + 1);
        String text = args.get(idx + 2);

        sessionCommandService.sendNotification(targetType, target, source, title, text);
        log.info("Session notification sent: targetType={}, target={}, title={}", targetType, target, title);
        return CommandResult.success("Notification sent to " + targetType + (target != null ? " " + target : ""));
    }

    private CommandResult handleCmd(List<String> args) {
        // cmd <targetType> [target] <cmd> [args...]
        if (args.size() < 3) {
            return CommandResult.error(-3, "Usage: SessionCommand cmd <targetType> [target] <cmd> [args...]");
        }

        SessionCommandTarget targetType;
        try {
            targetType = SessionCommandTarget.valueOf(args.get(1));
        } catch (IllegalArgumentException e) {
            return CommandResult.error(-4, "Invalid targetType: " + args.get(1) + ". Use ALL, TEAM, PLAYER, WORLD");
        }

        int idx = 2;
        String target = null;
        if (targetType != SessionCommandTarget.ALL) {
            if (args.size() < 4) {
                return CommandResult.error(-3, "Target required for " + targetType);
            }
            target = args.get(idx++);
        }

        if (args.size() <= idx) {
            return CommandResult.error(-3, "Command name required");
        }

        String cmd = args.get(idx++);
        List<String> cmdArgs = args.subList(idx, args.size());

        sessionCommandService.sendCommand(targetType, target, cmd, cmdArgs);
        log.info("Session command sent: targetType={}, target={}, cmd={}", targetType, target, cmd);
        return CommandResult.success("Command '" + cmd + "' sent to " + targetType + (target != null ? " " + target : ""));
    }

    @Override
    public String getHelp() {
        return """
                Send commands/notifications to connected player sessions.

                SessionCommand notify <targetType> [target] <source> <title> <text>
                  source: 0=System, 1=Player, 2=World

                SessionCommand cmd <targetType> [target] <cmd> [args...]

                Target types:
                  ALL    - all sessions (no target)
                  TEAM   - target = teamId
                  PLAYER - target = playerId (@userId:charName)
                  WORLD  - target = worldId prefix (e.g. "earth616:" for region)

                Examples:
                  SessionCommand notify ALL 0 System "Server shutting down in 5 min"
                  SessionCommand notify TEAM team-abc 0 Team "Your team won!"
                  SessionCommand notify WORLD earth616: 2 World "Event starting!"
                  SessionCommand cmd ALL notification 0 System "Maintenance soon"
                  SessionCommand cmd PLAYER @mhus:j3sus redirect /lobby""";
    }

    @Override
    public boolean requiresSession() {
        return false;
    }
}
