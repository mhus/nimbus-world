/**
 * SunSizeCommand - Set sun size/scaling factor
 *
 * Usage: sunSize [value]
 * - Without parameters: Shows current sun size
 * - With parameter: Sets sun billboard scaling factor (0.1-10.0)
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('SunSizeCommand');

export class SunSizeCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunSize';
  }

  description(): string {
    return 'Set sun size scaling factor (0.1-10.0, default: 1.0)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current size if no parameters
    if (parameters.length === 0) {
      return 'Usage: sunSize [value] (0.1-10.0)';
    }

    // Parse and validate parameter
    const size = toNumber(parameters[0]);

    if (isNaN(size)) {
      return 'Invalid parameter. Value must be a number (0.1-10.0).';
    }

    if (size < 0.1 || size > 10.0) {
      return 'Value out of bounds. Sun size must be between 0.1 and 10.0.';
    }

    // Set sun size
    sunService.setSunSize(size);

    logger.debug('Sun size set', { size });

    return `Sun size set to ${size}`;
  }
}
