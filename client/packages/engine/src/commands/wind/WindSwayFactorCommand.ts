/**
 * WindSwayFactorCommand - Set global wind sway factor
 *
 * Usage: windSwayFactor [value]
 * - Without parameters: Shows current wind sway factor
 * - With parameter: Sets wind sway factor (0-5)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('WindSwayFactorCommand');

export class WindSwayFactorCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'windSwayFactor';
  }

  description(): string {
    return 'Set wind sway factor (0-5)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current wind sway factor if no parameters
    if (parameters.length === 0) {
      const factor = environmentService.getWindSwayFactor();
      return `Current wind sway factor: ${factor.toFixed(2)}`;
    }

    // Parse and validate parameter
    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-5).';
    }

    if (value < 0 || value > 5) {
      return 'Value out of bounds. Wind sway factor must be between 0 and 5.';
    }

    // Set wind sway factor
    environmentService.setWindSwayFactor(value);

    const factor = environmentService.getWindSwayFactor();
    logger.debug('Wind sway factor set', { swayFactor: factor });

    return `Wind sway factor set to ${factor.toFixed(2)}`;
  }
}
