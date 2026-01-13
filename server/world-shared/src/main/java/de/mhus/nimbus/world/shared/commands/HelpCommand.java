package de.mhus.nimbus.world.shared.commands;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Help command - shows available commands and their usage.
 *
 * Usage:
 *   /help           - List all commands
 *   /help <command> - Show help for specific command
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HelpCommand implements Command {

    private final CommandService commandService;

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        // No arguments - list all commands
        if (args == null || args.isEmpty()) {
            List<String> commandNames = commandService.getCommandNames();

            StringBuilder sb = new StringBuilder();
            sb.append("Available commands:\n");
            for (String name : commandNames) {
                Command cmd = commandService.getCommand(name);
                if (cmd != null) {
                    sb.append("  /").append(name);
                    String help = cmd.getHelp();
                    if (help != null && !help.isBlank()) {
                        // Take first line of help as short description
                        String firstLine = help.split("\n")[0];
                        sb.append(" - ").append(firstLine);
                    }
                    sb.append("\n");
                }
            }
            sb.append("\nType '/help <command>' for more information.");

            return CommandResult.success(sb.toString());
        }

        // Specific command help
        String commandName = args.get(0);
        Command command = commandService.getCommand(commandName);

        if (command == null) {
            return CommandResult.error("Command not found: " + commandName);
        }

        String help = command.getHelp();
        if (help == null || help.isBlank()) {
            return CommandResult.success("No help available for: " + commandName);
        }

        return CommandResult.success("Command: /" + commandName + "\n\n" + help);
    }

    @Override
    public String getHelp() {
        return "Show help for commands\n" +
                "Usage:\n" +
                "  /help           - List all commands\n" +
                "  /help <command> - Show help for specific command\n" +
                "\n" +
                "Examples:\n" +
                "  /help\n" +
                "  /help say";
    }
}
