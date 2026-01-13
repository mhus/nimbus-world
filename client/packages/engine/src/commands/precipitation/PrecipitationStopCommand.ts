/**
 * PrecipitationStopCommand - Stop precipitation
 *
 * Usage: precipitationStop
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('PrecipitationStopCommand');

export class PrecipitationStopCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'precipitationStop';
  }

  description(): string {
    return 'Stop precipitation';
  }

  async execute(parameters: string[]): Promise<string> {
    const precipitationService = this.appContext.services.precipitation;
    if (!precipitationService) {
      return 'PrecipitationService not available';
    }

    try {
      precipitationService.setEnabled(false);
      logger.debug('Precipitation stopped');
      return 'Precipitation stopped';
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to stop precipitation', { error: errorMessage });
      return `Failed to stop precipitation: ${errorMessage}`;
    }
  }
}
