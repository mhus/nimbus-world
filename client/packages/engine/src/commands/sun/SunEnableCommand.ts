/**
 * SunEnableCommand - Enable or disable sun visibility
 *
 * Usage: sunEnable [true|false]
 * - Without parameters: Shows current sun enabled status
 * - With parameter: Enables or disables sun visibility
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toBoolean } from '@nimbus/shared';

const logger = getLogger('SunEnableCommand');

export class SunEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'sunEnable';
  }

  description(): string {
    return 'Enable or disable sun visibility (true|false)';
  }

  async execute(parameters: any[]): Promise<string> {
    const sunService = this.appContext.services.sun;

    if (!sunService) {
      return 'SunService not available';
    }

    // Show current status if no parameters
    if (parameters.length === 0) {
      return 'Usage: sunEnable [true|false]';
    }

    // Parse parameter using CastUtil
    const enabled = toBoolean(parameters[0]);

    // Set sun visibility
    sunService.setEnabled(enabled);

    logger.debug('Sun visibility changed', { enabled });

    return `Sun ${enabled ? 'enabled' : 'disabled'}`;
  }
}
