/**
 * MoonEnableCommand - Enable or disable a moon
 *
 * Usage: moonEnable [moonIndex] [enabled]
 * - moonIndex: 0, 1, or 2
 * - enabled: true or false
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('MoonEnableCommand');

export class MoonEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'moonEnable';
  }

  description(): string {
    return 'Enable/disable moon visibility (moonEnable [0-2] [true|false])';
  }

  async execute(parameters: any[]): Promise<string> {
    const moonService = this.appContext.services.moon;

    if (!moonService) {
      return 'MoonService not available';
    }

    if (parameters.length < 2) {
      return 'Usage: moonEnable [moonIndex] [enabled]\nExample: moonEnable 0 true';
    }

    const moonIndex = toNumber(parameters[0]);
    if (isNaN(moonIndex) || moonIndex < 0 || moonIndex > 2) {
      return 'Invalid moonIndex. Must be 0, 1, or 2.';
    }

    const enabled = parameters[1] === 'true';

    moonService.setMoonEnabled(moonIndex, enabled);

    logger.debug(`Moon ${moonIndex} ${enabled ? 'enabled' : 'disabled'}`);

    return `Moon ${moonIndex} ${enabled ? 'enabled' : 'disabled'}`;
  }
}
