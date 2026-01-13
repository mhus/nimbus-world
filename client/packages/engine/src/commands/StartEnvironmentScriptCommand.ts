/**
 * StartEnvironmentScriptCommand - Start an environment script
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('StartEnvironmentScriptCommand');

/**
 * Start environment script command
 *
 * Usage:
 *   startEnvironmentScript <name>
 *
 * Parameters:
 *   name - Script name to start
 *
 * Examples:
 *   startEnvironmentScript("rain_storm")
 *   startEnvironmentScript("day_cycle")
 *
 * Note: If a script is already running in the same group, it will be stopped first.
 */
export class StartEnvironmentScriptCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'startEnvironmentScript';
  }

  description(): string {
    return 'Start an environment script by name';
  }

  async execute(parameters: any[]): Promise<any> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Validate parameters
    if (parameters.length < 1) {
      logger.error('Usage: startEnvironmentScript <name>');
      return {
        error: 'Missing parameters. Usage: startEnvironmentScript <name>',
      };
    }

    const name = parameters[0];

    // Validate name
    if (typeof name !== 'string' || name.trim() === '') {
      logger.error('Script name must be a non-empty string');
      return { error: 'Script name must be a non-empty string' };
    }

    // Start the script
    const executorId = await environmentService.startEnvironmentScript(name);

    if (executorId) {
      const message = `Environment script started: ${name} (executor: ${executorId})`;
      logger.debug(message);
      return {
        name,
        executorId,
        started: true,
        message,
      };
    } else {
      const message = `Failed to start environment script: ${name}`;
      logger.error(message);
      return {
        name,
        started: false,
        message,
      };
    }
  }
}
