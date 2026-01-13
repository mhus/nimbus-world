package de.mhus.nimbus.world.control.commands;

import de.mhus.nimbus.world.control.service.EditService;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EditBlockTrigger command - triggered by engine when block selected.
 * Updates selected block coordinates in Redis edit state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EditBlockTriggerCommand implements Command {

    private final EditService editService;

    @Override
    public String getName() {
        return "EditBlockTrigger";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        // Args: [x, y, z, command]
        if (args.size() < 3) {
            return CommandResult.error(-3, "Usage: EditBlockTrigger <x> <y> <z> [command]");
        }

        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CommandResult.error(-2, "Session ID required");
        }

        try {
            int x = Integer.parseInt(args.get(0));
            int y = Integer.parseInt(args.get(1));
            int z = Integer.parseInt(args.get(2));
            String command = args.size() >= 4 ? args.get(3) : null;

            // Update selected block in Redis
            editService.doAction(
                    context.getWorldId(),
                    sessionId,
                    x, y, z,
                    command,
                    args
            );

            return CommandResult.success("Block selected at (" + x + "," + y + "," + z + ")");

        } catch (NumberFormatException e) {
            return CommandResult.error(-4, "Invalid coordinates: " + e.getMessage());
        } catch (Exception e) {
            log.error("EditBlockTrigger failed", e);
            return CommandResult.error(-5, "Internal error: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Trigger block selection for edit mode (called by engine)";
    }

    @Override
    public boolean requiresSession() {
        return false;  // sessionId in context is sufficient
    }
}
