/**
 * ShadowsEnableCommand - Enable or disable shadows
 *
 * Usage: shadowsEnable [true|false]
 * - Without parameters: Shows current shadow state
 * - With parameter: Enables or disables shadows
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger, toBoolean } from '@nimbus/shared';

const logger = getLogger('ShadowsEnableCommand');

export class ShadowsEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'shadowsEnable';
  }

  description(): string {
    return 'Enable or disable shadows';
  }

  async execute(parameters: any[]): Promise<string> {
    const envService = this.appContext.services.environment;

    if (!envService) {
      return 'EnvironmentService not available';
    }

    // Show current state if no parameters
    if (parameters.length === 0) {
      const info = envService.getShadowInfo();
      return `Shadows are currently ${info.enabled ? 'enabled' : 'disabled'}`;
    }

    // Parse and validate parameter
    const enabled = toBoolean(parameters[0]);

    // Set shadows enabled/disabled
    envService.setShadowsEnabled(enabled);

    logger.debug('Shadows toggled', { enabled });

    return `Shadows ${enabled ? 'enabled' : 'disabled'}`;
  }
}
