/**
 * MoonPositionCommand - Set moon position on circular orbit
 *
 * Usage: moonPosition [moonIndex] [angleY]
 * - moonIndex: 0, 1, or 2
 * - angleY: Horizontal position in degrees (0-360)
 *   - 0° = North, 90° = East, 180° = South, 270° = West
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('MoonPositionCommand');

export class MoonPositionCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'moonPosition';
  }

  description(): string {
    return 'Set moon position on circular orbit (moonPosition [0-2] [0-360])';
  }

  async execute(parameters: any[]): Promise<string> {
    const moonService = this.appContext.services.moon;

    if (!moonService) {
      return 'MoonService not available';
    }

    if (parameters.length < 2) {
      return 'Usage: moonPosition [moonIndex] [angleY]\nExample: moonPosition 0 180';
    }

    const moonIndex = toNumber(parameters[0]);
    if (isNaN(moonIndex) || moonIndex < 0 || moonIndex > 2) {
      return 'Invalid moonIndex. Must be 0, 1, or 2.';
    }

    const angleY = toNumber(parameters[1]);
    if (isNaN(angleY)) {
      return 'Invalid angleY. Must be a number (0-360).';
    }

    moonService.setMoonPositionOnCircle(moonIndex, angleY);

    const position = moonService.getMoonPosition(moonIndex);
    logger.debug(`Moon ${moonIndex} position set`, position);

    return `Moon ${moonIndex} position set to ${position?.angleY}° (0=North, 90=East, 180=South, 270=West)`;
  }
}
