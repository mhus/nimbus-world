/**
 * ShadowsIntensityCommand - Control shadow darkness
 *
 * Usage: shadowsIntensity [value]
 * - Without parameters: Shows current intensity
 * - With parameter: Sets shadow intensity (0.0-1.0)
 *   0.0 = no shadows (fully lit)
 *   1.0 = very dark shadows
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('ShadowsIntensityCommand');

export class ShadowsIntensityCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsIntensity';
  }

  description(): string {
    return 'Set shadow intensity/darkness (0.0-1.0, where 0=no shadows, 1=dark)';
  }

  async execute(parameters: any[]): Promise<string> {
    const envService = this.appContext.services.environment;

    if (!envService) {
      return 'EnvironmentService not available';
    }

    // Show current intensity if no parameters
    if (parameters.length === 0) {
      const info = envService.getShadowInfo();
      return `Current shadow darkness: ${info.darkness.toFixed(2)} (0=no shadows, 1=dark)`;
    }

    // Parse and validate parameter
    const intensity = toNumber(parameters[0]);

    if (isNaN(intensity)) {
      return 'Invalid parameter. Value must be a number (0.0-1.0).';
    }

    if (intensity < 0 || intensity > 1) {
      return 'Value out of bounds. Shadow intensity must be between 0.0 and 1.0.';
    }

    // Set shadow intensity
    envService.setShadowIntensity(intensity);

    logger.debug('Shadow intensity set', { intensity });

    return `Shadow intensity set to ${intensity.toFixed(2)}`;
  }
}
