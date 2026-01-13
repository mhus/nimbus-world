/**
 * CommandHandler - Abstract base class for all command handlers
 *
 * Command handlers implement specific commands that can be executed
 * via the CommandService. Each handler has a name, description, and
 * execute method that processes command parameters.
 */

/**
 * Abstract base class for command handlers
 *
 * Subclasses must implement:
 * - name(): string - Command name (e.g., "help", "weather")
 * - description(): string - Short description of what the command does
 * - execute(parameters: string[]): any - Command execution logic
 */
export abstract class CommandHandler {
  /**
   * Get the command name
   * This name is used for command execution and browser console function generation
   * @returns Command name (lowercase, no spaces)
   */
  abstract name(): string;

  /**
   * Get a short description of what this command does
   * @returns Command description
   */
  abstract description(): string;

  /**
   * Execute the command with given parameters
   * Can be synchronous or asynchronous
   *
   * @param parameters Array of string parameters passed to the command
   * @returns Command result (can be any type, will be logged to console)
   * @throws Error if command execution fails
   */
  abstract execute(parameters: any[]): Promise<any> | any;
}
