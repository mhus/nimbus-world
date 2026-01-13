/**
 * CloudSpeedCommand - Set cloud movement speed
 *
 * Usage: cloudSpeed [id] [speed]
 * - id: Cloud identifier
 * - speed: Movement speed in blocks per second (0 = static)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('CloudSpeedCommand');

export class CloudSpeedCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudSpeed';
  }

  description(): string {
    return 'Set cloud movement speed (0 = static)';
  }

  async execute(parameters: any[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    // Check parameter count
    if (parameters.length < 2) {
      return 'Usage: cloudSpeed [id] [speed]\nExample: cloudSpeed "cloud1" 5';
    }

    const id = parameters[0];
    const speed = toNumber(parameters[1]);

    if (isNaN(speed)) {
      return 'Invalid speed value. Must be a number.';
    }

    if (speed < 0) {
      return 'Speed cannot be negative.';
    }

    try {
      cloudsService.setCloudSpeed(id, speed);
      logger.debug('Cloud speed changed', { id, speed });
      return `Cloud "${id}" speed set to ${speed} blocks/second`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change cloud speed', { error: errorMessage, id, speed });
      return `Failed to change cloud speed: ${errorMessage}`;
    }
  }
}
