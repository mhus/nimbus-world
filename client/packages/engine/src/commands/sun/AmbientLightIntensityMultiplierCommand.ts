/**
 * AmbientLightIntensityMultiplierCommand - Set ambient light intensity multiplier
 *
 * Usage: ambientLightIntensityMultiplier [value]
 * - Without parameters: Shows current multiplier
 * - With parameter: Sets ambient light intensity multiplier (default: 0.5)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('AmbientLightIntensityMultiplierCommand');

export class AmbientLightIntensityMultiplierCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'ambientLightIntensityMultiplier';
  }

  description(): string {
    return 'Set ambient light intensity multiplier (e.g., 0.5)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current value if no parameters
    if (parameters.length === 0) {
      const multiplier = sunService.getAmbientLightIntensityMultiplier();
      return `Ambient light intensity multiplier is currently ${multiplier.toFixed(2)}`;
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
    sunService.setAmbientLightIntensityMultiplier(multiplier);

    logger.debug('Ambient light intensity multiplier changed', { multiplier });

    return `Ambient light intensity multiplier set to ${multiplier.toFixed(2)}`;
  }
}
