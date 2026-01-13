/**
 * SunLightIntensityCommand - Set sun light intensity
 *
 * Usage: sunLightIntensity [value]
 * - Without parameters: Shows current sun light intensity
 * - With parameter: Sets sun light intensity (0-10)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunLightIntensityCommand');

export class SunLightIntensityCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunLightIntensity';
  }

  description(): string {
    return 'Set sun light intensity (0-10)';
  }

  async execute(parameters: any[]): Promise<string> {
    const environmentService = this.appContext.services.environment;

    if (!environmentService) {
      return 'EnvironmentService not available';
    }

    // Show current intensity if no parameters
    if (parameters.length === 0) {
      const intensity = environmentService.getSunLightIntensity();
      return `Current sun light intensity: ${intensity.toFixed(2)}`;
    }

    // Parse and validate parameter
    const value = toNumber(parameters[0]);

    if (isNaN(value)) {
      return 'Invalid parameter. Value must be a number (0-10).';
    }

    if (value < 0 || value > 10) {
      return 'Value out of bounds. Sun light intensity must be between 0 and 10.';
    }

    // Set sun light intensity
    environmentService.setSunLightIntensity(value);

    const intensity = environmentService.getSunLightIntensity();
    logger.debug('Sun light intensity set', { intensity });

    return `Sun light intensity set to ${intensity.toFixed(2)}`;
  }
}
