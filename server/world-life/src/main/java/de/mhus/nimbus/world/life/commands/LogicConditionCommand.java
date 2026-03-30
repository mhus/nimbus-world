package de.mhus.nimbus.world.life.commands;

import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.world.LogicConditionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Test a SpEL condition against the current logic state.
 * Usage: /logic-condition <spelExpression>
 *
 * Expression must be fully qualified (state.pkg.key), same as serverInfo conditions.
 * Example: /logic-condition state.puzzle.hasKey == true
 */
@Component
@RequiredArgsConstructor
public class LogicConditionCommand implements Command {

    private final LogicConditionService conditionService;

    @Override
    public String getName() {
        return "logic-condition";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String worldId = context.getWorldId();
        if (worldId == null) {
            return CommandResult.error("No worldId in context");
        }
        if (args.isEmpty()) {
            return CommandResult.error("Usage: /logic-condition <spelExpression>");
        }

        String expression = String.join(" ", args);

        try {
            boolean result = conditionService.checkCondition(worldId, expression);
            return CommandResult.success(expression + " => " + result);
        } catch (Exception e) {
            return CommandResult.error("Evaluation failed: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Test a SpEL condition against current logic state\n" +
                "Usage: /logic-condition <spelExpression>\n" +
                "Expression must be fully qualified: state.pkg.key == value\n" +
                "Example: /logic-condition state.puzzle.hasKey == true";
    }
}
