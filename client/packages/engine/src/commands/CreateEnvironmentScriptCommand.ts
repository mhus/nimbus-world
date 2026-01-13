/**
 * CreateEnvironmentScriptCommand - Create/register an environment script
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('CreateEnvironmentScriptCommand');

/**
 * Create environment script command
 *
 * Usage:
 *   createEnvironmentScript <name> <scriptName>
 *
 * Parameters:
 *   name       - Action name (unique identifier, e.g., 'custom_weather')
 *   scriptName - Script name to execute (reference to script in script registry)
 *
 * Examples:
 *   createEnvironmentScript("rain_storm", "weather_rain")
 *   createEnvironmentScript("day_cycle", "daytime_day")
 */
export class CreateEnvironmentScriptCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'createEnvironmentScript';
  }

  description(): string {
    return 'Create/register an environment script (name scriptName)';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Validate parameters
    if (parameters.length < 2) {
      logger.error('Usage: createEnvironmentScript <name> <scriptName>');
      return {
        error: 'Missing parameters. Usage: createEnvironmentScript <name> <scriptName>',
      };
    }

    const name = parameters[0];
    const scriptName = parameters[1];

    // Validate name
    if (typeof name !== 'string' || name.trim() === '') {
      logger.error('Action name must be a non-empty string');
      return { error: 'Action name must be a non-empty string' };
    }

    // Validate scriptName
    if (typeof scriptName !== 'string' || scriptName.trim() === '') {
      logger.error('Script name must be a non-empty string');
      return { error: 'Script name must be a non-empty string' };
    }

    // Create the script
    environmentService.createEnvironmentScript(name, scriptName);

    const message = `Environment script created: ${name} -> ${scriptName}`;
    logger.debug(message);

    return {
      name,
      scriptName,
      message,
    };
  }
}
