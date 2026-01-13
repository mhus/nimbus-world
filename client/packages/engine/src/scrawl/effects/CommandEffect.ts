/**
 * CommandEffect - Execute a command via CommandService
 *
 * Allows scrawl scripts to trigger any command in the CommandService.
 * Useful for integrating scrawl effects with game mechanics.
 */

import { getLogger, ExceptionHandler } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';

const logger = getLogger('CommandEffect');

/**
 * Options for CommandEffect
 */
export interface CommandEffectOptions {
  /** Command name to execute */
  cmd: string;

  /** Optional parameters (parameter0 through parameter10) */
  parameter0?: any;
  parameter1?: any;
  parameter2?: any;
  parameter3?: any;
  parameter4?: any;
  parameter5?: any;
  parameter6?: any;
  parameter7?: any;
  parameter8?: any;
  parameter9?: any;
  parameter10?: any;

  /** Alternative: array of parameters */
  parameters?: any[];
}

/**
 * CommandEffect - Executes a command via CommandService
 *
 * Usage in scrawl script:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "command",
 *   "ctx": {
 *     "cmd": "notification",
 *     "parameter0": "Explosion!",
 *     "parameter1": "warning"
 *   }
 * }
 * ```
 *
 * Or with parameters array:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "command",
 *   "ctx": {
 *     "cmd": "teleport",
 *     "parameters": [100, 64, 200]
 *   }
 * }
 * ```
 */
export class CommandEffect extends ScrawlEffectHandler<CommandEffectOptions> {
  async execute(ctx: ScrawlExecContext): Promise<void> {
    const { cmd } = this.options;

    if (!cmd) {
      logger.error('CommandEffect: cmd is required');
      return;
    }

    const commandService = ctx.appContext.services.command;
    if (!commandService) {
      logger.error('CommandService not available');
      return;
    }

    try {
      // Build parameters array
      let parameters: any[];

      if (this.options.parameters) {
        // Use provided parameters array
        parameters = this.options.parameters;
      } else {
        // Collect parameter0 through parameter10
        parameters = [];
        for (let i = 0; i <= 10; i++) {
          const key = `parameter${i}` as keyof CommandEffectOptions;
          const value = this.options[key];
          if (value !== undefined) {
            parameters.push(value);
          }
        }
      }

      logger.debug('Executing command via CommandEffect', {
        cmd,
        parameters,
        scriptId: ctx.scriptId,
      });

      // Execute command
      const result = await commandService.executeCommand(cmd, parameters);

      logger.debug('Command executed successfully', {
        cmd,
        result,
        scriptId: ctx.scriptId,
      });
    } catch (error) {
      // Don't throw - log error and continue script execution
      ExceptionHandler.handle(error, 'CommandEffect.execute', {
        cmd,
        scriptId: ctx.scriptId,
      });
      logger.error('Failed to execute command via CommandEffect', {
        cmd,
        error: (error as Error).message,
      });
    }
  }
}
