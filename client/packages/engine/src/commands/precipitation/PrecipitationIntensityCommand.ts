/**
 * PrecipitationIntensityCommand - Set precipitation intensity
 *
 * Usage: precipitationIntensity [0-100]
 * - intensity: Value between 0 and 100
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('PrecipitationIntensityCommand');

export class PrecipitationIntensityCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'precipitationIntensity';
  }

  description(): string {
    return 'Set precipitation intensity (0-100)';
  }

  async execute(parameters: any[]): Promise<string> {
    const precipitationService = this.appContext.services.precipitation;
    if (!precipitationService) {
      return 'PrecipitationService not available';
    }

    // Parse intensity argument
    if (parameters.length < 1) {
      return 'Usage: precipitationIntensity [intensity]\nExample: precipitationIntensity 50';
    }

    const intensity = toNumber(parameters[0]);
    if (isNaN(intensity) || intensity < 0 || intensity > 100) {
      return 'Intensity must be a number between 0 and 100';
    }

    try {
      precipitationService.setIntensity(intensity);
      logger.debug('Precipitation intensity changed', { intensity });
      return `Precipitation intensity set to ${intensity}`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change precipitation intensity', { error: errorMessage, intensity });
      return `Failed to change precipitation intensity: ${errorMessage}`;
    }
  }
}
