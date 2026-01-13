/**
 * HorizonGradientDistanceCommand - Set horizon gradient box distance
 *
 * Usage: horizonGradientDistance <distance>
 * - distance: Distance from camera on XZ plane (positive number)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('HorizonGradientDistanceCommand');

export class HorizonGradientDistanceCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientDistance';
  }

  description(): string {
    return 'Set horizon gradient distance from camera (distance)';
  }

  async execute(parameters: any[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    if (parameters.length === 0) {
      return 'Usage: horizonGradientDistance <distance>\nExample: horizonGradientDistance 400';
    }

    const distance = toNumber(parameters[0]);

    if (isNaN(distance) || distance <= 0) {
      return 'Invalid distance. Must be a positive number.';
    }

    service.setDistance(distance);
    logger.debug('Horizon gradient distance set via command', { distance });
    return `Horizon gradient distance set to ${distance}`;
  }
}
