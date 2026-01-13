/**
 * HelpCommand - Lists all available commands with descriptions
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('HelpCommand');
import type { CommandService } from '../services/CommandService';

/**
 * Help command - Lists all available commands
 */
export class HelpCommand extends CommandHandler {
  private commandService: CommandService;

  constructor(commandService: CommandService) {
    super();
    this.commandService = commandService;
  }

  name(): string {
    return 'help';
  }

  description(): string {
    return 'Lists all available commands or shows details for a specific command (help [commandName])';
  }

  execute(parameters: any[]): any {
    const handlers = this.commandService.getHandlers();

    if (handlers.size === 0) {
      return 'No commands available';
    }

    // If a command name is provided, show details for that command
    if (parameters.length > 0) {
      const commandName = parameters[0].toString();
      const handler = handlers.get(commandName);

      if (!handler) {
        const error = `Command '${commandName}' not found. Use 'help' to see all available commands.`;
        logger.error(error);
        return { error };
      }

      // Show detailed help for this command
      const lines: string[] = [];
      lines.push(`Command: ${commandName}`);
      lines.push('');
      lines.push(`Description:`);
      lines.push(`  ${handler.description()}`);
      lines.push('');

      // Generate function name for display
      const functionName = 'do' + commandName.charAt(0).toUpperCase() + commandName.slice(1);
      lines.push(`Usage in browser console:`);
      lines.push(`  ${functionName}(parameters...)`);

      const output = lines.join('\n');
      logger.debug(output);
      return output;
    }

    // Build formatted output - list all commands
    const lines: string[] = [];
    lines.push('Available Commands:');
    lines.push('');

    for (const [name, handler] of handlers) {
      // Generate function name for display
      const functionName = 'do' + name.charAt(0).toUpperCase() + name.slice(1);
      lines.push(`  ${functionName.padEnd(20)} - ${handler.description()}`);
    }

    lines.push('');
    lines.push('Use "help <commandName>" for detailed information about a specific command.');

    const output = lines.join('\n');

    // Return as both string and log to console
    logger.debug(output);

    return output;
  }
}
