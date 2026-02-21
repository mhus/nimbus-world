/**
 * UndulationWavelengthCommand - Set global undulation spatial phase spread
 *
 * Usage: undulationWavelength [value]
 * - Without parameters: Shows current undulation wavelength
 * - With parameter: Sets undulation wavelength (0-5)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('UndulationWavelengthCommand');

export class UndulationWavelengthCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'undulationWavelength';
  }

  description(): string {
    return 'Set undulation spatial phase spread (0-5)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current value if no parameters
    if (parameters.length === 0) {
      const wavelength = environmentService.getUndulationWavelength();
      return `Current undulation wavelength: ${wavelength.toFixed(2)}`;
    }

    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-5).';
    }

    if (value < 0 || value > 5) {
      return 'Value out of bounds. Undulation wavelength must be between 0 and 5.';
    }

    environmentService.setUndulationWavelength(value);

    const wavelength = environmentService.getUndulationWavelength();
    logger.debug('Undulation wavelength set', { wavelength });

    return `Undulation wavelength set to ${wavelength.toFixed(2)}`;
  }
}
