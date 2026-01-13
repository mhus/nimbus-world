/**
 * WindGustStrengthCommand - Set global wind gust strength
 *
 * Usage: windGustStrength [value]
 * - Without parameters: Shows current wind gust strength
 * - With parameter: Sets wind gust strength (0-10)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('WindGustStrengthCommand');

export class WindGustStrengthCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'windGustStrength';
  }

  description(): string {
    return 'Set wind gust strength (0-10)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current wind gust strength if no parameters
    if (parameters.length === 0) {
      const strength = environmentService.getWindGustStrength();
      return `Current wind gust strength: ${strength.toFixed(2)}`;
    }

    // Parse and validate parameter
    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-10).';
    }

    if (value < 0 || value > 10) {
      return 'Value out of bounds. Wind gust strength must be between 0 and 10.';
    }

    // Set wind gust strength
    environmentService.setWindGustStrength(value);

    const strength = environmentService.getWindGustStrength();
    logger.debug('Wind gust strength set', { gustStrength: strength });

    return `Wind gust strength set to ${strength.toFixed(2)}`;
  }
}
