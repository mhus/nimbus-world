package de.mhus.nimbus.world.shared.commands;

import java.util.List;

/**
 * Interface for commands.
 * Commands are executed by the CommandService and provide responses.
 * Can be executed locally (with session) or remotely (session-less).
 */
public interface Command {

    /**
     * Get command name (e.g., "help", "say", "status").
     */
    String getName();

    /**
     * Execute command.
     *
     * @param context Command execution context (contains worldId, sessionId, userId, etc.)
     * @param args    Command arguments
     * @return CommandResult with return code and messages
     */
    CommandResult execute(CommandContext context, List<String> args);

    /**
     * Get command help text.
     */
    String getHelp();

    /**
     * Indicates if this command requires an active session.
     * Commands that need direct WebSocket access should return true.
     * Remote calls to session-required commands will return error -2.
     *
     * @return true if command requires session, false by default
     */
    default boolean requiresSession() {
        return false;
    }

    /**
     * Command execution result.
     */
    class CommandResult {
        private final int returnCode;
        private final String message;
        private final List<String> streamMessages;

        public CommandResult(int returnCode, String message) {
            this(returnCode, message, null);
        }

        public CommandResult(int returnCode, String message, List<String> streamMessages) {
            this.returnCode = returnCode;
            this.message = message;
            this.streamMessages = streamMessages;
        }

        public int getReturnCode() {
            return returnCode;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getStreamMessages() {
            return streamMessages;
        }

        public boolean isSuccess() {
            return returnCode == 0;
        }

        // Factory methods
        public static CommandResult success(String message) {
            return new CommandResult(0, message);
        }

        public static CommandResult error(String message) {
            return new CommandResult(1, message);
        }

        public static CommandResult error(int returnCode, String message) {
            return new CommandResult(returnCode, message);
        }

        public static CommandResult withStreaming(int returnCode, String message, List<String> streamMessages) {
            return new CommandResult(returnCode, message, streamMessages);
        }
    }
}
