/**
 * SunLightIntensityMultiplierCommand - Set sun light intensity multiplier
 *
 * Usage: sunLightIntensityMultiplier [value]
 * - Without parameters: Shows current multiplier
 * - With parameter: Sets sun light intensity multiplier (default: 1.0)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunLightIntensityMultiplierCommand');

export class SunLightIntensityMultiplierCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunLightIntensityMultiplier';
  }

  description(): string {
    return 'Set sun light intensity multiplier (e.g., 1.0)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current value if no parameters
    if (parameters.length === 0) {
      const multiplier = sunService.getSunLightIntensityMultiplier();
      return `Sun light intensity multiplier is currently ${multiplier.toFixed(2)}`;
    }

    // Parse parameter
    const multiplier = toNumber(parameters[0]);

    if (isNaN(multiplier)) {
      return 'Invalid parameter. Value must be a number.';
    }

    if (multiplier < 0) {
      return 'Invalid parameter. Value must be non-negative.';
    }

    // Set multiplier
    sunService.setSunLightIntensityMultiplier(multiplier);

    logger.debug('Sun light intensity multiplier changed', { multiplier });

    return `Sun light intensity multiplier set to ${multiplier.toFixed(2)}`;
  }
}
