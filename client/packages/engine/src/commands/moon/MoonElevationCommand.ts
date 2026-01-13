/**
 * MoonElevationCommand - Set moon elevation (height over camera)
 *
 * Usage: moonElevation [moonIndex] [elevation]
 * - moonIndex: 0, 1, or 2
 * - elevation: Vertical angle in degrees (-90 to 90)
 *   - -90° = below horizon, 0° = horizon, 90° = directly above
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('MoonElevationCommand');

export class MoonElevationCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'moonElevation';
  }

  description(): string {
    return 'Set moon elevation angle (moonElevation [0-2] [-90 to 90])';
  }

  async execute(parameters: any[]): Promise<string> {
    const moonService = this.appContext.services.moon;

    if (!moonService) {
      return 'MoonService not available';
    }

    if (parameters.length < 2) {
      return 'Usage: moonElevation [moonIndex] [elevation]\nExample: moonElevation 0 60';
    }

    const moonIndex = toNumber(parameters[0]);
    if (isNaN(moonIndex) || moonIndex < 0 || moonIndex > 2) {
      return 'Invalid moonIndex. Must be 0, 1, or 2.';
    }

    const elevation = toNumber(parameters[1]);
    if (isNaN(elevation)) {
      return 'Invalid elevation. Must be a number (-90 to 90).';
    }

    moonService.setMoonHeightOverCamera(moonIndex, elevation);

    const position = moonService.getMoonPosition(moonIndex);
    logger.debug(`Moon ${moonIndex} elevation set`, position);

    return `Moon ${moonIndex} elevation set to ${position?.elevation}°`;
  }
}
