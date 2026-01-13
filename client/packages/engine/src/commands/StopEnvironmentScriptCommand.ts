/**
 * StopEnvironmentScriptCommand - Stop an environment script by name
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('StopEnvironmentScriptCommand');

/**
 * Stop environment script command
 *
 * Usage:
 *   stopEnvironmentScript <name>
 *
 * Parameters:
 *   name - Action name to stop (e.g., 'rain_storm', 'day_cycle')
 *
 * Examples:
 *   stopEnvironmentScript("rain_storm")
 *   stopEnvironmentScript("day_cycle")
 */
export class StopEnvironmentScriptCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'stopEnvironmentScript';
  }

  description(): string {
    return 'Stop an environment script by name';
  }

  async execute(parameters: any[]): Promise<any> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Validate parameters
    if (parameters.length < 1) {
      logger.error('Usage: stopEnvironmentScript <name>');
      return {
        error: 'Missing parameters. Usage: stopEnvironmentScript <name>',
      };
    }

    const name = parameters[0];

    // Validate name
    if (typeof name !== 'string' || name.trim() === '') {
      logger.error('Action name must be a non-empty string');
      return { error: 'Action name must be a non-empty string' };
    }

    // Stop the script
    const stopped = await environmentService.stopEnvironmentScript(name);

    if (stopped) {
      const message = `Environment script stopped: ${name}`;
      logger.debug(message);
      return {
        name,
        stopped: true,
        message,
      };
    } else {
      const message = `No running script found: ${name}`;
      logger.debug(message);
      return {
        name,
        stopped: false,
        message,
      };
    }
  }
}
