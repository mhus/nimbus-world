/**
 * CloudClearCommand - Remove all clouds from the scene
 *
 * Usage: cloudClear
 */

import { CommandHandler } from '../CommandHandler';
import type { AppContext } from '../../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CloudClearCommand');

export class CloudClearCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'cloudClear';
  }

  description(): string {
    return 'Remove all clouds from the scene';
  }

  async execute(parameters: string[]): Promise<string> {
    const cloudsService = this.appContext.services.clouds;

    if (!cloudsService) {
      return 'CloudsService not available';
    }

    const count = cloudsService.getCloudCount();

    if (count === 0) {
      return 'No clouds to clear';
    }

    try {
      cloudsService.clearAllClouds();
      logger.debug('All clouds cleared', { count });
      return `All clouds cleared (${count} clouds removed)`;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Failed to clear clouds', { error: errorMessage });
      return `Failed to clear clouds: ${errorMessage}`;
    }
  }
}
