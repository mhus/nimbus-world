package de.mhus.nimbus.world.life.commands;

import de.mhus.nimbus.world.life.logic.LogicEvent;
import de.mhus.nimbus.world.life.logic.LogicMachineService;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Set logic state values via SpEL assignment.
 * Usage: /logic-set <spelAssignment>
 *
 * Assignment must be fully qualified (state.pkg.key), same as serverInfo logic parameter.
 * Multiple assignments separated by semicolon.
 *
 * Examples:
 *   /logic-set state.puzzle.hasKey = true
 *   /logic-set state.puzzle.counter = state.puzzle.counter + 1
 *   /logic-set state.puzzle.flag1 = true; state.puzzle.flag2 = false
 */
@Component
@RequiredArgsConstructor
public class LogicSetCommand implements Command {

    private final LogicMachineService logicMachineService;

    @Override
    public String getName() {
        return "logic-set";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String worldId = context.getWorldId();
        if (worldId == null) {
            return CommandResult.error("No worldId in context");
        }
        if (args.isEmpty()) {
            return CommandResult.error("Usage: /logic-set <spelAssignment>");
        }

        String expression = String.join(" ", args);

        // Split by semicolon for multiple assignments
        List<String> eval = java.util.Arrays.stream(expression.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (eval.isEmpty()) {
            return CommandResult.error("No valid assignments found");
        }

        try {
            LogicEvent event = LogicEvent.builder()
                    .worldId(worldId)
                    .eval(eval)
                    .source("command:logic-set")
                    .build();

            logicMachineService.processEvent(event);
            return CommandResult.success("Executed: " + String.join("; ", eval));
        } catch (Exception e) {
            return CommandResult.error("Failed: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Set logic state values via SpEL assignment\n" +
                "Usage: /logic-set <spelAssignment>\n" +
                "Assignment must be fully qualified: state.pkg.key = value\n" +
                "Multiple assignments separated by semicolon.\n" +
                "Triggers rule cascade after state changes.\n" +
                "Examples:\n" +
                "  /logic-set state.puzzle.hasKey = true\n" +
                "  /logic-set state.puzzle.counter = state.puzzle.counter + 1";
    }
}
