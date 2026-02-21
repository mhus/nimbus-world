/**
 * UndulationFrequencyCommand - Set global undulation wave speed
 *
 * Usage: undulationFrequency [value]
 * - Without parameters: Shows current undulation frequency
 * - With parameter: Sets undulation frequency (0-5)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('UndulationFrequencyCommand');

export class UndulationFrequencyCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'undulationFrequency';
  }

  description(): string {
    return 'Set undulation wave speed (0-5)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current value if no parameters
    if (parameters.length === 0) {
      const frequency = environmentService.getUndulationFrequency();
      return `Current undulation frequency: ${frequency.toFixed(2)}`;
    }

    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-5).';
    }

    if (value < 0 || value > 5) {
      return 'Value out of bounds. Undulation frequency must be between 0 and 5.';
    }

    environmentService.setUndulationFrequency(value);

    const frequency = environmentService.getUndulationFrequency();
    logger.debug('Undulation frequency set', { frequency });

    return `Undulation frequency set to ${frequency.toFixed(2)}`;
  }
}
