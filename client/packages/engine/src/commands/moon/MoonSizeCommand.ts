/**
 * MoonSizeCommand - Set moon size
 *
 * Usage: moonSize [moonIndex] [size]
 * - moonIndex: 0, 1, or 2
 * - size: Moon size in units (default: 60)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('MoonSizeCommand');

export class MoonSizeCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'moonSize';
  }

  description(): string {
    return 'Set moon size (moonSize [0-2] [size])';
  }

  async execute(parameters: any[]): Promise<string> {
    const moonService = this.appContext.services.moon;

    if (!moonService) {
      return 'MoonService not available';
    }

    if (parameters.length < 2) {
      return 'Usage: moonSize [moonIndex] [size]\nExample: moonSize 0 70';
    }

    const moonIndex = toNumber(parameters[0]);
    if (isNaN(moonIndex) || moonIndex < 0 || moonIndex > 2) {
      return 'Invalid moonIndex. Must be 0, 1, or 2.';
    }

    const size = toNumber(parameters[1]);
    if (isNaN(size) || size <= 0) {
      return 'Invalid size. Must be a positive number.';
    }

    moonService.setMoonSize(moonIndex, size);

    logger.debug(`Moon ${moonIndex} size set to ${size}`);

    return `Moon ${moonIndex} size set to ${size}`;
  }
}
