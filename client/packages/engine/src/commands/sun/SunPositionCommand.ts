/**
 * SunPositionCommand - Set sun position on circular orbit around camera
 *
 * Usage: sunPosition [angleY]
 * - Without parameters: Shows current sun position
 * - With parameter: Sets sun horizontal position (0-360 degrees)
 *   - 0° = North, 90° = East, 180° = South, 270° = West
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunPositionCommand');

export class SunPositionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunPosition';
  }

  description(): string {
    return 'Set sun position on circular orbit (0-360 degrees, 0=North, 90=East)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current position if no parameters
    if (parameters.length === 0) {
      const position = sunService.getSunPosition();
      return `Current sun position: angleY=${position.angleY}°, elevation=${position.elevation}°`;
    }

    // Parse and validate parameter
    const angleY = toNumber(parameters[0]);

    if (isNaN(angleY)) {
      return 'Invalid parameter. Value must be a number (0-360).';
    }

    // Set sun position (allow values outside 0-360, will wrap automatically)
    sunService.setSunPositionOnCircle(angleY);

    const position = sunService.getSunPosition();
    logger.debug('Sun position set', position);

    return `Sun position set to ${position.angleY}° (0=North, 90=East, 180=South, 270=West)`;
  }
}
