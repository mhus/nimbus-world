/**
 * CloudDirectionCommand - Set cloud movement direction
 *
 * Usage: cloudDirection [id] [direction]
 * - id: Cloud identifier
 * - direction: Direction in degrees (0=North, 90=East, 180=South, 270=West)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CloudDirectionCommand');

export class CloudDirectionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudDirection';
  }

  description(): string {
    return 'Set cloud movement direction (0=North, 90=East, 180=South, 270=West)';
  }

  async execute(parameters: any[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    // Check parameter count
    if (parameters.length < 2) {
      return 'Usage: cloudDirection [id] [direction]\nExample: cloudDirection "cloud1" 90';
    }

    const id = parameters[0];
    const direction = toNumber(parameters[1]);

    if (isNaN(direction)) {
      return 'Invalid direction value. Must be a number (0-360).';
    }

    try {
      cloudsService.setCloudDirection(id, direction);
      logger.debug('Cloud direction changed', { id, direction });
      return `Cloud "${id}" direction set to ${direction}° (0=North, 90=East, 180=South, 270=West)`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change cloud direction', { error: errorMessage, id, direction });
      return `Failed to change cloud direction: ${errorMessage}`;
    }
  }
}
