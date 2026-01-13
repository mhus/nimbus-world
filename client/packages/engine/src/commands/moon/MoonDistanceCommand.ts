/**
 * MoonDistanceCommand - Set moon distance from camera
 *
 * Usage: moonDistance [moonIndex] [distance]
 * - moonIndex: 0, 1, or 2
 * - distance: Distance in units (default: 450)
 *   - Must be > sun distance (400) and < skybox (~1000)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('MoonDistanceCommand');

export class MoonDistanceCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'moonDistance';
  }

  description(): string {
    return 'Set moon distance from camera (moonDistance [0-2] [100-1900])';
  }

  async execute(parameters: any[]): Promise<string> {
    const moonService = this.appContext.services.moon;

    if (!moonService) {
      return 'MoonService not available';
    }

    if (parameters.length < 2) {
      return 'Usage: moonDistance [moonIndex] [distance]\nExample: moonDistance 0 500';
    }

    const moonIndex = toNumber(parameters[0]);
    if (isNaN(moonIndex) || moonIndex < 0 || moonIndex > 2) {
      return 'Invalid moonIndex. Must be 0, 1, or 2.';
    }

    const distance = toNumber(parameters[1]);
    if (isNaN(distance) || distance <= 0) {
      return 'Invalid distance. Must be a positive number.';
    }

    moonService.setMoonDistance(moonIndex, distance);

    const position = moonService.getMoonPosition(moonIndex);
    logger.debug(`Moon ${moonIndex} distance set`, position);

    return `Moon ${moonIndex} distance set to ${position?.distance}`;
  }
}
