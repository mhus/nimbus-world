/**
 * GetCurrentEnvironmentScriptCommand - Check if an environment script is running
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('GetCurrentEnvironmentScriptCommand');

/**
 * Get current environment script command
 *
 * Usage:
 *   getCurrentEnvironmentScript <name>
 *
 * Parameters:
 *   name - Action name to check
 *
 * Examples:
 *   getCurrentEnvironmentScript("rain_storm")
 *   getCurrentEnvironmentScript("day_cycle")
 *
 * Returns: Whether the script is running
 */
export class GetCurrentEnvironmentScriptCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'getCurrentEnvironmentScript';
  }

  description(): string {
    return 'Check if an environment script is running by name';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Validate parameters
    if (parameters.length < 1) {
      logger.error('Usage: getCurrentEnvironmentScript <name>');
      return {
        error: 'Missing parameters. Usage: getCurrentEnvironmentScript <name>',
      };
    }

    const name = parameters[0];

    // Validate name
    if (typeof name !== 'string' || name.trim() === '') {
      logger.error('Action name must be a non-empty string');
      return { error: 'Action name must be a non-empty string' };
    }

    // Check if script is running
    const isRunning = environmentService.isEnvironmentScriptRunning(name);

    const message = `Script ${name} is ${isRunning ? 'running' : 'not running'}`;
    logger.debug(message);

    return {
      name,
      isRunning,
      message,
    };
  }
}
