/**
 * UndulationStrengthCommand - Set global undulation wave amplitude
 *
 * Usage: undulationStrength [value]
 * - Without parameters: Shows current undulation strength
 * - With parameter: Sets undulation strength (0-1)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('UndulationStrengthCommand');

export class UndulationStrengthCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'undulationStrength';
  }

  description(): string {
    return 'Set undulation wave amplitude (0-1)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current value if no parameters
    if (parameters.length === 0) {
      const strength = environmentService.getUndulationStrength();
      return `Current undulation strength: ${strength.toFixed(2)}`;
    }

    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-1).';
    }

    if (value < 0 || value > 1) {
      return 'Value out of bounds. Undulation strength must be between 0 and 1.';
    }

    environmentService.setUndulationStrength(value);

    const strength = environmentService.getUndulationStrength();
    logger.debug('Undulation strength set', { strength });

    return `Undulation strength set to ${strength.toFixed(2)}`;
  }
}
