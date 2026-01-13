package de.mhus.nimbus.world.shared.commands;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for executing commands.
 * Manages command registry and delegates execution to command beans.
 * Commands are loaded lazily to avoid circular dependency with HelpCommand.
 * Supports both local (session-based) and remote (session-less) command execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommandService {

    private final ApplicationContext applicationContext;
    private volatile Map<String, Command> commands;

    /**
     * Get commands lazily (on first access).
     */
    private Map<String, Command> getCommands() {
        if (commands == null) {
            synchronized (this) {
                if (commands == null) {
                    Map<String, Command> commandBeans = applicationContext.getBeansOfType(Command.class);
                    commands = new ConcurrentHashMap<>();

                    for (Command command : commandBeans.values()) {
                        commands.put(command.getName(), command);
                    }

                    log.info("Registered {} commands: {}", commands.size(), commands.keySet());
                }
            }
        }
        return commands;
    }

    /**
     * Execute command by name.
     *
     * @param context Command execution context
     * @param commandName Command name
     * @param args Command arguments
     * @return CommandResult with return code and messages
     */
    public Command.CommandResult execute(CommandContext context, String commandName, List<String> args) {
        Command command = getCommands().get(commandName);

        if (command == null) {
            log.warn("Command not found: {}", commandName);
            return Command.CommandResult.error(-1, "Command not found: " + commandName);
        }

        // Validate session requirement
        if (command.requiresSession() && !context.hasSession()) {
            log.warn("Command requires session but none provided: {}", commandName);
            return Command.CommandResult.error(-2, "Command requires active session");
        }

        try {
            log.debug("Executing command: {} with args: {} from: {} world: {}",
                    commandName, args, context.getOriginServer(), context.getWorldId());

            return command.execute(context, args);

        } catch (Exception e) {
            log.error("Command execution failed: {}", commandName, e);
            return Command.CommandResult.error(-4, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Get all registered command names.
     */
    public List<String> getCommandNames() {
        return getCommands().keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * Get command by name (for help text, etc.).
     */
    public Command getCommand(String name) {
        return getCommands().get(name);
    }
}
