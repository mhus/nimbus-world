/**
 * PrecipitationTypeCommand - Set precipitation type
 *
 * Usage: precipitationType [type]
 * - type: rain or snow
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { PrecipitationType } from '../../services/PrecipitationService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('PrecipitationTypeCommand');

export class PrecipitationTypeCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'precipitationType';
  }

  description(): string {
    return 'Set precipitation type (rain|snow)';
  }

  async execute(parameters: string[]): Promise<string> {
    const precipitationService = this.appContext.services.precipitation;
    if (!precipitationService) {
      return 'PrecipitationService not available';
    }

    // Parse type argument
    if (parameters.length < 1) {
      return 'Usage: precipitationType [type]\nExample: precipitationType rain';
    }

    const typeArg = parameters[0].toLowerCase();
    let type: PrecipitationType;

    if (typeArg === 'rain') {
      type = PrecipitationType.RAIN;
    } else if (typeArg === 'snow') {
      type = PrecipitationType.SNOW;
    } else {
      return 'Invalid type. Use "rain" or "snow"';
    }

    try {
      precipitationService.setPrecipitationType(type);
      logger.debug('Precipitation type changed', { type });
      return `Precipitation type set to ${type}`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change precipitation type', { error: errorMessage, type });
      return `Failed to change precipitation type: ${errorMessage}`;
    }
  }
}
