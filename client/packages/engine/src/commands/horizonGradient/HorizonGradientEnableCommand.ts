/**
 * HorizonGradientEnableCommand - Enable/disable horizon gradient box
 *
 * Usage: horizonGradientEnable [true|false]
 * - Without parameters: Shows current state
 * - With "true": Enables horizon gradient visibility
 * - With "false": Disables horizon gradient visibility
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('HorizonGradientEnableCommand');

export class HorizonGradientEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'horizonGradientEnable';
  }

  description(): string {
    return 'Enable/disable horizon gradient box (true|false)';
  }

  async execute(parameters: string[]): Promise<string> {
    const service = this.appContext.services.horizonGradient;

    if (!service) {
      return 'HorizonGradientService not available';
    }

    // Show current state if no parameters
    if (parameters.length === 0) {
      return `Horizon gradient is ${service.isEnabled() ? 'enabled' : 'disabled'}`;
    }

    const param = parameters[0].toLowerCase();

    if (param === 'true') {
      service.setEnabled(true);
      logger.debug('Horizon gradient enabled via command');
      return 'Horizon gradient enabled';
    } else if (param === 'false') {
      service.setEnabled(false);
      logger.debug('Horizon gradient disabled via command');
      return 'Horizon gradient disabled';
    } else {
      return 'Invalid parameter. Use "true" or "false".';
    }
  }
}
