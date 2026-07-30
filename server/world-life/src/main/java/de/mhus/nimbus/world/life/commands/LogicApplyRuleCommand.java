package de.mhus.nimbus.world.life.commands;

import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import de.mhus.nimbus.world.life.logic.LogicMachineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Execute a logic rule by name.
 * Usage: /logic-apply <ruleName>
 *
 * The rule's condition is checked. If true, effects are executed with
 * full pipeline (locking, cascade, persist).
 */
@Component
@RequiredArgsConstructor
public class LogicApplyRuleCommand implements Command {

    private final WLogicRuleService ruleService;
    private final LogicMachineService logicMachineService;

    @Override
    public String getName() {
        return "logic-apply";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String worldId = context.getWorldId();
        if (worldId == null) {
            return CommandResult.error("No worldId in context");
        }
        if (args.isEmpty()) {
            return CommandResult.error("Usage: /logic-apply <ruleName>");
        }

        String ruleName = args.get(0);

        // Find rule by worldId and name
        WLogicRule rule = ruleService.findByWorldIdAndName(worldId, ruleName).orElse(null);
        if (rule == null) {
            return CommandResult.error("Rule not found: " + ruleName + " in world " + worldId);
        }

        if (!rule.isEnabled()) {
            return CommandResult.error("Rule '" + ruleName + "' is disabled");
        }

        try {
            logicMachineService.executeRuleDirectly(worldId, rule);
            return CommandResult.success("Rule '" + ruleName + "' executed for world " + worldId);
        } catch (Exception e) {
            return CommandResult.error("Failed to execute rule '" + ruleName + "': " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Execute a logic rule by name\n" +
                "Usage: /logic-apply <ruleName>\n" +
                "Checks condition, executes effects, triggers cascade.";
    }
}
