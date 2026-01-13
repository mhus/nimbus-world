/**
 * AutomaticSunAdjustmentCommand - Enable or disable automatic sun light adjustment
 *
 * Usage: automaticSunAdjustment [true|false]
 * - Without parameters: Shows current automatic adjustment status
 * - With parameter: Enables or disables automatic sun light adjustment
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toBoolean } from '@nimbus/shared';

const logger = getLogger('AutomaticSunAdjustmentCommand');

export class AutomaticSunAdjustmentCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'automaticSunAdjustment';
  }

  description(): string {
    return 'Enable or disable automatic sun light adjustment (true|false)';
  }

  async execute(parameters: string[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current status if no parameters
    if (parameters.length === 0) {
      const enabled = sunService.getAutomaticSunAdjustment();
      return `Automatic sun adjustment is currently ${enabled ? 'enabled' : 'disabled'}`;
    }

    // Parse parameter using CastUtil
    const enabled = toBoolean(parameters[0]);

    // Set automatic sun adjustment
    sunService.setAutomaticSunAdjustment(enabled);

    logger.debug('Automatic sun adjustment changed', { enabled });

    return `Automatic sun adjustment ${enabled ? 'enabled' : 'disabled'}`;
  }
}
