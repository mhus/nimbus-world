/**
 * WindStrengthCommand - Set global wind strength
 *
 * Usage: windStrength [value]
 * - Without parameters: Shows current wind strength
 * - With parameter: Sets wind strength (0-10)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('WindStrengthCommand');

export class WindStrengthCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'windStrength';
  }

  description(): string {
    return 'Set wind strength (0-10)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current wind strength if no parameters
    if (parameters.length === 0) {
      const strength = environmentService.getWindStrength();
      return `Current wind strength: ${strength.toFixed(2)}`;
    }

    // Parse and validate parameter
    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-10).';
    }

    if (value < 0 || value > 10) {
      return 'Value out of bounds. Wind strength must be between 0 and 10.';
    }

    // Set wind strength
    environmentService.setWindStrength(value);

    const strength = environmentService.getWindStrength();
    logger.debug('Wind strength set', { strength });

    return `Wind strength set to ${strength.toFixed(2)}`;
  }
}
