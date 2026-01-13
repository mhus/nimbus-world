/**
 * CloudEnableCommand - Enable or disable a cloud
 *
 * Usage: cloudEnable [id] [enabled]
 * - id: Cloud identifier
 * - enabled: true or false
 *
 * Without parameters: Shows all clouds and their enabled state
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CloudEnableCommand');

export class CloudEnableCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudEnable';
  }

  description(): string {
    return 'Enable or disable a cloud';
  }

  async execute(parameters: string[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    // Show all clouds if no parameters
    if (parameters.length === 0) {
      const clouds = cloudsService.getAllClouds();
      if (clouds.length === 0) {
        return 'No clouds available';
      }

      const cloudList = clouds.map(cloud => `  - ${cloud.id}: ${cloud.enabled ? 'enabled' : 'disabled'}`).join('\n');
      return `Clouds (${clouds.length}):\n${cloudList}`;
    }

    // Check parameter count
    if (parameters.length < 2) {
      return 'Usage: cloudEnable [id] [enabled]\nExample: cloudEnable "cloud1" true';
    }

    const id = parameters[0];
    const enabledStr = parameters[1].toLowerCase();

    if (enabledStr !== 'true' && enabledStr !== 'false') {
      return 'Invalid enabled value. Must be "true" or "false".';
    }

    const enabled = enabledStr === 'true';

    try {
      cloudsService.setCloudEnabled(id, enabled);
      logger.debug('Cloud enabled state changed', { id, enabled });
      return `Cloud "${id}" ${enabled ? 'enabled' : 'disabled'}`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to change cloud enabled state', { error: errorMessage, id, enabled });
      return `Failed to change cloud enabled state: ${errorMessage}`;
    }
  }
}
