/**
 * SunLensFlareIntensityCommand - Set sun lens flare intensity
 *
 * Usage: sunLensFlareIntensity [value]
 * - Without parameters: Shows usage information
 * - With parameter: Sets lens flare intensity (0-2)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunLensFlareIntensityCommand');

export class SunLensFlareIntensityCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunLensFlareIntensity';
  }

  description(): string {
    return 'Set sun lens flare intensity (0-2)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show usage if no parameters
    if (parameters.length === 0) {
      return 'Usage: sunLensFlareIntensity [value] (0-2)';
    }

    // Parse and validate parameter
    const intensity = toNumber(parameters[0]);

    if (isNaN(intensity)) {
      return 'Invalid parameter. Value must be a number (0-2).';
    }

    if (intensity < 0 || intensity > 2) {
      return 'Value out of bounds. Intensity must be between 0 and 2.';
    }

    // Set lens flare intensity
    sunService.setSunLensFlareIntensity(intensity);

    logger.debug('Lens flare intensity set', { intensity });

    return `Lens flare intensity set to ${intensity.toFixed(2)}`;
  }
}
