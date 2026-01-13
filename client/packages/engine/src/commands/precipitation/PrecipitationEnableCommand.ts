/**
 * PrecipitationEnableCommand - Enable or disable precipitation
 *
 * Usage: precipitationEnable [enabled]
 * - enabled: true or false
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('PrecipitationEnableCommand');

export class PrecipitationEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'precipitationEnable';
  }

  description(): string {
    return 'Enable or disable precipitation';
  }

  async execute(parameters: string[]): Promise<string> {
    const precipitationService = this.appContext.services.precipitation;
    if (!precipitationService) {
      return 'PrecipitationService not available';
    }

    // Parse enabled argument
    if (parameters.length < 1) {
      return 'Usage: precipitationEnable [enabled]\nExample: precipitationEnable true';
    }

    const enabledStr = parameters[0].toLowerCase();

    if (enabledStr !== 'true' && enabledStr !== 'false') {
      return 'Invalid enabled value. Must be "true" or "false".';
    }

    const enabled = enabledStr === 'true';

    try {
      precipitationService.setEnabled(enabled);
      logger.debug('Precipitation enabled state changed', { enabled });
      return `Precipitation ${enabled ? 'enabled' : 'disabled'}`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change precipitation enabled state', { error: errorMessage, enabled });
      return `Failed to change precipitation enabled state: ${errorMessage}`;
    }
  }
}
