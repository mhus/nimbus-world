/**
 * HorizonGradientIlluminationStrengthCommand - Set horizon gradient illumination strength
 *
 * Usage: horizonGradientIlluminationStrength [strength]
 * - Without parameters: Shows current strength
 * - With parameter: Sets strength (0-10, typical 0.5-2.0)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('HorizonGradientIlluminationStrengthCommand');

export class HorizonGradientIlluminationStrengthCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientIlluminationStrength';
  }

  description(): string {
    return 'Set horizon gradient illumination strength (0-10, typical 0.5-2.0)';
  }

  async execute(parameters: any[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    // Show current strength if no parameters
    if (parameters.length === 0) {
      const strength = service.getIlluminationStrength();
      return `Horizon gradient illumination strength: ${strength.toFixed(2)}`;
    }

    const strength = toNumber(parameters[0]);

    if (isNaN(strength)) {
      return 'Invalid strength value. Must be a number (typical 0.5-2.0).';
    }

    if (strength < 0) {
      return 'Strength must be >= 0';
    }

    service.setIlluminationStrength(strength);
    logger.info('Horizon gradient illumination strength set via command', { strength });
    return `Horizon gradient illumination strength set to ${strength.toFixed(2)}`;
  }
}
