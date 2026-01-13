/**
 * DeleteEnvironmentScriptCommand - Delete an environment script
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('DeleteEnvironmentScriptCommand');

/**
 * Delete environment script command
 *
 * Usage:
 *   deleteEnvironmentScript <name>
 *
 * Parameters:
 *   name - Script name to delete
 *
 * Examples:
 *   deleteEnvironmentScript("rain_storm")
 *   deleteEnvironmentScript("day_cycle")
 */
export class DeleteEnvironmentScriptCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'deleteEnvironmentScript';
  }

  description(): string {
    return 'Delete an environment script by name';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Validate parameters
    if (parameters.length < 1) {
      logger.error('Usage: deleteEnvironmentScript <name>');
      return {
        error: 'Missing parameters. Usage: deleteEnvironmentScript <name>',
      };
    }

    const name = parameters[0];

    // Validate name
    if (typeof name !== 'string' || name.trim() === '') {
      logger.error('Script name must be a non-empty string');
      return { error: 'Script name must be a non-empty string' };
    }

    // Delete the script
    const deleted = environmentService.deleteEnvironmentScript(name);

    if (deleted) {
      const message = `Environment script deleted: ${name}`;
      logger.debug(message);
      return {
        name,
        deleted: true,
        message,
      };
    } else {
      const message = `Environment script not found: ${name}`;
      logger.warn(message);
      return {
        name,
        deleted: false,
        message,
      };
    }
  }
}
