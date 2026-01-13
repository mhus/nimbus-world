/**
 * AmbientLightIntensityCommand - Set ambient light intensity
 *
 * Usage: ambientLightIntensity [value]
 * - Without parameters: Shows current ambient light intensity
 * - With parameter: Sets ambient light intensity (0-10)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('AmbientLightIntensityCommand');

export class AmbientLightIntensityCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'ambientLightIntensity';
  }

  description(): string {
    return 'Set ambient light intensity (0-10)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current intensity if no parameters
    if (parameters.length === 0) {
      const intensity = environmentService.getAmbientLightIntensity();
      return `Current ambient light intensity: ${intensity.toFixed(2)}`;
    }

    // Parse and validate parameter
    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-10).';
    }

    if (value < 0 || value > 10) {
      return 'Value out of bounds. Ambient light intensity must be between 0 and 10.';
    }

    // Set ambient light intensity
    environmentService.setAmbientLightIntensity(value);

    const intensity = environmentService.getAmbientLightIntensity();
    logger.debug('Ambient light intensity set', { intensity });

    return `Ambient light intensity set to ${intensity.toFixed(2)}`;
  }
}
