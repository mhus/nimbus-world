/**
 * SunElevationCommand - Set sun elevation (height) over camera
 *
 * Usage: sunElevation [degrees]
 * - Without parameters: Shows current sun elevation
 * - With parameter: Sets sun elevation (-90 to 90 degrees)
 *   - -90° = directly below, 0° = horizon, 90° = directly above (zenith)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunElevationCommand');

export class SunElevationCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunElevation';
  }

  description(): string {
    return 'Set sun elevation over camera (-90 to 90 degrees, 0=horizon, 90=zenith)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current elevation if no parameters
    if (parameters.length === 0) {
      const position = sunService.getSunPosition();
      return `Current sun elevation: ${position.elevation}° (-90=below, 0=horizon, 90=zenith)`;
    }

    // Parse and validate parameter
    const elevation = toNumber(parameters[0]);

    if (isNaN(elevation)) {
      return 'Invalid parameter. Value must be a number (-90 to 90).';
    }

    if (elevation < -90 || elevation > 90) {
      return 'Value out of bounds. Elevation must be between -90 and 90 degrees.';
    }

    // Set sun elevation
    sunService.setSunHeightOverCamera(elevation);

    const position = sunService.getSunPosition();
    logger.debug('Sun elevation set', position);

    return `Sun elevation set to ${position.elevation}°`;
  }
}
